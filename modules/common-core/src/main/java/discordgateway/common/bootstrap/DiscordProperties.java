package discordgateway.common.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public class DiscordProperties {

    private String token;
    private String devGuildId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDevGuildId() {
        return devGuildId;
    }

    public void setDevGuildId(String devGuildId) {
        this.devGuildId = devGuildId;
    }
}
