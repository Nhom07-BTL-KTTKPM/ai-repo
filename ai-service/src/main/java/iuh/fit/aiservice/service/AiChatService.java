package iuh.fit.aiservice.service;

import iuh.fit.aiservice.client.GeminiClient;
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

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final List<String> DOMAIN_KEYWORDS = Arrays.asList(
        // --- Từ khóa chung về mỹ phẩm & da ---
        "mỹ phẩm", "my pham", "mỹ phẩm",            // không dấu & có dấu
        "cosmetic", "skincare", "chăm sóc da", "cham soc da",
        "dưỡng da", "duong da", "làm đẹp", "lam dep",
        "da", "skin", "da mặt", "da mat", "da body",
        "da dầu", "da dau", "da khô", "da kho",
        "da hỗn hợp", "da hon hop", "da nhạy cảm", "da nhay cam",
        "da mụn", "da mun", "da lão hóa", "da lao hoa",

        // --- Vấn đề về da ---
        "mụn", "mun", "acne", "pimple", "mụn trứng cá", "mun trung ca",
        "mụn đầu đen", "mun dau den", "mụn đầu trắng",
        "mụn bọc", "mụn ẩn", "mụn viêm", "mụn nang",
        "thâm", "tham", "sẹo", "seo", "sẹo mụn",
        "nám", "nam", "tàn nhang", "tan nhang",
        "đốm nâu", "dom nau", "tăng sắc tố", "hyperpigmentation",
        "da không đều màu", "da khong deu mau", "dull skin",
        "lỗ chân lông to", "lo chan long to", "se khít lỗ chân lông",
        "dầu", "dau", "nhờn", "nhờn", "oily",
        "kiềm dầu", "kiem dau", "oil control",
        "khô da", "kho da", "bong tróc", "bong troc",
        "mẩn đỏ", "man do", "đỏ da", "do da",
        "kích ứng", "kich ung", "viêm da", "eczema",
        "chàm", "cham", "rosacea", "vảy nến", "vay nen",

        // --- Mục tiêu dưỡng da ---
        "dưỡng ẩm", "duong am", "cấp ẩm", "cap am", "cấp nước",
        "làm dịu", "lam diu", "phục hồi", "phuc hoi",
        "tái tạo", "tai tao", "chống lão hóa", "chong lao hoa",
        "anti-aging", "anti aging", "anti wrinkle", "chống nhăn",
        "làm trắng", "lam trang", "sáng da", "sang da",
        "đều màu da", "deu mau da", "mờ thâm", "mo tham",
        "giảm mụn", "giam mun", "trị mụn", "tri mun",
        "ngừa mụn", "ngua mun", "se khít lỗ chân lông", "se khit",

        // --- Loại sản phẩm ---
        "kem dưỡng", "kem duong", "cream", "moisturizer",
        "sữa rửa mặt", "sua rua mat", "cleanser", "foam cleanser",
        "gel rửa mặt", "gel rua mat",
        "tẩy trang", "tay trang", "makeup remover",
        "dầu tẩy trang", "dau tay trang", "nước tẩy trang",
        "toner", "nước hoa hồng", "nuoc hoa hong",
        "lotion", "essence", "serum", "ampoule",
        "emulsion", "kem mắt", "eye cream", "kem mat",
        "mặt nạ", "mat na", "mask", "sheet mask",
        "sleeping mask", "wash off mask", "peeling",
        "tẩy tế bào chết", "tay te bao chet", "exfoliator",
        "scrub", "peeling gel", "dầu dưỡng", "facial oil",
        "xịt khoáng", "mist", "xịt dưỡng",

        // --- Thành phần hoạt chất (có thể viết liền hoặc cách) ---
        "retinol", "retinoid", "tretinoin", "adapalene",
        "retinaldehyde", "retinyl palmitate",
        "vitamin c", "vitamin c", "ascorbic acid",
        "niacinamide", "vitamin b3",
        "salicylic acid", "bha", "aha",
        "glycolic acid", "lactic acid", "mandelic acid",
        "azelaic acid", "hyaluronic acid", "hyaluronate",
        "ceramide", "peptide", "copper peptide",
        "matrixyl", "argireline",
        "centella asiatica", "rau má", "rau ma",
        "madecassoside", "cica", "panthenol", "vitamin b5",
        "vitamin e", "ferulic acid", "resveratrol",
        "tranexamic acid", "arbutin", "kojic acid",
        "glutathione", "alpha arbutin",
        "niacin", "zinc pca", "allantoin", "urea",
        "glycerin", "squalane", "squalene",
        "shea butter", "bơ hạt mỡ", "jojoba oil", "dầu jojoba",
        "rosehip oil", "dầu tầm xuân", "tea tree oil", "dầu tràm trà",
        "aloe vera", "nha đam", "nha dam",
        "snail mucin", "ốc sên", "snail secretion filtrate",
        "propolis", "sữa ong chúa", "bee venom",
        "collagen", "elastin", "stem cell", "tế bào gốc",
        "fermented", "lên men", "gạo", "rice", "oat", "yến mạch",
        "pH", "ph", "độ pH", "do pH", "acid", "acidic", "base", "alkaline",
        "axit", "kiềm", "acid/base",

        // --- Chống nắng ---
        "chống nắng", "chong nang", "sunscreen", "sunblock",
        "sữa chống nắng", "gel chống nắng", "stick chống nắng",
        "spf", "pa\\+\\+\\+", "uv", "uva", "uvb",
        "broad spectrum", "phổ rộng", "chỉ số chống nắng",

        // --- Nhãn hiệu phổ biến (lưu ý escape ký tự đặc biệt) ---
        "La Roche-Posay", "La Roche Posay",
        "Vichy", "Bioderma", "Avene", "Eucerin",
        "CeraVe", "Cerave", "Cetaphil", "Neutrogena",
        "Simple", "The Ordinary", "The Inkey List",
        "Paula's Choice", "Paulas Choice",
        "Cosrx", "COSRX", "Klairs", "Dear Klairs",
        "Missha", "Innisfree", "innisfree",
        "Laneige", "Sulwhasoo", "Hada Labo", "HadaLabo",
        "Senka", "Biore", "Anessa", "Skin Aqua",
        "Nivea", "Rohto", "Kose", "Shiseido",
        "Curel", "DHC", "Iunik", "Purito",
        "Beauty of Joseon", "Beauty Of Joseon",
        "Benton", "Etude House", "EtudeHouse",
        "Tony Moly", "Some By Mi", "SomeByMi",
        "Isntree", "Round Lab", "RoundLab",
        "TIRTIR", "Medicube", "Dr.Althea",
        "SKIN1004", "Anua", "ma:nyo", "Manyo",
        "Rovectin", "Torriden", "Numee", "House",
        "L'Oreal", "Loreal", "Garnier", "Olay",
        "Pond's", "Ponds", "Hazeline", "Nuxe",
        "Caudalie", "Kiehl's", "Kiehls",
        "Estee Lauder", "Lancome", "Clinique",
        "SK-II", "Shu Uemura", "Decorte",
        "Whoo", "History of Whoo",
        "Dior", "Chanel", "YSL", "MAC", "Bobbi Brown",
        "Origins", "The Body Shop", "Lush",
        "Mediheal", "Dr.Jart+", "Dr Jart",

        // --- Câu hỏi mua hàng & tương tác ---
        "review", "đánh giá", "review sản phẩm",
        "có tốt không", "có nên mua", "có nên dùng",
        "so sánh", "compare", "loại nào tốt",
        "tư vấn", "tu van", "gợi ý", "suggest",
        "công dụng", "cong dung", "hiệu quả", "hieu qua",
        "cách dùng", "cach dung", "cách sử dụng",
        "thành phần", "thanh phan", "bảng thành phần",
        "full ingredient", "chất lượng", "chat luong",
        "giá", "gia", "giá cả", "mua", "mua ở đâu",
        "order", "ship", "đặt hàng", "dat hang"
    );

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "\\b(?:" + String.join("|", DOMAIN_KEYWORDS) + ")\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final List<String> HARM_KEYWORDS = Arrays.asList(
        "gây hại", "gay hai", "gây hại da",
        "độc hại", "doc hai", "toxic", "độc tố",
        "nguy hiểm", "nguy hiem",
        "ung thư", "ung thu", "cancer",
        "tử vong", "tu vong", "chết người",
        "dị ứng nặng", "di ung nang",
        "phản ứng mạnh", "phan ung manh",
        "phản ứng phụ", "side effect",
        "kích ứng da", "kich ung da",
        "nổi mụn thêm", "nổi mụn tệ hơn",
        "gây mụn", "gay mun", "lên mụn",
        "làm mụn nặng hơn", "breakout",
        "tác hại", "tac hai", "tác dụng xấu",
        "tổn thương da", "ton thuong da",
        "hư da", "hỏng da", "damage skin",
        "bào mòn da", "bao mon da", "mỏng da",
        "châm chích", "sting", "rát", "redness",
        "đỏ da", "do da", "bong tróc da",
        "viêm da tiếp xúc", "dermatitis",
        "có hại không", "co hai khong",
        "có độc không", "co doc khong",
        "có an toàn không", "co an toan khong",
        "an toàn da", "safety skin"
    );

    private static final Pattern HARM_PATTERN = Pattern.compile(
        "\\b(?:" + String.join("|", HARM_KEYWORDS) + ")\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final String OUT_OF_SCOPE_REPLY =
        "Mình chỉ hỗ trợ các câu hỏi về mỹ phẩm, chăm sóc da và những vấn đề liên quan. " +
        "Bạn vui lòng hỏi đúng chủ đề để mình giúp bạn tốt nhất nha!";

    private static final String SAFETY_REPLY =
        "Mình không thể khẳng định sản phẩm nào gây hại nếu thiếu nguồn xác minh chính thống. " +
        "Nếu bạn lo lắng về độ an toàn, hãy kiểm tra bảng thành phần, thử trên vùng da nhỏ " +
        "(patch test), sử dụng đúng hướng dẫn và tham khảo bác sĩ da liễu khi có dấu hiệu bất thường nhé.";

private static final String ADVICE_DISCLAIMER =
    "\n\n Lưu ý: Đây chỉ là gợi ý tham khảo dựa trên thông tin sản phẩm và kinh nghiệm phổ biến. " +
    "\n Trước khi sử dụng, bạn nên: " +
    "\n ① Kiểm tra kỹ bảng thành phần và hướng dẫn từ nhà sản xuất; " +
    "\n ② Thử sản phẩm lên một vùng da nhỏ (patch test) để tránh kích ứng; " +
    "\n ③ Nếu có làn da nhạy cảm, đang điều trị da liễu hoặc cơ địa dị ứng, " +
    "hãy tham khảo thêm ý kiến bác sĩ da liễu trước khi dùng. " +
    "Thông tin từ chatbot không thay thế cho chẩn đoán hay chỉ định y khoa.";

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiRecommendationRepository recommendationRepository;
    private final ContextRetrievalService contextRetrievalService;
    private final PromptBuilder promptBuilder;
    private final GeminiChatService geminiChatService;
    private final AiContextCacheService cacheService;
    private final AiRagProperties ragProperties;
    private final AiChatRetentionProperties retentionProperties;

    public AiChatService(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            AiRecommendationRepository recommendationRepository,
            ContextRetrievalService contextRetrievalService,
            PromptBuilder promptBuilder,
            GeminiChatService geminiChatService,
            AiContextCacheService cacheService,
            AiRagProperties ragProperties,
            AiChatRetentionProperties retentionProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.recommendationRepository = recommendationRepository;
        this.contextRetrievalService = contextRetrievalService;
        this.promptBuilder = promptBuilder;
        this.geminiChatService = geminiChatService;
        this.cacheService = cacheService;
        this.ragProperties = ragProperties;
        this.retentionProperties = retentionProperties;
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
                .expireAt(resolveExpireAt(now))
                .build();
        messageRepository.save(userMessage);

        String guardReply = buildGuardReply(request.getMessage());
        if (guardReply != null) {
            AiChatMessage assistantMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.ASSISTANT)
                .content(guardReply)
                .tokenUsed(0)
                .createdAt(Instant.now())
                .expireAt(resolveExpireAt(Instant.now()))
                .build();
            messageRepository.save(assistantMessage);

            session.setLastMessageAt(assistantMessage.getCreatedAt());
            sessionRepository.save(session);

            updateChatContextCache(session, List.of(), userMessage, assistantMessage);

            return AiChatResponse.builder()
                .sessionId(session.getId())
                .reply(guardReply)
                .suggestedProducts(List.of())
                .build();
        }

        List<ContextMessage> history = loadHistory(session.getId(), request.getCustomerId());
        ContextRetrievalService.ContextSnapshot snapshot = contextRetrievalService.retrieveContext(
                request.getCustomerId(),
                request.getMessage(),
                request.getTopK()
        );

        String prompt = promptBuilder.buildPrompt(snapshot, history, request.getMessage());
        GeminiClient.GeminiChatResult result = geminiChatService.generateReply(prompt);

        String replyText = appendDisclaimer(result.text());
        AiChatMessage assistantMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(request.getCustomerId())
                .role(MessageRole.ASSISTANT)
            .content(replyText)
                .tokenUsed(result.totalTokenCount())
                .createdAt(Instant.now())
                .expireAt(resolveExpireAt(Instant.now()))
                .build();
        messageRepository.save(assistantMessage);

        session.setLastMessageAt(assistantMessage.getCreatedAt());
        sessionRepository.save(session);

        List<SuggestedProduct> suggestions = buildSuggestions(snapshot.items());
        persistRecommendations(request.getCustomerId(), snapshot.items());

        updateChatContextCache(session, history, userMessage, assistantMessage);

        return AiChatResponse.builder()
                .sessionId(session.getId())
            .reply(replyText)
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

    private String buildGuardReply(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            return OUT_OF_SCOPE_REPLY;
        }
        if (!isInDomain(text)) {
            return OUT_OF_SCOPE_REPLY;
        }
        if (isHarmQuery(text)) {
            return SAFETY_REPLY;
        }
        return null;
    }

    private boolean isInDomain(String text) {
        return DOMAIN_PATTERN.matcher(text).find();
    }

    private boolean isHarmQuery(String text) {
        return HARM_PATTERN.matcher(text).find();
    }

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
}