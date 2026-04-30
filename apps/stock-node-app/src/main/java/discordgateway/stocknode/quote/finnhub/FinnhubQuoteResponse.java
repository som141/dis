package discordgateway.stocknode.quote.finnhub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinnhubQuoteResponse(
        @JsonProperty("c") BigDecimal currentPrice,
        @JsonProperty("d") BigDecimal changeAmount,
        @JsonProperty("dp") BigDecimal changeRate,
        @JsonProperty("h") BigDecimal high,
        @JsonProperty("l") BigDecimal low,
        @JsonProperty("o") BigDecimal open,
        @JsonProperty("pc") BigDecimal previousClose
) {
}
