package discordgateway.application;

import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.PlaybackSnapshot;
import discordgateway.infrastructure.audio.VoiceGateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicApplicationService {

    private final PlaybackGateway playbackGateway;
    private final VoiceGateway voiceGateway;
    private final GuildStateRepository guildStateRepository;

    public MusicApplicationService(
            PlaybackGateway playbackGateway,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository
    ) {
        this.playbackGateway = playbackGateway;
        this.voiceGateway = voiceGateway;
        this.guildStateRepository = guildStateRepository;
    }

    public CompletableFuture<CommandResult> join(Guild guild, long userId) {
        return voiceGateway.findUserAudioChannel(guild, userId)
                .thenApply(audioChannel -> {
                    boolean alreadyConnected = voiceGateway.connect(guild, audioChannel);

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    if (alreadyConnected) {
                        return CommandResult.ephemeral("✅ 이미 해당 음성 채널에 연결되어 있습니다.");
                    }
                    return CommandResult.ephemeral("⏳ 음성 채널 연결 시도 중...");
                });
    }

    public CommandResult leave(Guild guild) {
        if (voiceGateway.connectedChannel(guild) == null) {
            return CommandResult.ephemeral("⚠️ 봇이 음성 채널에 들어와 있지 않습니다.");
        }

        voiceGateway.disconnect(guild);
        guildStateRepository.remove(guild.getIdLong());
        return CommandResult.publicMessage("👋 음성 채널에서 퇴장했습니다.");
    }

    public CompletableFuture<CommandResult> play(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String query,
            boolean autoPlay
    ) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(
                    CommandResult.ephemeral("❗ 사용법: `/play query:<검색어 또는 URL> autoplay:<true|false>`")
            );
        }

        return voiceGateway.findUserAudioChannel(guild, userId)
                .thenApply(audioChannel -> {
                    voiceGateway.connect(guild, audioChannel);

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setAutoPlay(autoPlay);
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    playbackGateway.setAutoPlay(guild, autoPlay);

                    String trackUrl = (query.startsWith("http://") || query.startsWith("https://"))
                            ? query
                            : "ytsearch:" + query;

                    playbackGateway.loadAndPlay(textChannel, trackUrl);
                    return CommandResult.ephemeral("⏳ 재생 요청을 접수했습니다.");
                });
    }

    public CommandResult stop(Guild guild) {
        playbackGateway.stop(guild);
        return CommandResult.ephemeral("⏹️ 재생을 중지하고 큐를 비웠습니다.");
    }

    public CommandResult skip(Guild guild) {
        playbackGateway.skip(guild);
        return CommandResult.ephemeral("⏭️ 다음 곡으로 건너뜁니다.");
    }

    public CommandResult queue(Guild guild) {
        List<String> queue = playbackGateway.queue(guild);
        if (queue.isEmpty()) {
            return CommandResult.ephemeral("📭 현재 대기열이 비어 있습니다.");
        }

        String content = String.join("\n", queue.stream().limit(30).toList());
        return CommandResult.ephemeral("🎶 현재 대기열:\n" + content);
    }

    public CommandResult clear(Guild guild) {
        playbackGateway.clearQueue(guild);
        return CommandResult.publicMessage("🧹 대기열을 비웠습니다.");
    }

    public CommandResult pause(Guild guild) {
        PlaybackSnapshot snapshot = playbackGateway.snapshot(guild);
        if (!snapshot.hasPlayingTrack()) {
            return CommandResult.ephemeral("⏸️ 재생 중인 곡이 없습니다.");
        }
        if (snapshot.paused()) {
            return CommandResult.ephemeral("⚠️ 이미 일시 정지 상태입니다.");
        }

        playbackGateway.pause(guild);
        return CommandResult.ephemeral("⏸️ 곡을 일시 정지했습니다.");
    }

    public CommandResult resume(Guild guild) {
        PlaybackSnapshot snapshot = playbackGateway.snapshot(guild);
        if (!snapshot.hasPlayingTrack()) {
            return CommandResult.ephemeral("▶️ 재생할 곡이 없습니다.");
        }
        if (!snapshot.paused()) {
            return CommandResult.ephemeral("⚠️ 현재 재생 중입니다.");
        }

        playbackGateway.resume(guild);
        return CommandResult.ephemeral("▶️ 재생을 재개했습니다.");
    }

    public CompletableFuture<CommandResult> playSfx(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String fileName
    ) {
        if (fileName == null || fileName.isBlank()) {
            return CompletableFuture.completedFuture(
                    CommandResult.ephemeral("효과음 이름이 비어 있습니다.")
            );
        }

        return voiceGateway.findUserAudioChannel(guild, userId)
                .thenApply(audioChannel -> {
                    voiceGateway.connect(guild, audioChannel);

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    playbackGateway.playLocalFile(textChannel, fileName);
                    return CommandResult.ephemeral("🔊 효과음을 재생합니다: `" + fileName + "`");
                });
    }
}