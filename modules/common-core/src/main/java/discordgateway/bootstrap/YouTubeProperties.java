package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube")
public class YouTubeProperties {

    private String refreshToken;
    private boolean oauthInit;
    private String poToken;
    private String visitorData;
    private String remoteCipherUrl;
    private String remoteCipherPassword;
    private String remoteCipherUserAgent;

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

    public String getPoToken() {
        return poToken;
    }

    public void setPoToken(String poToken) {
        this.poToken = poToken;
    }

    public String getVisitorData() {
        return visitorData;
    }

    public void setVisitorData(String visitorData) {
        this.visitorData = visitorData;
    }

    public String getRemoteCipherUrl() {
        return remoteCipherUrl;
    }

    public void setRemoteCipherUrl(String remoteCipherUrl) {
        this.remoteCipherUrl = remoteCipherUrl;
    }

    public String getRemoteCipherPassword() {
        return remoteCipherPassword;
    }

    public void setRemoteCipherPassword(String remoteCipherPassword) {
        this.remoteCipherPassword = remoteCipherPassword;
    }

    public String getRemoteCipherUserAgent() {
        return remoteCipherUserAgent;
    }

    public void setRemoteCipherUserAgent(String remoteCipherUserAgent) {
        this.remoteCipherUserAgent = remoteCipherUserAgent;
    }
}
