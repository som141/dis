package discordgateway.stocknode.application;

public class InvalidLeverageException extends RuntimeException {

    public InvalidLeverageException(String message) {
        super(message);
    }
}
