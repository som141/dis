package discordgateway.stocknode.quote.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stocknode.bootstrap.StockProviderProperties;
import discordgateway.stocknode.quote.model.StockQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

public class AlphaVantageQuoteProvider implements QuoteProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StockProviderProperties stockProviderProperties;
    private final Clock clock;

    public AlphaVantageQuoteProvider(
            ObjectMapper objectMapper,
            StockProviderProperties stockProviderProperties,
            Clock clock
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(stockProviderProperties.getTimeout())
                .build();
        this.objectMapper = objectMapper;
        this.stockProviderProperties = stockProviderProperties;
        this.clock = clock;
    }

    @Override
    public String providerName() {
        return "alphavantage";
    }

    @Override
    public StockQuote fetchQuote(String market, String symbol) {
        if (stockProviderProperties.getAlphaVantageApiKey() == null
                || stockProviderProperties.getAlphaVantageApiKey().isBlank()) {
            throw new IllegalStateException("Alpha Vantage API key is missing");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(symbol))
                .timeout(stockProviderProperties.getTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call Alpha Vantage quote API", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Alpha Vantage quote API", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Alpha Vantage returned HTTP " + response.statusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode globalQuote = root.path("Global Quote");
            if (globalQuote.isMissingNode() || globalQuote.isEmpty()) {
                throw new IllegalStateException("Alpha Vantage returned an empty quote payload");
            }
            String priceText = globalQuote.path("05. price").asText("");
            if (priceText.isBlank()) {
                throw new IllegalStateException("Alpha Vantage quote price is missing");
            }
            return new StockQuote(
                    market,
                    symbol,
                    new BigDecimal(priceText),
                    clock.instant()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse Alpha Vantage quote payload", exception);
        }
    }

    private URI buildUri(String symbol) {
        StringBuilder query = new StringBuilder(stockProviderProperties.getAlphaVantageBaseUrl())
                .append("/query?function=GLOBAL_QUOTE")
                .append("&symbol=").append(encode(symbol))
                .append("&apikey=").append(encode(stockProviderProperties.getAlphaVantageApiKey()));
        String entitlement = stockProviderProperties.getAlphaVantageEntitlement();
        if (entitlement != null && !entitlement.isBlank()) {
            query.append("&entitlement=").append(encode(entitlement.trim().toLowerCase(Locale.ROOT)));
        }
        return URI.create(query.toString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
