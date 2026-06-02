package iuh.fit.aiservice.service;

import iuh.fit.aiservice.dto.response.AiChatMessageItem;
import iuh.fit.aiservice.dto.response.AiChatSessionSummary;
import iuh.fit.aiservice.dto.response.CursorPageResponse;
import iuh.fit.aiservice.model.AiChatMessage;
import iuh.fit.aiservice.model.AiChatSession;
import iuh.fit.aiservice.repo.AiChatSessionRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiChatHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final MongoTemplate mongoTemplate;
    private final AiChatSessionRepository sessionRepository;

    public AiChatHistoryService(MongoTemplate mongoTemplate, AiChatSessionRepository sessionRepository) {
        this.mongoTemplate = mongoTemplate;
        this.sessionRepository = sessionRepository;
    }

    public CursorPageResponse<AiChatSessionSummary> listSessions(String customerId, String cursor, Integer limit) {
        int pageSize = sanitizeLimit(limit);
        Query query = new Query();
        query.addCriteria(Criteria.where("customerId").is(customerId));
        CursorToken token = parseCursor(cursor);
        if (token != null) {
            Criteria cursorCriteria = new Criteria().orOperator(
                    Criteria.where("lastMessageAt").lt(token.timestamp()),
                    new Criteria().andOperator(
                            Criteria.where("lastMessageAt").is(token.timestamp()),
                            Criteria.where("id").lt(token.id())
                    )
            );
            query.addCriteria(cursorCriteria);
        }
        query.with(Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("id")));
        query.limit(pageSize + 1);

        List<AiChatSession> sessions = mongoTemplate.find(query, AiChatSession.class);
        CursorPageResponse<AiChatSessionSummary> page = buildSessionPage(sessions, pageSize);
        return page;
    }

    public CursorPageResponse<AiChatMessageItem> listMessages(
            String customerId,
            String sessionId,
            String cursor,
            Integer limit
    ) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Session not found"));
        if (!Objects.equals(session.getCustomerId(), customerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Session does not belong to customer");
        }

        int pageSize = sanitizeLimit(limit);
        Query query = new Query();
        query.addCriteria(Criteria.where("sessionId").is(sessionId));
        query.addCriteria(Criteria.where("customerId").is(customerId));

        CursorToken token = parseCursor(cursor);
        if (token != null) {
            Criteria cursorCriteria = new Criteria().orOperator(
                    Criteria.where("createdAt").lt(token.timestamp()),
                    new Criteria().andOperator(
                            Criteria.where("createdAt").is(token.timestamp()),
                            Criteria.where("id").lt(token.id())
                    )
            );
            query.addCriteria(cursorCriteria);
        }
        query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        query.limit(pageSize + 1);

        List<AiChatMessage> messages = mongoTemplate.find(query, AiChatMessage.class);
        return buildMessagePage(messages, pageSize);
    }

    private CursorPageResponse<AiChatSessionSummary> buildSessionPage(List<AiChatSession> sessions, int pageSize) {
        boolean hasMore = sessions.size() > pageSize;
        List<AiChatSession> slice = hasMore ? sessions.subList(0, pageSize) : sessions;
        String nextCursor = null;
        if (hasMore && !slice.isEmpty()) {
            AiChatSession last = slice.get(slice.size() - 1);
            Instant cursorTime = last.getLastMessageAt() == null ? last.getCreatedAt() : last.getLastMessageAt();
            if (cursorTime != null && last.getId() != null) {
                nextCursor = buildCursor(cursorTime, last.getId());
            }
        }

        List<AiChatSessionSummary> items = slice.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return CursorPageResponse.<AiChatSessionSummary>builder()
                .items(items)
                .nextCursor(nextCursor)
                .build();
    }

    private CursorPageResponse<AiChatMessageItem> buildMessagePage(List<AiChatMessage> messages, int pageSize) {
        boolean hasMore = messages.size() > pageSize;
        List<AiChatMessage> slice = hasMore ? messages.subList(0, pageSize) : messages;
        String nextCursor = null;
        if (hasMore && !slice.isEmpty()) {
            AiChatMessage last = slice.get(slice.size() - 1);
            if (last.getCreatedAt() != null && last.getId() != null) {
                nextCursor = buildCursor(last.getCreatedAt(), last.getId());
            }
        }

        List<AiChatMessageItem> items = slice.stream()
                .map(this::toMessageItem)
                .collect(Collectors.toList());
        return CursorPageResponse.<AiChatMessageItem>builder()
                .items(items)
                .nextCursor(nextCursor)
                .build();
    }

    private AiChatSessionSummary toSummary(AiChatSession session) {
        return AiChatSessionSummary.builder()
                .id(session.getId())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .lastMessageAt(session.getLastMessageAt())
                .build();
    }

    private AiChatMessageItem toMessageItem(AiChatMessage message) {
        return AiChatMessageItem.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .role(message.getRole())
                .content(message.getContent())
                .tokenUsed(message.getTokenUsed())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private int sanitizeLimit(Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Limit must be >= 1");
        }
        return Math.min(effective, MAX_LIMIT);
    }

    private CursorToken parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split("\\|", 2);
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid cursor");
        }
        String timePart = parts[0];
        String idPart = parts[1];
        if (timePart.isBlank() || idPart.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid cursor");
        }
        try {
            Instant time = Instant.parse(timePart);
            return new CursorToken(time, idPart);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid cursor timestamp");
        }
    }

    private String buildCursor(Instant time, String id) {
        return time.toString() + "|" + id;
    }

    private record CursorToken(Instant timestamp, String id) {
    }
}
