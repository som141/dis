package discordgateway.gateway.maple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapleDiscordServiceTest {
    private static final Clock NOW=Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC);
    private static MapleDiscordProperties settings(String internalUrl){
        MapleDiscordProperties p=new MapleDiscordProperties();p.setEnabled(true);
        p.setPublicBaseUrl("https://maple.example.test");p.setRedirectUri("https://maple.example.test/maple/oauth/callback");
        p.setDiscordClientId("123456789012345678");p.setDiscordClientSecret("fixture-secret");
        p.setSigningKey("fixture-signing-key-12345678901234567890");
        p.setInternalKey("fixture-internal-key-12345678901234567890");p.setPortalInternalUrl(internalUrl);
        return p;
    }

    @Test
    void springCanCreateServiceWhenMapleIntegrationIsDisabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MapleDiscordProperties.class);
            context.registerBean(ObjectMapper.class);
            context.register(MapleDiscordService.class);
            context.refresh();
            assertThat(context.getBean(MapleDiscordService.class)).isNotNull();
        }
    }

    @Test
    void oauthIdentityMustMatchTicketBeforeAWebSessionIsIssued() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger sessions=new AtomicInteger();
        AtomicInteger acknowledgements=new AtomicInteger();
        server.createContext("/oauth2/token",ex->{byte[] out="{\"access_token\":\"fixture-access\"}".getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/users/@me",ex->{byte[] out="{\"id\":\"222\"}".getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/internal/discord/session",ex->{sessions.incrementAndGet();byte[] out=("{\"token\":\""+"a".repeat(64)+"\"}").getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/internal/discord/users/222/status",ex->{byte[] out="{\"linked\":true,\"jobs\":[{\"id\":\"glove-live\",\"name\":\"장갑\",\"phase\":\"watching\",\"watch\":true}]}".getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/internal/discord/users/222/stop",ex->{byte[] out="{\"ok\":true}".getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/internal/discord/events",ex->{byte[] out=("{\"events\":[{\"id\":\""+"b".repeat(64)+"\",\"discordUserId\":\"222\",\"jobId\":\"glove-live\",\"name\":\"@everyone 장갑\",\"price\":123456789}]}" ).getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.createContext("/internal/discord/events/ack",ex->{acknowledgements.incrementAndGet();byte[] out="{\"ok\":true}".getBytes();ex.sendResponseHeaders(200,out.length);try(var body=ex.getResponseBody()){body.write(out);}});
        server.start();
        try{
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            MapleDiscordService service=new MapleDiscordService(settings(url),new ObjectMapper(),HttpClient.newHttpClient(),NOW,url);
            String link=service.linkFor(111L),ticket=link.substring(link.indexOf("ticket=")+7);
            assertThat(service.authorize(ticket).toString()).contains("scope=identify","state=");
            assertThatThrownBy(()->service.complete("code",ticket)).isInstanceOf(IllegalArgumentException.class);
            assertThat(sessions).hasValue(0);
            String matching=service.linkFor(222L).split("ticket=")[1];
            assertThat(service.complete("code",matching)).isEqualTo("a".repeat(64));
            assertThat(sessions).hasValue(1);
            assertThat(service.status(222L)).contains("glove-live","감시 중");
            assertThat(service.stop(222L,"glove-live")).contains("중지");
            var notices=service.pendingNotices();assertThat(notices).hasSize(1);
            assertThat(service.noticeMessage(notices.get(0))).contains("＠everyone").doesNotContain("@everyone");
            service.acknowledgeNotice(notices.get(0).id(),true);
            assertThat(acknowledgements).hasValue(1);
        }finally{server.stop(0);}
    }
}
