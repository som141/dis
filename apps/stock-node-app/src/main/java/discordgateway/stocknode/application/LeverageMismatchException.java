package discordgateway.stocknode.application;

public class LeverageMismatchException extends RuntimeException {

    public LeverageMismatchException(String message) {
        super(message);
    }
}
