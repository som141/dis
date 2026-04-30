package discordgateway.stocknode.quote.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import discordgateway.stocknode.bootstrap.StockProviderProperties;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantageQuoteProviderTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void parsesGlobalQuotePayload() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/query", exchange -> {
            String body = """
                    {
                      "Global Quote": {
                        "01. symbol": "AAPL",
                        "05. price": "123.4500"
                      }
                    }
                    """;
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        httpServer.start();

        StockProviderProperties properties = new StockProviderProperties();
        properties.setAlphaVantageBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        properties.setAlphaVantageApiKey("test-key");

        AlphaVantageQuoteProvider provider = new AlphaVantageQuoteProvider(
                new ObjectMapper(),
                properties,
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        StockQuote stockQuote = provider.fetchQuote("us", "AAPL");

        assertThat(stockQuote.symbol()).isEqualTo("AAPL");
        assertThat(stockQuote.price()).isEqualByComparingTo("123.4500");
        assertThat(stockQuote.quotedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
    }

    @Test
    void rejectsMissingApiKey() {
        StockProviderProperties properties = new StockProviderProperties();
        properties.setAlphaVantageBaseUrl("http://127.0.0.1");
        properties.setAlphaVantageApiKey("");

        AlphaVantageQuoteProvider provider = new AlphaVantageQuoteProvider(
                new ObjectMapper(),
                properties,
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> provider.fetchQuote("us", "AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");
    }
}
