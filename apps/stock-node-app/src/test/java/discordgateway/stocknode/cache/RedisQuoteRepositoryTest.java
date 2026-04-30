package discordgateway.stocknode.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisQuoteRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisQuoteRepository redisQuoteRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        redisQuoteRepository = new RedisQuoteRepository(
                stringRedisTemplate,
                objectMapper,
                new StockRedisKeyFactory()
        );
    }

    @Test
    void savesQuoteUsingNormalizedCacheKey() {
        StockQuote stockQuote = new StockQuote(
                "US",
                "aapl",
                new BigDecimal("123.45"),
                Instant.parse("2026-04-22T07:00:00Z")
        );

        redisQuoteRepository.save(stockQuote, Duration.ofMinutes(10));

        verify(valueOperations).set(
                eq("stock:quote:US:AAPL"),
                contains("\"symbol\":\"AAPL\""),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void readsQuoteFromSerializedPayload() throws Exception {
        StockQuote stockQuote = new StockQuote(
                "US",
                "AAPL",
                new BigDecimal("123.45"),
                Instant.parse("2026-04-22T07:00:00Z")
        );
        when(valueOperations.get("stock:quote:US:AAPL"))
                .thenReturn(objectMapper.writeValueAsString(stockQuote));

        assertThat(redisQuoteRepository.find("us", "aapl"))
                .isPresent()
                .get()
                .usingRecursiveComparison()
                .isEqualTo(stockQuote);
    }
}
