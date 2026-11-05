package discordgateway.gateway.maple;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

public final class MapleTicketSigner {
    private final byte[] key;
    private final Clock clock;

    public MapleTicketSigner(String key, Clock clock) {
        if (key == null || key.length() < 32) throw new IllegalArgumentException("Ticket key must be at least 32 characters");
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(String discordUserId) {
        if (!discordUserId.matches("\\d{1,22}")) throw new IllegalArgumentException("Invalid Discord user ID");
        String nonce = HexFormat.of().formatHex(java.security.SecureRandom.getSeed(16));
        String payload = "v1:" + discordUserId + ":" + (clock.instant().getEpochSecond() + Duration.ofMinutes(5).toSeconds()) + ":" + nonce;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }

    public String verify(String ticket) {
        if (ticket == null || ticket.length() > 300) throw new IllegalArgumentException("연결 링크가 유효하지 않습니다.");
        String[] parts = ticket.split("\\.", -1);
        if (parts.length != 2) throw new IllegalArgumentException("연결 링크가 유효하지 않습니다.");
        try {
            if (!MessageDigest.isEqual(sign(parts[0]),Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException();
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            if (fields.length != 4 || !fields[0].equals("v1") || !fields[1].matches("\\d{1,22}") || !fields[3].matches("[a-f0-9]{32}")) throw new IllegalArgumentException();
            long expires = Long.parseLong(fields[2]);
            if (expires < clock.instant().getEpochSecond() || expires > clock.instant().getEpochSecond() + 300) throw new IllegalArgumentException();
            return fields[1];
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("연결 링크가 만료됐거나 유효하지 않습니다.");
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException("Ticket signing failed",error); }
    }
}
