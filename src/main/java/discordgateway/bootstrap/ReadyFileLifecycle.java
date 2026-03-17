package discordgateway.bootstrap;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ReadyFileLifecycle {

    private static final Path READY_FILE = Path.of("/tmp/ready");

    public ReadyFileLifecycle() throws IOException {
        Files.deleteIfExists(READY_FILE);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws IOException {
        Files.writeString(READY_FILE, "ready", StandardCharsets.UTF_8);
    }

    @EventListener(ContextClosedEvent.class)
    public void onClosed() throws IOException {
        Files.deleteIfExists(READY_FILE);
    }
}
