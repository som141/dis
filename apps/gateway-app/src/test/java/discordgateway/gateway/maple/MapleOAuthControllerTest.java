package discordgateway.gateway.maple;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MapleOAuthControllerTest {
    @Test
    void callbackSetsPrivateSecureCookieAndRedirectsToPortal() {
        MapleDiscordService service=mock(MapleDiscordService.class);
        when(service.complete("code","state")).thenReturn("a".repeat(64));
        when(service.publicBaseUrl()).thenReturn("https://maple.example.test");
        var response=new MapleOAuthController(service).callback("code","state");
        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("https://maple.example.test/");
        assertThat(response.getHeaders().getFirst("Set-Cookie"))
                .contains("maple_portal_session="+"a".repeat(64),"HttpOnly","Secure","SameSite=Lax","Path=/");
    }
}
