package discordgateway.infrastructure.audio;

public record PlaybackSnapshot(boolean hasPlayingTrack, boolean paused, String currentTrackTitle) {
}