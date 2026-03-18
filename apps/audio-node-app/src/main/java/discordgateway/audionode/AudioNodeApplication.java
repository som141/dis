package discordgateway.audionode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "discordgateway")
@EnableScheduling
public class AudioNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AudioNodeApplication.class, args);
    }
}
