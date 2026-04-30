package discordgateway.stocknode.application;

public class SymbolNotTradableException extends RuntimeException {

    public SymbolNotTradableException(String message) {
        super(message);
    }
}
