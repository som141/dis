package discordgateway.stocknode.application;

public class InvalidTradeArgumentException extends RuntimeException {

    public InvalidTradeArgumentException(String message) {
        super(message);
    }
}
