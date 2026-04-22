package discordgateway.stocknode.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stocknode.quote.model.StockQuote;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisQuoteRepository implements QuoteRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final StockRedisKeyFactory stockRedisKeyFactory;

    public RedisQuoteRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            StockRedisKeyFactory stockRedisKeyFactory
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.stockRedisKeyFactory = stockRedisKeyFactory;
    }

    @Override
    public Optional<StockQuote> find(String market, String symbol) {
        String key = stockRedisKeyFactory.quoteKey(market, symbol);
        String payload = stringRedisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, StockQuote.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize cached quote: " + key, exception);
        }
    }

    @Override
    public void save(StockQuote quote, Duration ttl) {
        String key = stockRedisKeyFactory.quoteKey(quote.market(), quote.symbol());
        try {
            String payload = objectMapper.writeValueAsString(quote);
            stringRedisTemplate.opsForValue().set(key, payload, ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize quote for cache: " + key, exception);
        }
    }
}
