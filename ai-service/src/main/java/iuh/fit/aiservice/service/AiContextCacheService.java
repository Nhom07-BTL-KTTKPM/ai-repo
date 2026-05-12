package iuh.fit.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.aiservice.config.AiCacheProperties;
import iuh.fit.aiservice.dto.cache.ChatContextCacheEntry;
import iuh.fit.aiservice.dto.cache.QueryCacheEntry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class AiContextCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiCacheProperties cacheProperties;

    public AiContextCacheService(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            AiCacheProperties cacheProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    public Optional<QueryCacheEntry> getQueryCache(String key) {
        Object value = valueOps().get(key);
        return Optional.ofNullable(convert(value, QueryCacheEntry.class));
    }

    public void saveQueryCache(String key, QueryCacheEntry entry) {
        Duration ttl = cacheProperties.getQueryTtl();
        valueOps().set(key, entry, ttl);
    }

    public void deleteQueryCache(String key) {
        redisTemplate.delete(key);
    }

    public Optional<ChatContextCacheEntry> getChatContext(String key) {
        Object value = valueOps().get(key);
        return Optional.ofNullable(convert(value, ChatContextCacheEntry.class));
    }

    public void saveChatContext(String key, ChatContextCacheEntry entry) {
        Duration ttl = cacheProperties.getContextTtl();
        valueOps().set(key, entry, ttl);
    }

    public void deleteChatContext(String key) {
        redisTemplate.delete(key);
    }

    private ValueOperations<String, Object> valueOps() {
        return redisTemplate.opsForValue();
    }

    private <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return objectMapper.convertValue(value, type);
    }
}
