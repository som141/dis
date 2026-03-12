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
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final Path READY_FILE = Path.of("/tmp/ready");

    public static void main(String[] args) throws Exception {
        Files.deleteIfExists(READY_FILE);

        String token = readToken();
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8080"));

        HttpServer healthServer = startHealthServer(healthPort);

        JDA jda = JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(new Listeners())
                .build();

        jda.awaitReady();
        Files.writeString(READY_FILE, "ready", StandardCharsets.UTF_8);

        System.out.println("Bot is ready.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutdown requested...");
            try {
                healthServer.stop(0);
            } catch (Exception ignored) {
            }
            try {
                jda.shutdown();
            } catch (Exception ignored) {
            }
            try {
                Files.deleteIfExists(READY_FILE);
            } catch (Exception ignored) {
            }
        }));
    }

    private static String readToken() {
        String token = System.getenv("token");
        if (token == null || token.isBlank()) {
            token = System.getenv("TOKEN");
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
        boolean ready = Files.exists(READY_FILE);
        String body = "{"
                + "\"status\":\"" + (ready ? "ok" : "starting") + "\","
                + "\"ready\":" + ready
                + "}\n";

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(ready ? 200 : 503, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}