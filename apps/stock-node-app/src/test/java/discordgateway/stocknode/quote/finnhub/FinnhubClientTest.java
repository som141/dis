package discordgateway.stocknode.quote.finnhub;

import com.sun.net.httpserver.HttpServer;
import discordgateway.stocknode.bootstrap.FinnhubProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinnhubClientTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void fetchesQuoteFromFinnhubRestEndpoint() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/quote", exchange -> {
            String body = """
                    {
                      "c": 216.61,
                      "d": 1.23,
                      "dp": 4.00,
                      "h": 220.00,
                      "l": 210.00,
                      "o": 212.00,
                      "pc": 215.38
                    }
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        httpServer.start();

        FinnhubProperties properties = new FinnhubProperties();
        properties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        properties.setApiKey("test-key");
        FinnhubClient client = new FinnhubClient(
                WebClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties
        );

        FinnhubQuoteResponse response = client.fetchQuote("NVDA");

        assertThat(response.currentPrice()).isEqualByComparingTo("216.61");
        assertThat(response.changeRate()).isEqualByComparingTo("4.00");
    }

    @Test
    void rejectsInvalidCurrentPrice() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/quote", exchange -> {
            String body = """
                    {
                      "c": 0,
                      "d": 0,
                      "dp": 0
                    }
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        httpServer.start();

        FinnhubProperties properties = new FinnhubProperties();
        properties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        properties.setApiKey("test-key");
        FinnhubClient client = new FinnhubClient(
                WebClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties
        );

        assertThatThrownBy(() -> client.fetchQuote("NVDA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid current price");
    }
}
