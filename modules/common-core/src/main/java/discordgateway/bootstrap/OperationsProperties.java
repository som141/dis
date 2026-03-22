package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ops")
public class OperationsProperties {

    private boolean commandDlqReplayEnabled = false;
    private boolean commandDlqReplayExitAfterRun = false;
    private int commandDlqReplayMaxMessages = 100;
    private boolean voiceIdleDisconnectEnabled = true;
    private Duration voiceIdleTimeout = Duration.ofMinutes(5);

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

    public boolean isVoiceIdleDisconnectEnabled() {
        return voiceIdleDisconnectEnabled;
    }

    public void setVoiceIdleDisconnectEnabled(boolean voiceIdleDisconnectEnabled) {
        this.voiceIdleDisconnectEnabled = voiceIdleDisconnectEnabled;
    }

    public Duration getVoiceIdleTimeout() {
        return voiceIdleTimeout;
    }

    public void setVoiceIdleTimeout(Duration voiceIdleTimeout) {
        this.voiceIdleTimeout = voiceIdleTimeout;
    }
}
