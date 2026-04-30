package discordgateway.stocknode.application;

public class StaleQuoteException extends RuntimeException {

    public StaleQuoteException(String message) {
        super(message);
    }
}
