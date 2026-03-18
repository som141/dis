package discordgateway.application;

import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.PlaybackSnapshot;
import discordgateway.infrastructure.audio.VoiceGateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicWorkerService {

    private static final Logger log = LoggerFactory.getLogger(MusicWorkerService.class);

    private final DiscordReferenceResolver discordReferenceResolver;
    private final PlaybackGateway playbackGateway;
    private final VoiceGateway voiceGateway;
    private final GuildStateRepository guildStateRepository;
    private final PlayerStateRepository playerStateRepository;
    private final MusicEventPublisher musicEventPublisher;
    private final MusicEventFactory musicEventFactory;

    public MusicWorkerService(
            DiscordReferenceResolver discordReferenceResolver,
            PlaybackGateway playbackGateway,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        this.discordReferenceResolver = discordReferenceResolver;
        this.playbackGateway = playbackGateway;
        this.voiceGateway = voiceGateway;
        this.guildStateRepository = guildStateRepository;
        this.playerStateRepository = playerStateRepository;
        this.musicEventPublisher = musicEventPublisher;
        this.musicEventFactory = musicEventFactory;
    }

    public CompletableFuture<CommandResult> handle(MusicCommand command) {
        return dispatch(command);
    }

    public CompletableFuture<CommandResult> handle(MusicCommandMessage message) {
        return MusicCommandTraceContext.callWith(
                MusicCommandTrace.from(message),
                () -> dispatch(message.command())
        );
    }

    private CompletableFuture<CommandResult> dispatch(MusicCommand command) {
        if (command instanceof MusicCommand.Join join) {
            return join(join);
        }
        if (command instanceof MusicCommand.Leave leave) {
            return CompletableFuture.completedFuture(leave(leave));
        }
        if (command instanceof MusicCommand.Play play) {
            return play(play);
        }
        if (command instanceof MusicCommand.Stop stop) {
            return CompletableFuture.completedFuture(stop(stop));
        }
        if (command instanceof MusicCommand.Skip skip) {
            return CompletableFuture.completedFuture(skip(skip));
        }
        if (command instanceof MusicCommand.Queue queue) {
            return CompletableFuture.completedFuture(queue(queue));
        }
        if (command instanceof MusicCommand.Clear clear) {
            return CompletableFuture.completedFuture(clear(clear));
        }
        if (command instanceof MusicCommand.Pause pause) {
            return CompletableFuture.completedFuture(pause(pause));
        }
        if (command instanceof MusicCommand.Resume resume) {
            return CompletableFuture.completedFuture(resume(resume));
        }
        if (command instanceof MusicCommand.PlaySfx playSfx) {
            return playSfx(playSfx);
        }

        return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unsupported music command: " + command.getClass().getName())
        );
    }

    private CompletableFuture<CommandResult> join(MusicCommand.Join command) {
        Guild guild = requireGuild(command.guildId());
        TextChannel textChannel = requireTextChannel(command.guildId(), command.textChannelId());
        MusicCommandTrace trace = MusicCommandTraceContext.current();

        log.atInfo()
                .addKeyValue("guildId", command.guildId())
                .addKeyValue("userId", command.userId())
                .addKeyValue("textChannelId", command.textChannelId())
                .log("join requested");

        return voiceGateway.findUserAudioChannel(guild, command.userId())
                .thenApply(audioChannel -> MusicCommandTraceContext.callWith(trace, () -> {
                    boolean alreadyConnected = voiceGateway.connect(guild, audioChannel);

                    log.atInfo()
                            .addKeyValue("guildId", command.guildId())
                            .addKeyValue("userId", command.userId())
                            .addKeyValue("voiceChannelId", audioChannel.getIdLong())
                            .addKeyValue("alreadyConnected", alreadyConnected)
                            .log("join connect resolved");

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    if (!alreadyConnected) {
                        musicEventPublisher.publish(
                                musicEventFactory.voiceConnected(
                                        guild.getIdLong(),
                                        audioChannel.getIdLong(),
                                        command.userId(),
                                        "join-command"
                                )
                        );
                        sendTextChannelMessage(textChannel, "🔊 음성 채널 연결: " + audioChannel.getName());
                    }

                    if (alreadyConnected) {
                        return CommandResult.ephemeral("이미 해당 음성 채널에 연결되어 있습니다.");
                    }
                    return CommandResult.ephemeral("🔊 음성 채널 연결 요청을 보냈습니다.");
                }));
    }

    private CommandResult leave(MusicCommand.Leave command) {
        Guild guild = requireGuild(command.guildId());
        var connectedChannel = voiceGateway.connectedChannel(guild);

        if (connectedChannel == null) {
            return CommandResult.ephemeral("현재 봇이 음성 채널에 들어가 있지 않습니다.");
        }

        voiceGateway.disconnect(guild);
        guildStateRepository.remove(guild.getIdLong());
        playerStateRepository.remove(guild.getIdLong());
        musicEventPublisher.publish(
                musicEventFactory.voiceDisconnected(
                        guild.getIdLong(),
                        connectedChannel.getIdLong(),
                        null,
                        "leave-command"
                )
        );
        return CommandResult.publicMessage("음성 채널에서 나갔습니다.");
    }

    private CompletableFuture<CommandResult> play(MusicCommand.Play command) {
        Guild guild = requireGuild(command.guildId());
        TextChannel textChannel = requireTextChannel(command.guildId(), command.textChannelId());
        MusicCommandTrace trace = MusicCommandTraceContext.current();

        log.atInfo()
                .addKeyValue("guildId", command.guildId())
                .addKeyValue("userId", command.userId())
                .addKeyValue("textChannelId", command.textChannelId())
                .addKeyValue("autoPlay", command.autoPlay())
                .addKeyValue("query", command.query())
                .log("play requested");

        if (command.query() == null || command.query().isBlank()) {
            return CompletableFuture.completedFuture(
                    CommandResult.ephemeral("사용법: `/play query:<검색어 또는 URL> autoplay:<true|false>`")
            );
        }

        return voiceGateway.findUserAudioChannel(guild, command.userId())
                .thenApply(audioChannel -> MusicCommandTraceContext.callWith(trace, () -> {
                    boolean alreadyConnected = voiceGateway.connect(guild, audioChannel);

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    if (!alreadyConnected) {
                        musicEventPublisher.publish(
                                musicEventFactory.voiceConnected(
                                        guild.getIdLong(),
                                        audioChannel.getIdLong(),
                                        command.userId(),
                                        "play-command"
                                )
                        );
                    }

                    playbackGateway.setAutoPlay(guild, command.autoPlay());

                    String trackUrl = (command.query().startsWith("http://") || command.query().startsWith("https://"))
                            ? command.query()
                            : "ytsearch:" + command.query();

                    log.atInfo()
                            .addKeyValue("guildId", command.guildId())
                            .addKeyValue("userId", command.userId())
                            .addKeyValue("trackUrl", trackUrl)
                            .log("play load scheduled");

                    playbackGateway.loadAndPlay(textChannel, trackUrl);
                    return CommandResult.ephemeral("재생 요청을 접수했습니다. 결과는 채널 메시지로 안내합니다.");
                }));
    }

    private CommandResult stop(MusicCommand.Stop command) {
        Guild guild = requireGuild(command.guildId());
        playbackGateway.stop(guild);
        return CommandResult.ephemeral("재생을 중지하고 대기열을 비웠습니다.");
    }

    private CommandResult skip(MusicCommand.Skip command) {
        Guild guild = requireGuild(command.guildId());
        playbackGateway.skip(guild);
        return CommandResult.ephemeral("다음 곡으로 건너뜁니다.");
    }

    private CommandResult queue(MusicCommand.Queue command) {
        Guild guild = requireGuild(command.guildId());
        List<String> queue = playbackGateway.queue(guild);
        if (queue.isEmpty()) {
            return CommandResult.ephemeral("현재 대기열이 비어 있습니다.");
        }

        String content = String.join("\n", queue.stream().limit(30).toList());
        return CommandResult.ephemeral("현재 대기열:\n" + content);
    }

    private CommandResult clear(MusicCommand.Clear command) {
        Guild guild = requireGuild(command.guildId());
        playbackGateway.clearQueue(guild);
        return CommandResult.publicMessage("대기열을 비웠습니다.");
    }

    private CommandResult pause(MusicCommand.Pause command) {
        Guild guild = requireGuild(command.guildId());
        PlaybackSnapshot snapshot = playbackGateway.snapshot(guild);
        if (!snapshot.hasPlayingTrack()) {
            return CommandResult.ephemeral("현재 재생 중인 곡이 없습니다.");
        }
        if (snapshot.paused()) {
            return CommandResult.ephemeral("이미 일시 정지 상태입니다.");
        }

        playbackGateway.pause(guild);
        return CommandResult.ephemeral("곡을 일시 정지했습니다.");
    }

    private CommandResult resume(MusicCommand.Resume command) {
        Guild guild = requireGuild(command.guildId());
        PlaybackSnapshot snapshot = playbackGateway.snapshot(guild);
        if (!snapshot.hasPlayingTrack()) {
            return CommandResult.ephemeral("현재 재생 중인 곡이 없습니다.");
        }
        if (!snapshot.paused()) {
            return CommandResult.ephemeral("현재 재생 중입니다.");
        }

        playbackGateway.resume(guild);
        return CommandResult.ephemeral("재생을 재개했습니다.");
    }

    private CompletableFuture<CommandResult> playSfx(MusicCommand.PlaySfx command) {
        Guild guild = requireGuild(command.guildId());
        TextChannel textChannel = requireTextChannel(command.guildId(), command.textChannelId());
        MusicCommandTrace trace = MusicCommandTraceContext.current();

        if (command.fileName() == null || command.fileName().isBlank()) {
            return CompletableFuture.completedFuture(
                    CommandResult.ephemeral("효과음 파일명이 비어 있습니다.")
            );
        }

        return voiceGateway.findUserAudioChannel(guild, command.userId())
                .thenApply(audioChannel -> MusicCommandTraceContext.callWith(trace, () -> {
                    boolean alreadyConnected = voiceGateway.connect(guild, audioChannel);

                    GuildPlayerState state = guildStateRepository.getOrCreate(guild.getIdLong());
                    state.setConnectedVoiceChannelId(audioChannel.getIdLong());
                    guildStateRepository.save(state);

                    if (!alreadyConnected) {
                        musicEventPublisher.publish(
                                musicEventFactory.voiceConnected(
                                        guild.getIdLong(),
                                        audioChannel.getIdLong(),
                                        command.userId(),
                                        "play-sfx-command"
                                )
                        );
                    }

                    playbackGateway.playLocalFile(textChannel, command.fileName());
                    return CommandResult.ephemeral("효과음을 재생합니다. `" + command.fileName() + "`");
                }));
    }

    private Guild requireGuild(long guildId) {
        Guild guild = discordReferenceResolver.resolveGuild(guildId);
        if (guild == null) {
            throw new IllegalStateException("길드 정보를 찾을 수 없습니다.");
        }
        return guild;
    }

    private TextChannel requireTextChannel(long guildId, long textChannelId) {
        TextChannel textChannel = discordReferenceResolver.resolveTextChannel(guildId, textChannelId);
        if (textChannel == null) {
            throw new IllegalStateException("텍스트 채널 정보를 찾을 수 없습니다.");
        }
        return textChannel;
    }

    private void sendTextChannelMessage(TextChannel textChannel, String message) {
        textChannel.sendMessage(message).queue(
                null,
                failure -> log.atWarn()
                        .addKeyValue("guildId", textChannel.getGuild().getIdLong())
                        .addKeyValue("channelId", textChannel.getIdLong())
                        .addKeyValue("message", message)
                        .setCause(failure)
                        .log("Failed to send follow-up text channel message")
        );
    }
}
