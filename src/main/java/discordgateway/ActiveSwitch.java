package discordgateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Blue/Green 중 "나 자신이 ACTIVE인지" 확인하는 스위치 유틸.
 *
 * - BOT_COLOR: blue | green (컨테이너별 고정)
 * - ACTIVE_FILE: /data/active (호스트에서 echo로 전환)
 */
public final class ActiveSwitch {

    private static final String BOT_COLOR =
            System.getenv().getOrDefault("BOT_COLOR", "blue").trim().toLowerCase();

    private static final Path ACTIVE_FILE =
            Path.of(System.getenv().getOrDefault("ACTIVE_FILE", "/data/active"));

    private static final Path READY_FILE = Path.of("/tmp/ready");

    private ActiveSwitch() { }

    public static boolean isActive() {
        try {
            String active = Files.readString(ACTIVE_FILE, StandardCharsets.UTF_8)
                    .trim()
                    .toLowerCase();
            return BOT_COLOR.equals(active);
        } catch (IOException e) {
            // active 파일이 없으면 "blue"를 기본 ACTIVE로 간주(초기 부팅 안전장치)
            return "blue".equals(BOT_COLOR);
        }
    }

    public static void markReady() {
        try {
            Files.writeString(READY_FILE, Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static boolean isReady() {
        return Files.exists(READY_FILE);
    }

    public static String color() {
        return BOT_COLOR;
    }
}
