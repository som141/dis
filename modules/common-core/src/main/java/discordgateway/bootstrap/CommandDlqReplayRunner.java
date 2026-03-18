package discordgateway.bootstrap;

import discordgateway.infrastructure.messaging.rabbit.CommandDlqReplayReport;
import discordgateway.infrastructure.messaging.rabbit.CommandDlqReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class CommandDlqReplayRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandDlqReplayRunner.class);

    private final CommandDlqReplayService commandDlqReplayService;
    private final OperationsProperties operationsProperties;
    private final ConfigurableApplicationContext applicationContext;

    public CommandDlqReplayRunner(
            CommandDlqReplayService commandDlqReplayService,
            OperationsProperties operationsProperties,
            ConfigurableApplicationContext applicationContext
    ) {
        this.commandDlqReplayService = commandDlqReplayService;
        this.operationsProperties = operationsProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        CommandDlqReplayReport report =
                commandDlqReplayService.replay(operationsProperties.getCommandDlqReplayMaxMessages());

        log.atInfo()
                .addKeyValue("replayedCount", report.replayedCount())
                .addKeyValue("failedCount", report.failedCount())
                .addKeyValue("stoppedByLimit", report.stoppedByLimit())
                .log("command-dlq replay finished");

        if (operationsProperties.isCommandDlqReplayExitAfterRun()) {
            int exitCode = report.failedCount() > 0 ? 1 : 0;
            System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
        }
    }
}
