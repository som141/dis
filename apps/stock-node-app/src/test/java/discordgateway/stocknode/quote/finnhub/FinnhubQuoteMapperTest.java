package discordgateway.stocknode.quote.finnhub;

import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FinnhubQuoteMapperTest {

    @Test
    void mapsFinnhubPayloadIntoInternalQuote() {
        StockWatchlistEntity item = Mockito.mock(StockWatchlistEntity.class);
        Mockito.when(item.normalizedMarket()).thenReturn("US");
        Mockito.when(item.normalizedSymbol()).thenReturn("NVDA");

        FinnhubQuoteMapper mapper = new FinnhubQuoteMapper(
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        StockQuote quote = mapper.map(
                item,
                new FinnhubQuoteResponse(
                        new BigDecimal("216.61"),
                        new BigDecimal("1.23"),
                        new BigDecimal("4.00"),
                        new BigDecimal("220.00"),
                        new BigDecimal("210.00"),
                        new BigDecimal("212.00"),
                        new BigDecimal("215.38")
                )
        );

        assertThat(quote.market()).isEqualTo("us");
        assertThat(quote.symbol()).isEqualTo("NVDA");
        assertThat(quote.price()).isEqualByComparingTo("216.61");
        assertThat(quote.provider()).isEqualTo("finnhub");
        assertThat(quote.changeRate()).isEqualByComparingTo("4.00");
        assertThat(quote.quotedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
    }
}
