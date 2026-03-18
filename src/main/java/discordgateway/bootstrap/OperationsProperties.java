package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops")
public class OperationsProperties {

    private boolean commandDlqReplayEnabled = false;
    private boolean commandDlqReplayExitAfterRun = false;
    private int commandDlqReplayMaxMessages = 100;

    public boolean isCommandDlqReplayEnabled() {
        return commandDlqReplayEnabled;
    }

    public void setCommandDlqReplayEnabled(boolean commandDlqReplayEnabled) {
        this.commandDlqReplayEnabled = commandDlqReplayEnabled;
    }

    public boolean isCommandDlqReplayExitAfterRun() {
        return commandDlqReplayExitAfterRun;
    }

    public void setCommandDlqReplayExitAfterRun(boolean commandDlqReplayExitAfterRun) {
        this.commandDlqReplayExitAfterRun = commandDlqReplayExitAfterRun;
    }

    public int getCommandDlqReplayMaxMessages() {
        return commandDlqReplayMaxMessages;
    }

    public void setCommandDlqReplayMaxMessages(int commandDlqReplayMaxMessages) {
        this.commandDlqReplayMaxMessages = commandDlqReplayMaxMessages;
    }
}
