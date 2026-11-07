package discordgateway.gateway.maple;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MapleOAuthControllerTest {
    @Test
    void capacityFailureShowsReadablePageInsteadOfBareWhiteText() {
        MapleDiscordService service=mock(MapleDiscordService.class);
        var response=new MapleOAuthController(service).error(new MapleDiscordService.MapleCapacityException());
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/html");
        assertThat(response.getBody()).contains("포털 접속 인원이 가득 찼습니다", "운영자가 접속 인원 설정을 늘려야 합니다");
    }

    @Test
    void invalidOrExpiredTicketShowsRestartInstructions() {
        MapleDiscordService service=mock(MapleDiscordService.class);
        var response=new MapleOAuthController(service).error(new IllegalArgumentException("private-state"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("/maple 연결").doesNotContain("private-state");
    }
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
