package discordgateway.gateway.maple;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<String> error(RuntimeException ignored){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("메이플 계정 연결에 실패했습니다. Discord에서 `/maple 연결`을 다시 실행해 주세요.");
    }
}
