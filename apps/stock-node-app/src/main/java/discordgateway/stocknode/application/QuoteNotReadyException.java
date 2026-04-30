package discordgateway.stocknode.application;

public class QuoteNotReadyException extends RuntimeException {

    public QuoteNotReadyException(String message) {
        super(message);
    }
}
