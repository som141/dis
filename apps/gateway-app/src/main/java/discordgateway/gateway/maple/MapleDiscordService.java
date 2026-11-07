package discordgateway.gateway.maple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class MapleDiscordService {
    private final MapleDiscordProperties settings;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock clock;
    private final String discordApiBase;

    @Autowired
    public MapleDiscordService(MapleDiscordProperties settings, ObjectMapper json) {
        this(settings,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),Clock.systemUTC(),"https://discord.com/api/v10");
    }

    MapleDiscordService(MapleDiscordProperties settings,ObjectMapper json,HttpClient http,Clock clock,String discordApiBase) {
        this.settings=settings;this.json=json;this.http=http;this.clock=clock;this.discordApiBase=base(discordApiBase);
    }

    public String linkFor(long discordUserId) {
        settings.requireConfigured();
        return base(settings.getPublicBaseUrl())+"/maple/connect?ticket="+encode(signer().issue(Long.toString(discordUserId)));
    }

    public URI authorize(String ticket) {
        settings.requireConfigured();signer().verify(ticket);
        return URI.create("https://discord.com/oauth2/authorize?response_type=code&client_id="+encode(settings.getDiscordClientId())
                +"&scope=identify&redirect_uri="+encode(settings.getRedirectUri())+"&state="+encode(ticket));
    }

    public String complete(String code,String state) {
        settings.requireConfigured();
        String expected=signer().verify(state);
        if(code==null||code.isBlank()||code.length()>1024)throw new IllegalArgumentException("Discord 인증 코드가 유효하지 않습니다.");
        String form="client_id="+encode(settings.getDiscordClientId())+"&client_secret="+encode(settings.getDiscordClientSecret())
                +"&grant_type=authorization_code&code="+encode(code)+"&redirect_uri="+encode(settings.getRedirectUri());
        JsonNode token=send(HttpRequest.newBuilder(URI.create(discordApiBase+"/oauth2/token"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String accessToken=token.path("access_token").asText("");
        if(accessToken.isBlank())throw new IllegalStateException("Discord 인증에 실패했습니다.");
        JsonNode user=send(HttpRequest.newBuilder(URI.create(discordApiBase+"/users/@me"))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization","Bearer "+accessToken).GET().build());
        if(!expected.equals(user.path("id").asText()))throw new IllegalArgumentException("Discord 계정이 연결 요청과 다릅니다.");
        JsonNode portal=send(internalRequest("/internal/discord/session")
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"discordUserId\":\""+expected+"\"}")).build());
        String cookie=portal.path("token").asText("");
        if(!cookie.matches("[a-f0-9]{64}"))throw new IllegalStateException("개인 포털 세션을 만들지 못했습니다.");
        return cookie;
    }

    public String status(long discordUserId) {
        settings.requireConfigured();
        JsonNode data=send(internalRequest("/internal/discord/users/"+discordUserId+"/status").GET().build());
        if(!data.path("linked").asBoolean(false))return "메이플 계정이 아직 연결되지 않았습니다. `/maple 연결`을 실행해 주세요.";
        StringBuilder result=new StringBuilder("메이플 작업 상태");
        String character=data.path("character").asText("");
        if(!character.isBlank())result.append(" · ").append(safe(character));
        JsonNode jobs=data.path("jobs");
        if(!jobs.isArray()||jobs.isEmpty())return result.append("\n등록된 작업이 없습니다. `/maple 연결`에서 검색을 시작하세요.").toString();
        int count=0;
        for(JsonNode job:jobs){
            if(count++>=10){result.append("\n나머지는 웹 포털에서 확인하세요.");break;}
            result.append("\n• ").append(safe(job.path("name").asText("아이템")))
                    .append(" (`").append(safe(job.path("id").asText())).append("`) · ")
                    .append(safe(job.path("phase").asText("상태 확인 필요")));
            if(job.path("watch").asBoolean(false))result.append(" · 감시 중");
        }
        return result.toString();
    }

    public String stop(long discordUserId,String jobId) {
        settings.requireConfigured();
        if(jobId==null||!jobId.matches("[a-zA-Z0-9_-]{1,70}"))throw new IllegalArgumentException("작업 ID를 확인하세요.");
        send(internalRequest("/internal/discord/users/"+discordUserId+"/stop")
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jobId\":\""+jobId+"\"}")).build());
        return "`"+jobId+"` 작업의 조회·감시를 중지했습니다.";
    }

    public String publicBaseUrl(){settings.requireConfigured();return base(settings.getPublicBaseUrl());}

    public record Notice(String id,String discordUserId,String jobId,String name,Long price) {}
    public List<Notice> pendingNotices() {
        settings.requireConfigured();
        JsonNode events=send(internalRequest("/internal/discord/events").GET().build()).path("events");
        List<Notice> notices=new ArrayList<>();
        if(!events.isArray())return notices;
        for(JsonNode event:events){
            String id=event.path("id").asText(""),userId=event.path("discordUserId").asText("");
            if(!id.matches("[a-f0-9]{64}")||!userId.matches("\\d{1,22}"))continue;
            notices.add(new Notice(id,userId,event.path("jobId").asText(""),safe(event.path("name").asText("매물")),
                    event.path("price").isNumber()?event.path("price").asLong():null));
        }
        return notices;
    }

    public void acknowledgeNotice(String eventId,boolean delivered) {
        settings.requireConfigured();
        if(!eventId.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid event ID");
        send(internalRequest("/internal/discord/events/ack").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"eventId\":\""+eventId+"\",\"status\":\""+(delivered?"delivered":"retry")+"\"}"))
                .build());
    }

    public String noticeMessage(Notice notice) {
        String price=notice.price()==null?"가격 확인 필요":String.format("%,d 메소",notice.price());
        return "메이플 경매장 상위 10위 변동 · "+notice.name()+"\n"+price+" · 작업 `"+safe(notice.jobId())+"`\n"+publicBaseUrl()+"/";
    }

    private MapleTicketSigner signer(){return new MapleTicketSigner(settings.getSigningKey(),clock);}
    private HttpRequest.Builder internalRequest(String path){
        return HttpRequest.newBuilder(URI.create(base(settings.getPortalInternalUrl())+path))
                .timeout(Duration.ofSeconds(8)).header("X-Maple-Internal-Key",settings.getInternalKey());
    }
    private JsonNode send(HttpRequest request){
        try{
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429 && request.uri().getPath().equals("/internal/discord/session"))
                throw new MapleCapacityException();
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("메이플 연결을 처리하지 못했습니다. 로그인 또는 작업 상태를 확인하세요.");
            return json.readTree(response.body());
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("메이플 연결이 중단됐습니다.");}
        catch(java.io.IOException error){throw new IllegalStateException("메이플 서비스에 연결하지 못했습니다.");}
    }
    private static String safe(String value){return value.replace('@','＠').replace('\n',' ').replace('\r',' ').replace('`','\'');}
    private static String base(String value){return value.replaceAll("/+$","");}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static final class MapleCapacityException extends IllegalStateException {
        MapleCapacityException(){super("메이플 포털 접속 인원이 가득 찼습니다.");}
    }
}
