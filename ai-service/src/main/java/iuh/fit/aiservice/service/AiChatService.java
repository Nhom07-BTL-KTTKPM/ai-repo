package iuh.fit.aiservice.service;

import iuh.fit.aiservice.client.GeminiClient;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiRecommendationRepository recommendationRepository;
    private final ContextRetrievalService contextRetrievalService;
    private final PromptBuilder promptBuilder;
    private final GeminiChatService geminiChatService;
    private final AiContextCacheService cacheService;
    private final AiRagProperties ragProperties;

    public AiChatService(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            AiRecommendationRepository recommendationRepository,
            ContextRetrievalService contextRetrievalService,
            PromptBuilder promptBuilder,
            GeminiChatService geminiChatService,
            AiContextCacheService cacheService,
            AiRagProperties ragProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.recommendationRepository = recommendationRepository;
        this.contextRetrievalService = contextRetrievalService;
        this.promptBuilder = promptBuilder;
        this.geminiChatService = geminiChatService;
        this.cacheService = cacheService;
        this.ragProperties = ragProperties;
    }

    public AiChatResponse chat(AiChatRequest request) {
        AiChatSession session = resolveSession(request.getSessionId(), request.getCustomerId());
        Instant now = Instant.now();

        AiChatMessage userMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.USER)
                .content(request.getMessage())
                .createdAt(now)
                .build();
        messageRepository.save(userMessage);

        List<ContextMessage> history = loadHistory(session.getId(), request.getCustomerId());
        ContextRetrievalService.ContextSnapshot snapshot = contextRetrievalService.retrieveContext(
                request.getCustomerId(),
                request.getMessage(),
                request.getTopK()
        );

        String prompt = promptBuilder.buildPrompt(snapshot, history, request.getMessage());
        GeminiClient.GeminiChatResult result = geminiChatService.generateReply(prompt);

        AiChatMessage assistantMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.ASSISTANT)
                .content(result.text())
                .tokenUsed(result.totalTokenCount())
                .createdAt(Instant.now())
                .build();
        messageRepository.save(assistantMessage);

        session.setLastMessageAt(assistantMessage.getCreatedAt());
        sessionRepository.save(session);

        List<SuggestedProduct> suggestions = buildSuggestions(snapshot.items());
        persistRecommendations(request.getCustomerId(), snapshot.items());

        updateChatContextCache(session, history, userMessage, assistantMessage);

        return AiChatResponse.builder()
                .sessionId(session.getId())
                .reply(result.text())
                .suggestedProducts(suggestions)
                .build();
    }

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

    private List<SuggestedProduct> buildSuggestions(List<CatalogSemanticSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
            .filter(Objects::nonNull)
            .filter(item -> item.getProductId() != null)
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

    private String chatContextKey(String sessionId) {
        return "ai:chat:context:" + sessionId;
    }
}