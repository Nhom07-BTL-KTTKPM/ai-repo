package iuh.fit.aiservice.service;

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

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Set<String> DOMAIN_KEYWORDS = new LinkedHashSet<>(List.of(
        // Từ khóa gốc
        "my pham", "skincare", "cosmetic", "da", "skin", "serum", "cleanser", "toner",
        "kem duong", "moisturizer", "mat na", "mask", "retinol", "niacinamide",
        "vitamin c", "bha", "aha", "ceramide", "kem chong nang", "sunscreen",
        "mun", "tham", "lo chan long", "da dau", "da kho", "da nhay cam",
        "san pham", "hang", "thuong hieu", "brand", "hang nao", "cua hang",
        "cerave", "laroche posay", "la roche posay", "bioderma", "cetaphil",

        // Thành phần
        "hyaluronic acid", "acid hyaluronic", "salicylic acid", "acid salicylic",
        "glycolic acid", "lactic acid", "azelaic acid", "tranexamic acid",
        "bakuchiol", "peptide", "collagen", "squalane", "centella", "madecassoside",
        "panthenol", "glycerin", "aloe vera", "nha dam", "tram tra", "tea tree",
        "tra xanh", "green tea", "dau dua", "coconut oil", "dau argan",
        "vitamin e", "vitamin b5", "tretinoin", "adapalene", "benzoyl peroxide",
        "zinc", "cica", "allantoin", "arbutin", "kojic acid", "licorice",

        // Loại da & vấn đề
        "da hon hop", "da thuong", "da dang mat nuoc", "da mat nuoc",
        "mun trung ca", "mun dau den", "mun cam", "mun boc", "mun an",
        "seo mun", "vet tham", "tham mun", "nam", "tan nhang", "dom nau",
        "da khong deu mau", "lao hoa", "nep nhan", "vet chan chim",
        "da chay xe", "da xin mau", "lo chan long to", "se khit lo chan long",
        "dau", "nhon", "bong dau", "kho rap", "bong troc", "kich ung", "man do",
        "rosacea", "viem da", "eczema", "cham", "vay nen", "da mun",

        // Dạng sản phẩm
        "gel", "cream", "lotion", "oil", "balm", "sua", "tinh chat", "nuoc",
        "xit", "mist", "bot", "foam", "mat na giay", "sheet mask", "mat na ngu",
        "sleeping mask", "tay te bao chet vat ly", "tay da chet hoa hoc",
        "peel da", "toner pad", "mieng dan mun", "pimple patch",

        // Quy trình skincare
        "routine", "cham soc da", "buoi sang", "buoi toi", "double cleansing",
        "tay trang", "sua rua mat", "nuoc can bang", "kem duong", "kem mat",
        "duong moi", "mat na", "tay da chet", "chong nang", "duong am",

        // Trang điểm
        "makeup", "trang diem", "kem nen", "foundation", "cushion", "phan phu",
        "che khuyet diem", "concealer", "ma hong", "blush", "highlighter",
        "tao khoi", "contour", "phan mat", "eyeshadow", "ke mat", "eyeliner",
        "mascara", "chi ke may", "eyebrow", "son moi", "lipstick", "son tint",
        "lip gloss", "son duong", "lip balm", "xit khoa makeup", "setting spray",

        // Chăm sóc tóc
        "dau goi", "shampoo", "dau xa", "conditioner", "kem u toc", "hair mask",
        "dau duong toc", "hair oil", "xit duong toc", "serum toc", "goi dau",
        "cham soc toc", "hair", "haircare", "da dau", "scalp", "gau", "ngua",
        "rung toc", "kich thich moc toc",

        // Chăm sóc cơ thể
        "sua tam", "body wash", "sua duong the", "body lotion", "kem tay",
        "hand cream", "kem chan", "foot cream", "tay te bao chet body",
        "body scrub", "lan khu mui", "deodorant", "nuoc hoa", "perfume",
        "fragrance",

        // Truy vấn thực tế tiếng Việt
        "cach tri mun", "serum tri tham", "kem chong nang cho da dau",
        "sua rua mat cho da nhay cam", "top kem duong", "review my pham",
        "my pham han quoc", "my pham nhat", "my pham phap", "my pham my",
        "hang noi dia", "xach tay",

        // Thương hiệu mở rộng
        "the ordinary", "paulas choice", "vichy", "avene", "neutrogena", "eucerin",
        "klairs", "cosrx", "innisfree", "laneige", "sulwhasoo", "sk-ii",
        "estee lauder", "lancome", "shiseido", "anessa", "biore", "hada labo",
        "rohto", "melano cc", "skin1004", "some by mi", "senka", "simple",
        "nivea", "ponds", "olay", "loreal", "maybelline", "revlon"
    ));

    private static final Set<String> SAFETY_SENSITIVE_KEYWORDS = new LinkedHashSet<>(List.of(
        "gay hai", "doc hai", "nguy hiem", "ung thu", "side effect",
        "bao mon", "ton thuong da",
        // Mở rộng
        "kich ung", "di ung", "noi man", "do da", "ngua", "phat ban",
        "rat", "cham chich", "bong troc nang", "tac dung phu", "phan ung",
        "an toan", "paraben", "sulfate", "con", "alcohol", "huong lieu",
        "fragrance", "chat bao quan", "preservative", "chat tao mau",
        "silicone", "dau khoang", "mineral oil", "co hai", "doc hai",
        "gay ung thu", "gay kich ung", "cam dung", "thu hoi", "canh bao",
        "FDA", "dermatologist tested", "hypoallergenic", "non-comedogenic"
    ));

    private static final Set<String> BRAND_DISCOVERY_KEYWORDS = new LinkedHashSet<>(List.of(
        "hang", "thuong hieu", "brand", "san pham", "dong san pham", "cerave",
        // Mở rộng
        "hang", "cua hang nao", "thuong hieu nao tot", "brand nao",
        "nen mua hang nao", "dong san pham", "san pham cua", "review hang",
        "so sanh cac hang", "top thuong hieu", "best brand", "cac hang my pham",
        "han quoc", "nhat ban", "phap", "my", "viet nam", "thuan chay", "vegan",
        "cruelty-free", "organic", "thien nhien",
        // Thêm tên thương hiệu làm đối tượng khám phá
        "the ordinary", "paulas choice", "cosrx", "innisfree", "laneige",
        "vichy", "avene", "neutrogena", "bioderma", "la roche posay"
    ));

    private static final Set<String> HARD_BLOCK_KEYWORDS = new LinkedHashSet<>(List.of(
        "chung khoan", "co phieu", "bitcoin", "bong da", "the thao", "chinh tri",
        "code java", "lap trinh", "toan hoc", "vat ly", "lich su", "dia ly",
        // Mở rộng
        "bong chuyen", "cau long", "boi loi", "tennis", "game online", "esports",
        "tieu thuyet", "trinh tham", "khoa hoc vien tuong", "du hanh vu tru",
        "may tinh", "dien thoai", "phan mem", "mang xa hoi", "marketing", "SEO",
        "tieu duong", "tim mach", "huyet ap", "am nhac", "phim anh", "du lich",
        "am thuc", "nau an", "cong thuc mon", "xe co", "o to", "bat dong san",
        "tinh yeu", "hen ho", "meo vat nha bep"
    ));

    private static final Set<String> PRODUCT_SUGGESTION_KEYWORDS = new LinkedHashSet<>(List.of(
        "goi y", "tu van", "nen dung", "nen mua", "san pham", "routine",
        "phu hop", "so sanh", "compare", "review", "de xuat", "chon", "lua chon",
        "gia", "tam", "khoang", "duoi", "tren",
        // Mở rộng
        "suggest", "khuyen", "tot nhat", "top", "ban chay", "best seller",
        "duoc ua chuong", "da dau nen dung gi", "da kho nen dung kem gi",
        "cach dung", "huong dan", "lieu trinh", "ket hop", "buoi sang",
        "buoi toi", "ngay", "dem", "se khit lo chan long", "tri tham",
        "duong trang", "trang da", "routine cho da dau", "cach xay dung routine",
        "buoc skincare", "quy trinh cham soc da", "nen dung san pham nao",
        "tot nhat cho da mun", "top kem chong nang", "best serum",
        "review chi tiet", "so sanh", "su khac biet", "lua chon thay the",
        "dupe", "gia re", "binh dan", "cao cap", "luxury", "hop tui tien",
        "cho hoc sinh", "sinh vien", "van phong", "me bau", "ba bau",
        "cho nam", "cho nu", "da dau mun nen dung gi", "da kho nen dung gi",
        "kem duong am cho mua dong", "sua rua mat mua he", "tu van",
        "khuyen dung", "bac si da lieu khuyen dung", "theo bac si"
    ));

    private static final Set<String> COSMETIC_CONTEXT_KEYWORDS = new LinkedHashSet<>(List.of(
        "my pham", "cham soc da", "duong da", "lam sach", "duong am", "tri mun",
        "serum", "toner", "cleanser", "sunscreen", "moisturizer", "makeup",
        "kem chong nang", "kem duong", "sua rua mat", "mat na", "tay te bao chet",
        "thuong hieu", "brand", "hang", "san pham", "thanh phan", "routine",
        // Mở rộng
        "mun dau den", "mun boc", "seo", "nam", "tan nhang", "lao hoa da",
        "chong lao hoa", "collagen", "elastin", "duong trang", "trang da",
        "sang da", "deu mau da", "cap am", "cap nuoc", "duong am sau",
        "phuc hoi da", "hang rao bao ve da", "skin barrier", "da yeu",
        "da ton thuong", "kich ung", "diu da", "chong oxy hoa", "antioxidant",
        "chong viem", "lam sach sau", "tay trang", "nuoc tay trang",
        "dau tay trang", "sap tay trang", "sua rua mat diu nhe", "gel rua mat",
        "bot rua mat", "mat na dat set", "clay mask", "mat na ngu",
        "sleeping mask", "mat na lot", "peel-off mask", "dau duong", "face oil",
        "dau goi", "dau xa", "mat na toc", "serum toc", "cham soc co the",
        "body care", "tay", "chan", "moi", "lip care", "son duong moi",
        "mat na moi", "tay da chet moi", "duong mi", "eyelash serum",
        "nuoc hoa hong", "xit khoang", "thermal water"
    ));

    private static final Map<String, List<String>> PRODUCT_TYPE_ALIASES = Map.ofEntries(
        // Các mục gốc
        Map.entry("serum", List.of("serum", "tinh chat", "essence", "ampoule", "concentrate")),
        Map.entry("sunscreen", List.of("sunscreen", "sunblock", "kem chong nang", "chong nang")),
        Map.entry("cleanser", List.of("cleanser", "face wash", "foam cleanser", "sua rua mat", "rua mat")),
        Map.entry("moisturizer", List.of("moisturizer", "hydrating cream", "kem duong", "duong am")),
        Map.entry("toner", List.of("toner", "balancing toner", "nuoc can bang")),
        Map.entry("mask", List.of("mask", "mat na")),
        Map.entry("shampoo", List.of("shampoo", "dau goi", "goi dau", "toc", "cham soc toc", "hair", "haircare")),
        Map.entry("gel", List.of("gel")),
        Map.entry("cream", List.of("cream", "kem")),
        Map.entry("makeup_remover", List.of("makeup remover", "tay trang", "nuoc tay trang", "dau tay trang", "sap tay trang", "micellar water")),
        Map.entry("exfoliator", List.of("exfoliator", "tay te bao chet", "tay da chet", "bha", "aha", "pha", "peel")),
        Map.entry("treatment", List.of("treatment", "dac tri", "cham mun", "tri mun", "retinol", "tretinoin")),
        Map.entry("bodycare", List.of("bodycare", "sua tam", "kem tuyet", "duong the", "body lotion", "body wash")),
        Map.entry("makeup", List.of("makeup", "trang diem")),

        // Mở rộng
        Map.entry("hair_conditioner", List.of("conditioner", "dau xa", "xa toc", "kem xa")),
        Map.entry("hair_mask", List.of("hair mask", "kem u toc", "mat na toc")),
        Map.entry("hair_oil", List.of("hair oil", "dau duong toc", "serum toc")),
        Map.entry("scalp_treatment", List.of("scalp treatment", "dieu tri da dau", "cham soc da dau")),
        Map.entry("body_lotion", List.of("body lotion", "sua duong the", "kem duong the", "body cream")),
        Map.entry("hand_cream", List.of("hand cream", "kem duong tay")),
        Map.entry("foot_cream", List.of("foot cream", "kem duong chan")),
        Map.entry("lip_balm", List.of("lip balm", "son duong", "duong moi")),
        Map.entry("eye_cream", List.of("eye cream", "kem mat", "duong mat")),
        Map.entry("face_oil", List.of("face oil", "dau duong mat", "facial oil")),
        Map.entry("mist", List.of("mist", "xit khoang", "facial mist", "setting spray")),
        Map.entry("ampoule", List.of("ampoule", "ampule")),
        Map.entry("essence", List.of("essence", "nuoc than", "first essence")),
        Map.entry("emulsion", List.of("emulsion", "nhu tuong")),
        Map.entry("sheet_mask", List.of("sheet mask", "mat na giay")),
        Map.entry("sleeping_mask", List.of("sleeping mask", "mat na ngu")),
        Map.entry("wash_off_mask", List.of("wash off mask", "mat na rua", "mat na dat set")),
        Map.entry("sunscreen_stick", List.of("sunscreen stick", "son chong nang", "chong nang dang thoi")),
        Map.entry("sun_cushion", List.of("sun cushion", "cushion chong nang")),
        Map.entry("primer", List.of("primer", "kem lot", "base makeup")),
        Map.entry("foundation", List.of("foundation", "kem nen", "cushion", "phan nuoc")),
        Map.entry("concealer", List.of("concealer", "che khuyet diem", "kem che khuyet diem")),
        Map.entry("powder", List.of("powder", "phan phu", "phan bot", "loose powder", "pressed powder")),
        Map.entry("blush", List.of("blush", "ma hong")),
        Map.entry("highlighter", List.of("highlighter", "phan bat sang", "tao khoi sang")),
        Map.entry("contour", List.of("contour", "tao khoi", "phan tao khoi")),
        Map.entry("eyeshadow", List.of("eyeshadow", "phan mat")),
        Map.entry("eyeliner", List.of("eyeliner", "ke mat", "but ke mat")),
        Map.entry("mascara", List.of("mascara", "mascara")),
        Map.entry("eyebrow", List.of("eyebrow", "chi ke may", "bot ke may", "gel ke may", "ke chan may")),
        Map.entry("lipstick", List.of("lipstick", "son moi", "son thoi", "son kem")),
        Map.entry("lip_tint", List.of("lip tint", "son tint", "son nuoc")),
        Map.entry("lip_gloss", List.of("lip gloss", "son bong")),
        Map.entry("lip_liner", List.of("lip liner", "chi ke vien moi")),
        Map.entry("setting_spray", List.of("setting spray", "xit khoa makeup", "xit co dinh makeup")),
        Map.entry("nail_polish", List.of("nail polish", "son mong tay")),
        Map.entry("perfume", List.of("perfume", "nuoc hoa", "eau de parfum", "eau de toilette", "cologne")),
        Map.entry("deodorant", List.of("deodorant", "lan khu mui", "xit khu mui"))
    );

    private static final String OUT_OF_SCOPE_REPLY =
            "Mình chỉ hỗ trợ các câu hỏi về mỹ phẩm, chăm sóc da và thành phần liên quan. "
                    + "Bạn hãy hỏi đúng chủ đề để mình tư vấn tốt hơn nhé.";

    private static final String SAFETY_REPLY =
            "Mình không thể khẳng định độ an toàn của sản phẩm nếu thiếu nguồn xác minh rõ ràng. "
                    + "Bạn nên kiểm tra bảng thành phần, thử trước trên vùng da nhỏ và tham khảo bác sĩ da liễu nếu da đang nhạy cảm hoặc đang điều trị.";

    private static final String ADVICE_DISCLAIMER =
            "\n\nBạn có thể xem thêm bảng thành phần, cách sử dụng và thử trước trên một vùng da nhỏ để yên tâm hơn.";

    private static final int DEFAULT_SUGGESTED_TOP_N = 3;
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

    public AiChatService(
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            AiRecommendationRepository recommendationRepository,
            ContextRetrievalService contextRetrievalService,
            PromptBuilder promptBuilder,
            OllamaChatService ollamaChatService,
            AiContextCacheService cacheService,
            AiRagProperties ragProperties,
            AiChatRetentionProperties retentionProperties
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
            return saveGuardReply(session, request.getCustomerId(), userMessage, guardReply);
        }

        boolean recommendationIntent = isRecommendationIntent(request.getMessage());
        int retrievalTopK = resolveRetrievalTopK(request.getTopK(), recommendationIntent);

        CompletableFuture<List<ContextMessage>> historyFuture = CompletableFuture.supplyAsync(
                () -> loadHistory(session.getId(), request.getCustomerId())
        );
        CompletableFuture<ContextRetrievalService.ContextSnapshot> snapshotFuture = CompletableFuture.supplyAsync(
                () -> contextRetrievalService.retrieveContext(
                        request.getCustomerId(),
                        request.getMessage(),
                        retrievalTopK
                )
        );

        List<ContextMessage> history = historyFuture.join();
        ContextRetrievalService.ContextSnapshot snapshot = snapshotFuture.join();

        boolean shouldSuggestProducts = recommendationIntent || shouldSuggestProducts(request.getMessage(), snapshot.items());
        List<CatalogSemanticSearchItem> selectedItems = shouldSuggestProducts
                ? selectSuggestedItems(snapshot.items(), request.getMessage(), request.getTopK())
                : List.of();

        String replyText;
        int tokenUsed = 0;
        if (shouldSuggestProducts) {
            replyText = buildRecommendationReply(request.getMessage(), selectedItems);
        } else {
            String prompt = promptBuilder.buildPrompt(snapshot, history, request.getMessage());
            OllamaChatService.ChatResult result = ollamaChatService.generateReply(prompt);
            tokenUsed = result.tokenUsed();
            replyText = buildGeneralReply(result.text());
        }

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

        List<SuggestedProduct> suggestions = buildSuggestions(selectedItems);
        if (shouldSuggestProducts && !selectedItems.isEmpty()) {
            persistRecommendations(request.getCustomerId(), selectedItems);
        }

        updateChatContextCache(session, history, userMessage, assistantMessage);

        return AiChatResponse.builder()
                .sessionId(session.getId())
                .reply(replyText)
                .suggestedProducts(suggestions)
                .build();
    }

    private AiChatResponse saveGuardReply(
            AiChatSession session,
            String customerId,
            AiChatMessage userMessage,
            String guardReply
    ) {
        AiChatMessage assistantMessage = AiChatMessage.builder()
                .sessionId(session.getId())
                .customerId(customerId)
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

    private String chatContextKey(String sessionId) {
        return "ai:chat:context:" + sessionId;
    }

    private String buildGuardReply(String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return OUT_OF_SCOPE_REPLY;
        }
        if (containsKeyword(text, HARD_BLOCK_KEYWORDS) && !containsKeyword(text, COSMETIC_CONTEXT_KEYWORDS)) {
            return OUT_OF_SCOPE_REPLY;
        }
        if (!isAllowedScope(text)) {
            return OUT_OF_SCOPE_REPLY;
        }
        if (containsKeyword(text, SAFETY_SENSITIVE_KEYWORDS)) {
            return SAFETY_REPLY;
        }
        return null;
    }

    private String appendDisclaimer(String reply) {
        String text = reply == null ? "" : reply.trim();
        if (text.isBlank()) {
            return ADVICE_DISCLAIMER.trim();
        }
        return text + ADVICE_DISCLAIMER;
    }

    private List<CatalogSemanticSearchItem> selectSuggestedItems(
            List<CatalogSemanticSearchItem> items,
            String message,
            Integer topKOverride
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        String normalizedMessage = normalize(message);
        String requestedBrand = extractRequestedBrand(normalizedMessage, items);
        String requestedProductType = extractRequestedProductType(normalizedMessage, items);
        int maxCount = resolveSuggestedCount(topKOverride);
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductId() != null)
                .filter(item -> item.getScore() != null && item.getScore() > 0)
                .filter(item -> matchesIntent(item, normalizedMessage, requestedBrand, requestedProductType))
                .sorted(suggestionComparator(normalizedMessage, requestedBrand, requestedProductType))
                .collect(Collectors.toMap(
                        CatalogSemanticSearchItem::getProductId,
                        item -> item,
                        (left, right) -> {
                            Double leftScore = left.getScore();
                            Double rightScore = right.getScore();
                            if (leftScore == null) {
                                return right;
                            }
                            if (rightScore == null) {
                                return left;
                            }
                            return leftScore >= rightScore ? left : right;
                        },
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(suggestionComparator(normalizedMessage, requestedBrand, requestedProductType))
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    private int resolveSuggestedCount(Integer topKOverride) {
        if (topKOverride == null || topKOverride < 1) {
            return DEFAULT_SUGGESTED_TOP_N;
        }
        return Math.min(topKOverride, DEFAULT_SUGGESTED_TOP_N);
    }

    private Comparator<CatalogSemanticSearchItem> suggestionComparator(
            String normalizedMessage,
            String requestedBrand,
            String requestedProductType
    ) {
        return Comparator.comparingDouble(
                (CatalogSemanticSearchItem item) -> scoreForSuggestion(item, normalizedMessage, requestedBrand, requestedProductType)
        ).reversed();
    }

    private String buildRecommendationReply(String message, List<CatalogSemanticSearchItem> selectedItems) {
        StringBuilder builder = new StringBuilder();
        if (selectedItems != null && !selectedItems.isEmpty()) {
            builder.append(buildRecommendationIntro(message, selectedItems)).append("\n");
            int index = 1;
            for (CatalogSemanticSearchItem item : selectedItems) {
                if (item == null || item.getName() == null || item.getName().isBlank()) {
                    continue;
                }
                builder.append(index).append(". ").append(item.getName().trim());
                String reason = buildSuggestionReason(item);
                if (!reason.isBlank()) {
                    builder.append(" - ").append(reason);
                }
                builder.append("\n");
                index++;
            }
            builder.append("\n");
            builder.append(buildRecommendationExplanation(message, selectedItems));
        } else {
            builder.append(buildNoRecommendationReply(message));
        }
        return appendDisclaimer(builder.toString());
    }

    private String buildGeneralReply(String modelText) {
        String explanation = modelText == null ? "" : modelText.trim();
        if (!explanation.isBlank()) {
            return appendDisclaimer(explanation);
        }
        return "Mình chưa có đủ thông tin để gợi ý thật sát. Bạn có thể nói rõ hơn về loại da, vấn đề da, mức giá hoặc thương hiệu bạn đang quan tâm không?" + ADVICE_DISCLAIMER;
    }

    private boolean shouldSuggestProducts(String message, List<CatalogSemanticSearchItem> items) {
        String normalized = normalize(message);
        return containsKeyword(normalized, PRODUCT_SUGGESTION_KEYWORDS)
                || containsKeyword(normalized, BRAND_DISCOVERY_KEYWORDS)
                || (!normalized.isBlank() && items != null && !items.isEmpty());
    }

    private boolean isRecommendationIntent(String message) {
        String normalized = normalize(message);
        return containsKeyword(normalized, PRODUCT_SUGGESTION_KEYWORDS)
                || containsKeyword(normalized, BRAND_DISCOVERY_KEYWORDS)
                || normalized.contains("mua")
                || normalized.contains("tim")
                || normalized.contains("chon");
    }

    private int resolveRetrievalTopK(Integer topKOverride, boolean recommendationIntent) {
        int requested = topKOverride == null ? ragProperties.getTopK() : topKOverride;
        int safeRequested = Math.max(1, requested);
        if (!recommendationIntent) {
            return safeRequested;
        }
        return Math.max(safeRequested, RECOMMENDATION_RETRIEVAL_TOP_K);
    }

    private boolean isAllowedScope(String text) {
        return containsKeyword(text, DOMAIN_KEYWORDS)
                || containsKeyword(text, COSMETIC_CONTEXT_KEYWORDS)
                || containsKeyword(text, PRODUCT_SUGGESTION_KEYWORDS)
                || containsKeyword(text, BRAND_DISCOVERY_KEYWORDS);
    }

    private boolean matchesIntent(
            CatalogSemanticSearchItem item,
            String message,
            String requestedBrand,
            String requestedProductType
    ) {
        return matchesBrand(item, requestedBrand)
                && matchesProductType(item, requestedProductType)
                && matchesPrice(item, message);
    }

    private boolean matchesBrand(CatalogSemanticSearchItem item, String requestedBrand) {
        if (requestedBrand == null || requestedBrand.isBlank()) {
            return true;
        }
        String brand = normalize(item.getBrandName());
        if (brand.isBlank()) {
            return true;
        }
        return brand.equals(requestedBrand);
    }

    private boolean matchesPrice(CatalogSemanticSearchItem item, String message) {
        Integer targetPrice = extractTargetPriceInThousand(message);
        if (targetPrice == null) {
            return true;
        }
        BigDecimal min = item.getMinPrice();
        BigDecimal max = item.getMaxPrice();
        if (min == null && max == null) {
            return true;
        }
        BigDecimal target = BigDecimal.valueOf(targetPrice.longValue() * 1000L);
        BigDecimal lowerBound = target.multiply(BigDecimal.valueOf(0.7));
        BigDecimal upperBound = target.multiply(BigDecimal.valueOf(1.3));
        BigDecimal effectiveMin = min == null ? max : min;
        BigDecimal effectiveMax = max == null ? min : max;
        if (effectiveMin == null || effectiveMax == null) {
            return true;
        }
        return effectiveMax.compareTo(lowerBound) >= 0 && effectiveMin.compareTo(upperBound) <= 0;
    }

    private Double scoreForSuggestion(
            CatalogSemanticSearchItem item,
            String message,
            String requestedBrand,
            String requestedProductType
    ) {
        double score = item.getScore() == null ? 0.0 : item.getScore();
        if (matchesBrand(item, requestedBrand)) {
            score += 2.5;
        }
        if (matchesProductType(item, requestedProductType)) {
            score += 2.0;
        }
        Integer targetPrice = extractTargetPriceInThousand(message);
        if (targetPrice != null) {
            score += priceCloseness(item, targetPrice);
        }
        score += lexicalBoost(item, message);
        return score;
    }

    private double lexicalBoost(CatalogSemanticSearchItem item, String message) {
        Set<String> queryTokens = extractTokens(message);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        double boost = 0;
        boost += tokenOverlapScore(queryTokens, extractTokens(normalize(item.getName()))) * 1.4;
        boost += tokenOverlapScore(queryTokens, extractTokens(normalize(item.getCategoryName()))) * 0.8;
        boost += tokenOverlapScore(queryTokens, extractTokens(normalize(item.getBrandName()))) * 2.0;
        
        if (item.getSuitableSkinTypes() != null && !item.getSuitableSkinTypes().isEmpty()) {
            boost += tokenOverlapScore(queryTokens, extractTokens(normalize(String.join(" ", item.getSuitableSkinTypes())))) * 2.5;
        }
        if (item.getSkinConcerns() != null && !item.getSkinConcerns().isEmpty()) {
            boost += tokenOverlapScore(queryTokens, extractTokens(normalize(String.join(" ", item.getSkinConcerns())))) * 2.5;
        }
        
        return boost;
    }

    private double tokenOverlapScore(Set<String> queryTokens, Set<String> fieldTokens) {
        if (queryTokens.isEmpty() || fieldTokens.isEmpty()) {
            return 0;
        }
        long matched = queryTokens.stream().filter(fieldTokens::contains).count();
        return (double) matched / (double) queryTokens.size();
    }

    private String extractRequestedBrand(String message, List<CatalogSemanticSearchItem> items) {
        if (message == null || message.isBlank() || items == null || items.isEmpty()) {
            return null;
        }
        Set<String> queryTokens = extractTokens(message);
        String bestBrand = null;
        int bestScore = 0;
        for (CatalogSemanticSearchItem item : items) {
            String normalizedBrand = normalize(item.getBrandName());
            if (normalizedBrand.isBlank()) {
                continue;
            }
            Set<String> brandTokens = extractTokens(normalizedBrand);
            int score = 0;
            for (String token : queryTokens) {
                if (brandTokens.contains(token) || normalizedBrand.contains(token)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestBrand = normalizedBrand;
            }
        }
        return bestScore > 0 ? bestBrand : null;
    }

    private String extractRequestedProductType(String message, List<CatalogSemanticSearchItem> items) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String directType = extractCanonicalProductType(message);
        if (directType != null) {
            return directType;
        }
        Set<String> queryTokens = extractTokens(message);
        Set<String> canonicalQueryTypes = expandCanonicalTypes(queryTokens);
        if (items == null || items.isEmpty()) {
            return null;
        }
        String bestType = null;
        int bestScore = 0;
        for (CatalogSemanticSearchItem item : items) {
            Set<String> itemTokens = itemProductTypeTokens(item);
            int score = 0;
            for (String token : canonicalQueryTypes) {
                if (itemTokens.contains(token)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = itemTokens.stream().filter(PRODUCT_TYPE_ALIASES::containsKey).findFirst().orElse(null);
            }
        }
        return bestType;
    }

    private boolean matchesProductType(CatalogSemanticSearchItem item, String requestedProductType) {
        if (requestedProductType == null || requestedProductType.isBlank()) {
            return true;
        }
        return itemProductTypeTokens(item).contains(requestedProductType);
    }

    private String extractCanonicalProductType(String message) {
        String normalizedMessage = normalize(message);
        String bestType = null;
        int bestAliasLength = 0;
        for (Map.Entry<String, List<String>> entry : PRODUCT_TYPE_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (!alias.isBlank() && normalizedMessage.contains(alias) && alias.length() > bestAliasLength) {
                    bestType = entry.getKey();
                    bestAliasLength = alias.length();
                }
            }
        }
        return bestType;
    }

    private Set<String> itemProductTypeTokens(CatalogSemanticSearchItem item) {
        Set<String> itemTokens = new LinkedHashSet<>();
        itemTokens.addAll(extractTokens(normalize(item.getCategoryName())));
        itemTokens.addAll(extractTokens(normalize(item.getName())));
        itemTokens.addAll(extractTokens(normalize(item.getDescription())));
        itemTokens.addAll(expandCanonicalTypes(itemTokens));
        for (Map.Entry<String, List<String>> entry : PRODUCT_TYPE_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                String normalizedAlias = normalize(alias);
                if (normalize(item.getCategoryName()).contains(normalizedAlias)
                        || normalize(item.getName()).contains(normalizedAlias)
                        || normalize(item.getDescription()).contains(normalizedAlias)) {
                    itemTokens.add(entry.getKey());
                    break;
                }
            }
        }
        return itemTokens;
    }

    private Set<String> expandCanonicalTypes(Set<String> tokens) {
        Set<String> expanded = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : PRODUCT_TYPE_ALIASES.entrySet()) {
            if (tokens.contains(entry.getKey())) {
                expanded.add(entry.getKey());
                continue;
            }
            for (String alias : entry.getValue()) {
                Set<String> aliasTokens = extractTokens(alias);
                if (!aliasTokens.isEmpty() && tokens.containsAll(aliasTokens)) {
                    expanded.add(entry.getKey());
                    break;
                }
            }
        }
        return expanded;
    }

    private double priceCloseness(CatalogSemanticSearchItem item, int targetPriceInThousand) {
        BigDecimal min = item.getMinPrice();
        BigDecimal max = item.getMaxPrice();
        if (min == null && max == null) {
            return 0;
        }
        BigDecimal effectiveMin = min == null ? max : min;
        BigDecimal effectiveMax = max == null ? min : max;
        if (effectiveMin == null || effectiveMax == null) {
            return 0;
        }
        BigDecimal avg = effectiveMin.add(effectiveMax).divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        double target = targetPriceInThousand * 1000.0;
        double distance = Math.abs(avg.doubleValue() - target);
        return Math.max(0, 0.8 - (distance / Math.max(target, 1.0)));
    }

    private Integer extractTargetPriceInThousand(String message) {
        if (message == null || message.isBlank() || !message.contains("gia")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+[\\.,]?\\d*)\\s*(k|nghin|ngan|tr|trieu)?").matcher(message);
        while (matcher.find()) {
            String rawNumber = matcher.group(1);
            String unit = matcher.group(2);
            if (rawNumber == null || rawNumber.isBlank()) {
                continue;
            }
            double value = Double.parseDouble(rawNumber.replace(",", "."));
            if (unit == null || unit.isBlank() || unit.equals("k") || unit.equals("nghin") || unit.equals("ngan")) {
                return (int) Math.round(value);
            }
            if (unit.equals("tr") || unit.equals("trieu")) {
                return (int) Math.round(value * 1000);
            }
        }
        return null;
    }

    private Set<String> extractTokens(String message) {
        if (message == null || message.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(message.split("[^a-z0-9]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildRecommendationExplanation(String message, List<CatalogSemanticSearchItem> items) {
        List<String> reasons = new ArrayList<>();
        String normalized = normalize(message);
        if (containsKeyword(normalized, BRAND_DISCOVERY_KEYWORDS)) {
            String brand = items.stream()
                    .map(CatalogSemanticSearchItem::getBrandName)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);
            if (brand != null) {
                reasons.add("Mình ưu tiên các sản phẩm của " + brand + " để danh sách bám sát hơn với điều bạn đang tìm.");
            }
        }
        Integer targetPrice = extractTargetPriceInThousand(normalized);
        if (targetPrice != null) {
            reasons.add("Mình cũng canh theo tầm giá khoảng " + targetPrice + "k để bạn dễ chọn hơn.");
        }
        if (reasons.isEmpty()) {
            return "Nếu bạn muốn, mình có thể lọc tiếp theo loại da, vấn đề da hoặc mức giá cụ thể hơn.";
        }
        return String.join(" ", reasons);
    }

    private String buildNoRecommendationReply(String message) {
        Integer targetPrice = extractTargetPriceInThousand(normalize(message));
        if (targetPrice != null) {
            return "Mình chưa tìm được sản phẩm thật sự sát với tầm giá bạn muốn trong kho dữ liệu hiện tại. Bạn có thể nới ngân sách thêm một chút, hoặc nói rõ hơn về loại sản phẩm, loại da hay thương hiệu để mình gợi ý đúng hơn.";
        }
        return "Mình chưa tìm được gợi ý thật sự ăn khớp từ kho dữ liệu hiện tại. Bạn thử nói rõ hơn về thương hiệu, loại sản phẩm, vấn đề da hoặc mức giá để mình chọn lại sát nhu cầu hơn nhé.";
    }

    private String buildSuggestionReason(CatalogSemanticSearchItem item) {
        List<String> reasons = new ArrayList<>();
        if (item.getCategoryName() != null && !item.getCategoryName().isBlank()) {
            reasons.add("thuộc nhóm " + item.getCategoryName().trim());
        }
        if (item.getBrandName() != null && !item.getBrandName().isBlank()) {
            reasons.add("đến từ thương hiệu " + item.getBrandName().trim());
        }
        if (item.getSkinConcerns() != null && !item.getSkinConcerns().isEmpty()) {
            reasons.add("nổi bật ở khả năng hỗ trợ " + String.join(", ", item.getSkinConcerns()));
        }
        if (item.getSuitableSkinTypes() != null && !item.getSuitableSkinTypes().isEmpty()) {
            reasons.add("hợp với " + String.join(", ", item.getSuitableSkinTypes()));
        }
        return String.join(", ", reasons);
    }

    private String buildRecommendationIntro(String message, List<CatalogSemanticSearchItem> items) {
        String normalized = normalize(message);
        String productType = extractRequestedProductType(normalized, items);
        Integer targetPrice = extractTargetPriceInThousand(normalized);
        String brand = items.stream()
                .map(CatalogSemanticSearchItem::getBrandName)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);

        List<String> details = new ArrayList<>();
        if (productType != null && !productType.isBlank()) {
            details.add(toVietnameseProductType(productType));
        }
        if (brand != null && containsKeyword(normalized, BRAND_DISCOVERY_KEYWORDS)) {
            details.add("của " + brand);
        }
        if (targetPrice != null) {
            details.add("tầm " + targetPrice + "k");
        }

        if (details.isEmpty()) {
            return "Mình chọn ra vài sản phẩm khá hợp để bạn tham khảo:";
        }
        return "Mình đã chọn ra vài gợi ý " + String.join(" ", details) + " để bạn dễ tham khảo:";
    }

    private String toVietnameseProductType(String productType) {
        return switch (productType) {
            case "sunscreen" -> "kem chống nắng";
            case "cleanser" -> "sữa rửa mặt";
            case "moisturizer" -> "kem dưỡng";
            case "mask" -> "mặt nạ";
            case "shampoo" -> "dầu gội";
            case "toner" -> "toner";
            case "serum" -> "serum";
            case "gel" -> "gel";
            case "cream" -> "kem";
            case "makeup" -> "sản phẩm trang điểm";
            case "makeup_remover" -> "tẩy trang";
            case "exfoliator" -> "tẩy tế bào chết";
            case "treatment" -> "sản phẩm đặc trị";
            case "bodycare" -> "sản phẩm chăm sóc cơ thể";
            default -> productType;
        };
    }
    private boolean containsKeyword(String text, Set<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
        String result = normalized.replaceAll("\\s+", " ");
        result = result.replace("cevare", "cerave");
        result = result.replace("inisfree", "innisfree");
        return result;
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


