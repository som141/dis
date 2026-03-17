package discordgateway.application;

import discordgateway.bootstrap.AppProperties;

import java.util.UUID;

public class MusicCommandMessageFactory {

    private final String producer;

    public MusicCommandMessageFactory(AppProperties appProperties) {
        String configured = appProperties.getNodeName();
        if (configured == null || configured.isBlank()) {
            this.producer = "discord-gateway";
            return;
        }
        this.producer = configured.trim();
    }

    public MusicCommandMessage create(MusicCommand command) {
        return new MusicCommandMessage(
                UUID.randomUUID().toString(),
                MusicProtocol.SCHEMA_VERSION,
                System.currentTimeMillis(),
                producer,
                command
        );
    }
}
