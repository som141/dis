package discordgateway.gateway.maple;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Objects;

@ConfigurationProperties(prefix="maple.discord")
public class MapleDiscordProperties {
    private boolean enabled;
    private String publicBaseUrl = "";
    private String discordClientId = "";
    private String discordClientSecret = "";
    private String redirectUri = "";
    private String signingKey = "";
    private String portalInternalUrl = "";
    private String internalKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getDiscordClientId() { return discordClientId; }
    public void setDiscordClientId(String discordClientId) { this.discordClientId = discordClientId; }
    public String getDiscordClientSecret() { return discordClientSecret; }
    public void setDiscordClientSecret(String discordClientSecret) { this.discordClientSecret = discordClientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getSigningKey() { return signingKey; }
    public void setSigningKey(String signingKey) { this.signingKey = signingKey; }
    public String getPortalInternalUrl() { return portalInternalUrl; }
    public void setPortalInternalUrl(String portalInternalUrl) { this.portalInternalUrl = portalInternalUrl; }
    public String getInternalKey() { return internalKey; }
    public void setInternalKey(String internalKey) { this.internalKey = internalKey; }

    public void requireConfigured() {
        if (!enabled || signingKey.length() < 32 || internalKey.length() < 32 || discordClientId.isBlank()
                || discordClientSecret.isBlank() || redirectUri.isBlank() || publicBaseUrl.isBlank()
                || portalInternalUrl.isBlank()) {
            throw new IllegalStateException("메이플 Discord 연결 설정이 아직 완료되지 않았습니다.");
        }
        URI publicUri=URI.create(publicBaseUrl),callbackUri=URI.create(redirectUri);
        boolean local="http".equals(publicUri.getScheme()) && ("localhost".equals(publicUri.getHost()) || "127.0.0.1".equals(publicUri.getHost()));
        if ((!"https".equals(publicUri.getScheme()) && !local) || !Objects.equals(publicUri.getScheme(),callbackUri.getScheme())
                || !Objects.equals(publicUri.getHost(),callbackUri.getHost()) || publicUri.getPort()!=callbackUri.getPort()
                || !"/maple/oauth/callback".equals(callbackUri.getPath())
                || (publicUri.getPath()!=null&&!publicUri.getPath().isBlank()&&!"/".equals(publicUri.getPath()))) {
            throw new IllegalStateException("메이플 웹 주소와 Discord 콜백 주소를 확인하세요.");
        }
    }
}
