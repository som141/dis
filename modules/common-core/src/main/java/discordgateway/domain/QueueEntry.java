package discordgateway.domain;

public record QueueEntry(
        String identifier,
        String title,
        String author,
        long requestedAtMillis
) {
    public String displayLine() {
        return title + " - " + author;
    }
}