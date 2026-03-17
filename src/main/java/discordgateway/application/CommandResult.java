package discordgateway.application;

public record CommandResult(String message, boolean ephemeral) {

    public static CommandResult publicMessage(String message) {
        return new CommandResult(message, false);
    }

    public static CommandResult ephemeral(String message) {
        return new CommandResult(message, true);
    }
}