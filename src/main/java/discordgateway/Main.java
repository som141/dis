package discordgateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws Exception {
        String token = readToken();
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8080"));

        // 1) Discord(JDA) 부팅
        JDA jda = JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                // 기존 Listeners를 ACTIVE일 때만 동작하도록 래핑
                .addEventListeners(new ActiveOnlyListener(new Listeners()))
                .build();

        // 2) health server는 먼저 띄워두되, ready 여부는 awaitReady 이후 true로
        HttpServer healthServer = startHealthServer(healthPort);

        // 3) Discord Ready 대기
        jda.awaitReady();
        ActiveSwitch.markReady();
        System.out.println("bot start let's go! color=" + ActiveSwitch.color());

        // 4) SIGTERM 등 종료 신호 처리(그레이스풀)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutdown requested...");
            try {
                healthServer.stop(0);
            } catch (Exception ignored) {
            }
            try {
                // graceful shutdown
                jda.shutdown();
            } catch (Exception ignored) {
            }
        }));
    }

    private static String readToken() {
        String token = System.getenv("token"); // 기존 코드 호환
        if (token == null || token.isBlank()) {
            token = System.getenv("TOKEN");     // GitHub Secret(TOKEN) 호환
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Discord bot token is missing. Set env var 'token' or 'TOKEN'.");
        }
        return token.trim();
    }

    private static HttpServer startHealthServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/health", Main::handleHealth);
        server.start();
        return server;
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        String body = "{"
                + "\"status\":\"ok\","
                + "\"ready\":" + ActiveSwitch.isReady() + ","
                + "\"active\":" + ActiveSwitch.isActive() + ","
                + "\"color\":\"" + ActiveSwitch.color() + "\""
                + "}\n";

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
