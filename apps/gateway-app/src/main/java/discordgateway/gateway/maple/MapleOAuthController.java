package discordgateway.gateway.maple;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
public class MapleOAuthController {
    private final MapleDiscordService service;
    public MapleOAuthController(MapleDiscordService service){this.service=service;}

    @GetMapping("/maple/connect")
    public ResponseEntity<Void> connect(@RequestParam String ticket){
        return ResponseEntity.status(HttpStatus.FOUND).location(service.authorize(ticket)).build();
    }

    @GetMapping("/maple/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code,@RequestParam String state){
        String token=service.complete(code,state);
        URI destination=URI.create(service.publicBaseUrl()+"/");
        ResponseCookie cookie=ResponseCookie.from("maple_portal_session",token).httpOnly(true)
                .secure("https".equals(destination.getScheme()))
                .sameSite("Lax").path("/").maxAge(Duration.ofDays(30)).build();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE,cookie.toString()).location(destination).build();
    }

    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public ResponseEntity<String> error(RuntimeException error){
        boolean full=error instanceof MapleDiscordService.MapleCapacityException;
        String title=full?"포털 접속 인원이 가득 찼습니다":"메이플 계정 연결을 완료하지 못했습니다";
        String help=full?"운영자가 접속 인원 설정을 늘려야 합니다. 잠시 후 다시 시도해 주세요.":"Discord에서 /maple 연결을 다시 실행해 새 링크로 접속해 주세요. 계속 실패하면 운영자에게 알려주세요.";
        String page="<!doctype html><html lang=\"ko\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>메이플 포털 연결</title>"
                +"<style>body{min-height:100vh;margin:0;display:grid;place-items:center;background:#f6eedf;color:#382719;font-family:system-ui,sans-serif}main{max-width:32rem;margin:1.5rem;padding:2rem;border:1px solid #ddc49e;border-radius:1rem;background:#fffaf1;box-shadow:0 8px 24px #37230b16}h1{font-size:1.4rem}p{line-height:1.7}</style>"
                +"<main><h1>"+title+"</h1><p>"+help+"</p></main></html>";
        return ResponseEntity.status(full?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML).body(page);
    }
}
