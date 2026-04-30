package discordgateway.stocknode.quote.provider;

import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FallbackQuoteProviderTest {

    @Test
    void usesFallbackWhenPrimaryFails() {
        QuoteProvider primary = mock(QuoteProvider.class);
        QuoteProvider fallback = mock(QuoteProvider.class);
        when(primary.providerName()).thenReturn("alphavantage");
        when(fallback.providerName()).thenReturn("mock");
        when(primary.fetchQuote("us", "AAPL")).thenThrow(new IllegalStateException("boom"));
        when(fallback.fetchQuote("us", "AAPL")).thenReturn(
                new StockQuote("us", "AAPL", new BigDecimal("111.11"), Instant.parse("2026-05-01T00:00:00Z"))
        );

        FallbackQuoteProvider provider = new FallbackQuoteProvider(primary, fallback, true);

        StockQuote stockQuote = provider.fetchQuote("us", "AAPL");

        assertThat(stockQuote.price()).isEqualByComparingTo("111.11");
        assertThat(provider.providerName()).isEqualTo("alphavantage");
    }

    @Test
    void rethrowsPrimaryFailureWhenFallbackDisabled() {
        QuoteProvider primary = mock(QuoteProvider.class);
        QuoteProvider fallback = mock(QuoteProvider.class);
        when(primary.providerName()).thenReturn("alphavantage");
        when(primary.fetchQuote("us", "AAPL")).thenThrow(new IllegalStateException("boom"));

        FallbackQuoteProvider provider = new FallbackQuoteProvider(primary, fallback, false);

        assertThatThrownBy(() -> provider.fetchQuote("us", "AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
    }
}
