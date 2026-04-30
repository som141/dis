package discordgateway.stocknode.application;

public class InsufficientCashException extends RuntimeException {

    public InsufficientCashException(String message) {
        super(message);
    }
}
