package discordgateway.domain;

public class PlayerState {

    private static final String DEFAULT_REPEAT_MODE = "OFF";

    private final long guildId;
    private String nowPlaying;
    private boolean paused;
    private boolean autoPlay;
    private String repeatMode = DEFAULT_REPEAT_MODE;
    private String ownerNode;
    private boolean processingFlag;

    public PlayerState(long guildId) {
        this.guildId = guildId;
    }

    public long getGuildId() {
        return guildId;
    }

    public String getNowPlaying() {
        return nowPlaying;
    }

    public void setNowPlaying(String nowPlaying) {
        this.nowPlaying = nowPlaying;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public String getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(String repeatMode) {
        if (repeatMode == null || repeatMode.isBlank()) {
            this.repeatMode = DEFAULT_REPEAT_MODE;
            return;
        }
        this.repeatMode = repeatMode;
    }

    public String getOwnerNode() {
        return ownerNode;
    }

    public void setOwnerNode(String ownerNode) {
        this.ownerNode = ownerNode;
    }

    public boolean isProcessingFlag() {
        return processingFlag;
    }

    public void setProcessingFlag(boolean processingFlag) {
        this.processingFlag = processingFlag;
    }
}
