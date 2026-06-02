package iuh.fit.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.aiservice.config.AiChatRetentionProperties;
import iuh.fit.aiservice.config.AiRagProperties;
import iuh.fit.aiservice.dto.cache.ChatContextCacheEntry;
import iuh.fit.aiservice.dto.cache.ContextMessage;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import iuh.fit.aiservice.dto.request.AiChatRequest;
import iuh.fit.aiservice.dto.response.AiChatResponse;
import iuh.fit.aiservice.dto.response.SuggestedProduct;
import iuh.fit.aiservice.model.AiChatMessage;
import iuh.fit.aiservice.model.AiChatSession;
import iuh.fit.aiservice.model.AiRecommendation;
import iuh.fit.aiservice.model.enums.MessageRole;
import iuh.fit.aiservice.model.enums.RecommendationType;
import iuh.fit.aiservice.model.enums.SessionStatus;
import iuh.fit.aiservice.repo.AiChatMessageRepository;
import iuh.fit.aiservice.repo.AiChatSessionRepository;
import iuh.fit.aiservice.repo.AiRecommendationRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Luồng xử lý chat AI (Tối ưu hóa: Gọi LLM 1 lần duy nhất):
 * 1. Truy vấn Vector DB trực tiếp bằng câu hỏi thô + profile của khách hàng.
 * 2. Gửi câu hỏi, lịch sử và các ứng viên sản phẩm vào duy nhất một cuộc gọi LLM (Unified Prompt).
 * 3. LLM tự động phân loại (inScope, safety, recommendationIntent) và sinh câu trả lời + chọn sản phẩm phù hợp.
 * 4. Parse JSON phản hồi từ LLM và trả về cho Client.
 */
@Service
public class AiChatService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AiChatService.class);

    // Pattern để extract UUID từ reply text dạng [**uuid**] hoặc **uuid** hoặc uuid
    private static final Pattern TAGGED_PRODUCTS_BLOCK = Pattern.compile(
            "\\[?\\*{0,2}[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\*{0,2}\\]?"
    );
    private static final Pattern UUID_IN_TEXT = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

    private static final String OUT_OF_SCOPE_REPLY =
            "Mình chỉ hỗ trợ các câu hỏi về mỹ phẩm, chăm sóc da và thành phần liên quan. "
                    + "Bạn hãy hỏi đúng chủ đề để mình tư vấn tốt hơn nhé.";

    private static final String SAFETY_REPLY =
            "Mình không thể khẳng định độ an toàn của sản phẩm nếu thiếu nguồn xác minh rõ ràng. "
                    + "Bạn nên kiểm tra bảng thành phần, thử trước trên vùng da nhỏ và tham khảo bác sĩ da liễu nếu da đang nhạy cảm hoặc đang điều trị.";

    private static final String ADVICE_DISCLAIMER =
            "\n\nBạn có thể xem thêm bảng thành phần, cách sử dụng và thử trước trên một vùng da nhỏ để yên tâm hơn.";

    /** Số ứng viên tối đa gửi cho LLM chọn lọc. */
    private static final int CANDIDATE_LIMIT_FOR_LLM = 5;

    /** Top-K vector search khi người dùng có intent gợi ý sản phẩm. */
    private static final int RECOMMENDATION_RETRIEVAL_TOP_K = 20;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiRecommendationRepository recommendationRepository;
    private final ContextRetrievalService contextRetrievalService;
    private final PromptBuilder promptBuilder;
    private final OllamaChatService ollamaChatService;
    private final AiContextCacheService cacheService;
    private final AiRagProperties ragProperties;
    private final AiChatRetentionProperties retentionProperties;
    private final ObjectMapper objectMapper;

    public AiChatService(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            AiRecommendationRepository recommendationRepository,
            ContextRetrievalService contextRetrievalService,
            PromptBuilder promptBuilder,
            OllamaChatService ollamaChatService,
            AiContextCacheService cacheService,
            AiRagProperties ragProperties,
            AiChatRetentionProperties retentionProperties,
            ObjectMapper objectMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.recommendationRepository = recommendationRepository;
        this.contextRetrievalService = contextRetrievalService;
        this.promptBuilder = promptBuilder;
        this.ollamaChatService = ollamaChatService;
        this.cacheService = cacheService;
        this.ragProperties = ragProperties;
        this.retentionProperties = retentionProperties;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public AiChatResponse chat(AiChatRequest request) {
        AiChatSession session = resolveSession(request.getSessionId(), request.getCustomerId());
        Instant now = Instant.now();

        AiChatMessage userMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.USER)
                .content(request.getMessage())
                .createdAt(now)
                .expireAt(resolveExpireAt(now))
                .build();
        messageRepository.save(userMessage);

        // Bước 1: Song song tải lịch sử + vector search trực tiếp bằng câu hỏi thô
        CompletableFuture<List<ContextMessage>> historyFuture = CompletableFuture.supplyAsync(
                () -> loadHistory(session.getId(), request.getCustomerId())
        );
        CompletableFuture<ContextRetrievalService.ContextSnapshot> snapshotFuture = CompletableFuture.supplyAsync(
                () -> contextRetrievalService.retrieveContext(
                        request.getCustomerId(),
                        request.getMessage(),
                        RECOMMENDATION_RETRIEVAL_TOP_K
                )
        );

        List<ContextMessage> history = historyFuture.join();
        ContextRetrievalService.ContextSnapshot snapshot = snapshotFuture.join();

        // Bước 2: Chọn ứng viên sản phẩm (dedup + sort theo vector score)
        List<CatalogSemanticSearchItem> candidates = deduplicateAndSort(snapshot.items());

        // Bước 3: Tạo unified prompt & gọi LLM một lần duy nhất
        ContextRetrievalService.ContextSnapshot candidateSnapshot = new ContextRetrievalService.ContextSnapshot(
                snapshot.profile(),
                snapshot.viewLogs(),
                candidates
            );
        
        String unifiedPrompt = promptBuilder.buildUnifiedPrompt(candidateSnapshot, request.getMessage());
        OllamaChatService.ChatResult result = ollamaChatService.generateReply(unifiedPrompt, "json");
        logger.info("Ollama raw response: [{}]", result.text());

        String replyText;
        int tokenUsed = result.tokenUsed();
        List<CatalogSemanticSearchItem> selectedItems = List.of();

        if (result.fallback()) {
            replyText = "Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau giây lát nhé.";
        } else {
            LlmUnifiedResult parsed = parseLlmUnified(result.text(), candidates);
            
            // Kiểm tra guard (inScope, isSafetySensitive) trực tiếp từ kết quả phân loại của LLM
            if (Boolean.FALSE.equals(parsed.inScope())) {
                replyText = OUT_OF_SCOPE_REPLY;
            } else if (Boolean.TRUE.equals(parsed.isSafetySensitive())) {
                replyText = SAFETY_REPLY;
            } else {
                selectedItems = parsed.items();
                replyText = appendDisclaimer(parsed.reply());
            }
        }

        // Bước 4: Lưu message assistant + cập nhật session/cache
        AiChatMessage assistantMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.ASSISTANT)
                .content(replyText)
                .tokenUsed(tokenUsed)
                .createdAt(Instant.now())
                .expireAt(resolveExpireAt(Instant.now()))
                .build();
        messageRepository.save(assistantMessage);

        session.setLastMessageAt(assistantMessage.getCreatedAt());
        sessionRepository.save(session);

        if (!selectedItems.isEmpty()) {
            persistRecommendations(request.getCustomerId(), selectedItems);
        }
        updateChatContextCache(session, history, userMessage, assistantMessage);

        return AiChatResponse.builder()
                .sessionId(session.getId())
                .reply(replyText)
                .suggestedProducts(buildSuggestions(selectedItems))
                .build();
    }

    // =========================================================================
    // LLM Response Parsing
    // =========================================================================

    /**
     * Parse kết quả JSON thống nhất từ LLM.
     */
    private LlmUnifiedResult parseLlmUnified(
            String llmText,
            List<CatalogSemanticSearchItem> candidates
    ) {
        if (llmText != null && !llmText.isBlank()) {
            try {
                String json = extractJsonObject(llmText);
                if (!json.isBlank()) {
                    LlmUnifiedResponse parsed = objectMapper.readValue(json, LlmUnifiedResponse.class);
                    if (parsed != null) {
                        boolean inScope = parsed.inScope() != null ? parsed.inScope() : true;
                        boolean isSafety = parsed.isSafetySensitive() != null ? parsed.isSafetySensitive() : false;
                        boolean isRec = parsed.recommendationIntent() != null ? parsed.recommendationIntent() : true;
                        String reply = parsed.reply() != null ? parsed.reply().trim() : "";

                        // Xử lý selected_product_ids (áp dụng cho cả recommendation lẫn factual query)
                        List<CatalogSemanticSearchItem> filtered = List.of();
                        if (!candidates.isEmpty()) {
                            List<String> selectedIds = parsed.selectedProductIds();
                            if (selectedIds != null && !selectedIds.isEmpty()) {
                                filtered = candidates.stream()
                                        .filter(item -> item.getProductId() != null
                                                && selectedIds.contains(item.getProductId().toString()))
                                        .collect(Collectors.toList());
                            }
                            // Fallback top-3 chỉ khi là recommendation_intent (không áp dụng cho factual query)
                            if (filtered.isEmpty() && isRec) {
                                filtered = candidates.stream().limit(3).collect(Collectors.toList());
                            }
                        }

                        // Fallback: parse pattern [**uuid1**, **uuid2**] từ reply nếu selected_product_ids vẫn rỗng
                        if (filtered.isEmpty() && !candidates.isEmpty()) {
                            List<String> taggedIds = extractTaggedProductIds(reply);
                            if (!taggedIds.isEmpty()) {
                                filtered = candidates.stream()
                                        .filter(item -> item.getProductId() != null
                                                && taggedIds.contains(item.getProductId().toString()))
                                        .collect(Collectors.toList());
                            }
                        }
                        // Strip pattern [**uuid**] khỏi reply trước khi trả về client
                        reply = stripTaggedProductIds(reply);

                        if (reply.isEmpty()) {
                            reply = isRec ? "Dựa trên nhu cầu của bạn, mình đề xuất một số sản phẩm sau đây kèm theo lý do cụ thể:" : "Mình chưa có câu trả lời cụ thể cho vấn đề này.";
                        }

                        return new LlmUnifiedResult(inScope, isSafety, isRec, filtered, reply);
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to parse LLM response: {}. Raw text: [{}]", ex.getMessage(), llmText, ex);
            }
        }
        
        // Cơ chế dự phòng khi lỗi parse JSON: coi như hợp lệ, thuộc phạm vi và trả về toàn bộ văn bản thô
        boolean hasCandidates = !candidates.isEmpty();
        String fallbackReply = (llmText == null || llmText.isBlank())
                ? "Dựa trên nhu cầu tư vấn của bạn, mình đã tìm thấy một số sản phẩm phù hợp. Để mình giải thích cụ thể lý do lựa chọn từng sản phẩm bên dưới:"
                : llmText.trim();
        List<CatalogSemanticSearchItem> fallbackItems = hasCandidates 
                ? candidates.stream().limit(3).collect(Collectors.toList()) 
                : List.of();
        return new LlmUnifiedResult(true, false, hasCandidates, fallbackItems, fallbackReply);
    }

    private String extractJsonObject(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        // Strip Qwen3 thinking tags <think>...</think> nếu có trong response
        String stripped = response.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (stripped.isBlank()) {
            stripped = response;
        }
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return stripped.substring(start, end + 1);
    }

    /**
     * Extract UUIDs từ pattern [**uuid1**, **uuid2**, ...] trong reply text.
     * Đây là cơ chế fallback khi AI quên điền selected_product_ids nhưng có nhúng ID trong reply.
     */
    List<String> extractTaggedProductIds(String reply) {
        if (reply == null || reply.isBlank()) return List.of();
        List<String> ids = new ArrayList<>();
        Matcher blockMatcher = TAGGED_PRODUCTS_BLOCK.matcher(reply);
        while (blockMatcher.find()) {
            Matcher uuidMatcher = UUID_IN_TEXT.matcher(blockMatcher.group());
            while (uuidMatcher.find()) {
                ids.add(uuidMatcher.group());
            }
        }
        return ids;
    }

    /**
     * Xóa pattern [**uuid1**, **uuid2**] khỏi reply text trước khi trả về client.
     */
    String stripTaggedProductIds(String reply) {
        if (reply == null) return "";
        return TAGGED_PRODUCTS_BLOCK.matcher(reply).replaceAll("").trim();
    }

    // =========================================================================
    // Product Selection Helpers
    // =========================================================================

    /**
     * Dedup theo productId, giữ score cao nhất, sort giảm dần, lấy top CANDIDATE_LIMIT_FOR_LLM.
     */
    private List<CatalogSemanticSearchItem> deduplicateAndSort(List<CatalogSemanticSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductId() != null)
                .filter(item -> item.getScore() != null && item.getScore() > 0)
                .collect(Collectors.toMap(
                        CatalogSemanticSearchItem::getProductId,
                        item -> item,
                        (left, right) -> {
                            double ls = left.getScore() == null ? 0 : left.getScore();
                            double rs = right.getScore() == null ? 0 : right.getScore();
                            return ls >= rs ? left : right;
                        },
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble((CatalogSemanticSearchItem i) ->
                        i.getScore() == null ? 0.0 : i.getScore()).reversed())
                .limit(CANDIDATE_LIMIT_FOR_LLM)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Suggestions / Persistence
    // =========================================================================

    private List<SuggestedProduct> buildSuggestions(List<CatalogSemanticSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductId() != null)
                .distinct()
                .map(item -> SuggestedProduct.builder()
                        .productId(item.getProductId().toString())
                        .name(item.getName())
                        .score(item.getScore())
                        .build())
                .collect(Collectors.toList());
    }

    private void persistRecommendations(String customerId, List<CatalogSemanticSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<AiRecommendation> recommendations = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductId() != null)
                .map(item -> AiRecommendation.builder()
                        .customerId(customerId)
                        .productId(item.getProductId().toString())
                        .score(item.getScore())
                        .reason("chat_context")
                        .type(RecommendationType.SIMILAR)
                        .isClicked(false)
                        .createdAt(now)
                        .build())
                .collect(Collectors.toList());
        recommendationRepository.saveAll(recommendations);
    }

    // =========================================================================
    // Session / Message / Cache
    // =========================================================================

    private AiChatSession resolveSession(String sessionId, String customerId) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<AiChatSession> existing = sessionRepository.findById(sessionId);
            if (existing.isPresent()) {
                AiChatSession session = existing.get();
                if (!Objects.equals(session.getCustomerId(), customerId)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Session does not belong to customer");
                }
                if (session.getStatus() == SessionStatus.CLOSED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Session is closed");
                }
                return session;
            }
        }

        AiChatSession session = AiChatSession.builder()
                .customerId(customerId)
                .status(SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build();
        return sessionRepository.save(session);
    }

    private List<ContextMessage> loadHistory(String sessionId, String customerId) {
        String cacheKey = chatContextKey(sessionId);
        Optional<ChatContextCacheEntry> cached = cacheService.getChatContext(cacheKey);
        if (cached.isPresent() && cached.get().getMessages() != null) {
            return new ArrayList<>(cached.get().getMessages());
        }

        List<AiChatMessage> messages = messageRepository.findBySessionIdAndCustomerIdOrderByCreatedAtAsc(
                sessionId,
                customerId,
                PageRequest.of(0, ragProperties.getMaxMessages())
        );
        return messages.stream()
                .map(this::toContextMessage)
                .collect(Collectors.toList());
    }

    private ContextMessage toContextMessage(AiChatMessage message) {
        return ContextMessage.builder()
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void updateChatContextCache(
            AiChatSession session,
            List<ContextMessage> history,
            AiChatMessage userMessage,
            AiChatMessage assistantMessage
    ) {
        List<ContextMessage> updated = new ArrayList<>(history);
        updated.add(toContextMessage(userMessage));
        updated.add(toContextMessage(assistantMessage));
        int maxMessages = ragProperties.getMaxMessages();
        if (updated.size() > maxMessages) {
            updated = updated.subList(updated.size() - maxMessages, updated.size());
        }

        ChatContextCacheEntry entry = ChatContextCacheEntry.builder()
                .sessionId(session.getId())
                .customerId(session.getCustomerId())
                .cachedAt(Instant.now())
                .messages(updated)
                .build();
        cacheService.saveChatContext(chatContextKey(session.getId()), entry);
    }

    private String chatContextKey(String sessionId) {
        return "ai:chat:context:" + sessionId;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private String appendDisclaimer(String reply) {
        String text = reply == null ? "" : reply.trim();
        if (text.isBlank()) {
            return ADVICE_DISCLAIMER.trim();
        }
        return text + ADVICE_DISCLAIMER;
    }

    private Instant resolveExpireAt(Instant createdAt) {
        if (retentionProperties == null || !retentionProperties.isEnabled()) {
            return null;
        }
        Duration ttl = retentionProperties.getMessageTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        return createdAt.plus(ttl);
    }

    // =========================================================================
    // Internal Records
    // =========================================================================

    private record LlmUnifiedResult(
            boolean inScope,
            boolean isSafetySensitive,
            boolean recommendationIntent,
            List<CatalogSemanticSearchItem> items,
            String reply
    ) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmUnifiedResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("in_scope") Boolean inScope,
            @com.fasterxml.jackson.annotation.JsonProperty("is_safety_sensitive") Boolean isSafetySensitive,
            @com.fasterxml.jackson.annotation.JsonProperty("recommendation_intent") Boolean recommendationIntent,
            @com.fasterxml.jackson.annotation.JsonProperty("selected_product_ids") List<String> selectedProductIds,
            String reply
    ) {}
}
