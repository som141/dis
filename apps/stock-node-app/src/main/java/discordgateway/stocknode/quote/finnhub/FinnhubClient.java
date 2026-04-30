package discordgateway.stocknode.quote.finnhub;

import discordgateway.stocknode.bootstrap.FinnhubProperties;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public class FinnhubClient {

    private final WebClient webClient;
    private final FinnhubProperties finnhubProperties;

    public FinnhubClient(WebClient webClient, FinnhubProperties finnhubProperties) {
        this.webClient = webClient;
        this.finnhubProperties = finnhubProperties;
    }

    public FinnhubQuoteResponse fetchQuote(String symbol) {
        FinnhubQuoteResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", finnhubProperties.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(FinnhubQuoteResponse.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Finnhub returned an empty response for " + symbol)))
                .block();

        if (response == null) {
            throw new IllegalStateException("Finnhub returned null for " + symbol);
        }
        if (response.currentPrice() == null || response.currentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Finnhub returned an invalid current price for " + symbol);
        }
        return response;
    }
}
