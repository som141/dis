package discordgateway.stocknode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "discordgateway")
@EnableScheduling
public class StockNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockNodeApplication.class, args);
    }
}
