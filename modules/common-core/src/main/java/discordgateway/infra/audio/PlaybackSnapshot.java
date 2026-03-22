package discordgateway.infra.audio;

public record PlaybackSnapshot(boolean hasPlayingTrack, boolean paused, String currentTrackTitle) {
}