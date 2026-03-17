package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube")
public class YouTubeProperties {

    private String refreshToken;
    private boolean oauthInit;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isOauthInit() {
        return oauthInit;
    }

    public void setOauthInit(boolean oauthInit) {
        this.oauthInit = oauthInit;
    }
}
