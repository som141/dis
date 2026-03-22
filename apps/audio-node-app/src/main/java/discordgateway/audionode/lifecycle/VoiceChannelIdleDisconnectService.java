package discordgateway.audionode.lifecycle;

import discordgateway.application.DiscordReferenceResolver;
import discordgateway.application.VoiceSessionLifecycleService;
import discordgateway.bootstrap.OperationsProperties;
import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;
import discordgateway.infrastructure.audio.VoiceGateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VoiceChannelIdleDisconnectService {

    private static final Logger log = LoggerFactory.getLogger(VoiceChannelIdleDisconnectService.class);

    private final VoiceGateway voiceGateway;
    private final VoiceSessionLifecycleService voiceSessionLifecycleService;
    private final DiscordReferenceResolver discordReferenceResolver;
    private final GuildStateRepository guildStateRepository;
    private final OperationsProperties operationsProperties;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, PendingDisconnect> pendingDisconnects;

    public VoiceChannelIdleDisconnectService(
            VoiceGateway voiceGateway,
            VoiceSessionLifecycleService voiceSessionLifecycleService,
            DiscordReferenceResolver discordReferenceResolver,
            GuildStateRepository guildStateRepository,
            OperationsProperties operationsProperties,
            ScheduledExecutorService scheduler
    ) {
        this.voiceGateway = voiceGateway;
        this.voiceSessionLifecycleService = voiceSessionLifecycleService;
        this.discordReferenceResolver = discordReferenceResolver;
        this.guildStateRepository = guildStateRepository;
        this.operationsProperties = operationsProperties;
        this.scheduler = scheduler;
        this.pendingDisconnects = new ConcurrentHashMap<>();
    }

    public void evaluate(Guild guild, String trigger) {
        long guildId = guild.getIdLong();

        if (!operationsProperties.isVoiceIdleDisconnectEnabled()) {
            cancel(guildId, "feature-disabled", null, -1);
            return;
        }

        AudioChannel connectedChannel = voiceGateway.connectedChannel(guild);
        if (connectedChannel == null) {
            cancel(guildId, "bot-not-connected", null, -1);
            return;
        }

        int humanUserCount = countHumanUsers(connectedChannel);
        if (humanUserCount == 0) {
            schedule(guild, connectedChannel, humanUserCount, trigger);
            return;
        }

        cancel(guildId, "human-present", connectedChannel.getIdLong(), humanUserCount);
    }

    private void schedule(Guild guild, AudioChannel connectedChannel, int humanUserCount, String trigger) {
        long guildId = guild.getIdLong();
        long voiceChannelId = connectedChannel.getIdLong();
        long idleTimeoutMs = idleTimeout().toMillis();

        pendingDisconnects.compute(guildId, (ignored, existing) -> {
            if (existing != null && !existing.future().isDone() && existing.voiceChannelId() == voiceChannelId) {
                log.atInfo()
                        .addKeyValue("guildId", guildId)
                        .addKeyValue("voiceChannelId", voiceChannelId)
                        .addKeyValue("idleTimeoutMs", idleTimeoutMs)
                        .addKeyValue("humanUserCount", humanUserCount)
                        .addKeyValue("trigger", trigger)
                        .addKeyValue("action", "schedule-noop")
                        .log("voice-idle transition");
                return existing;
            }

            if (existing != null) {
                existing.future().cancel(false);
            }

            String token = UUID.randomUUID().toString();
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> runDisconnect(guild, voiceChannelId, token),
                    idleTimeoutMs,
                    TimeUnit.MILLISECONDS
            );

            log.atInfo()
                    .addKeyValue("guildId", guildId)
                    .addKeyValue("voiceChannelId", voiceChannelId)
                    .addKeyValue("idleTimeoutMs", idleTimeoutMs)
                    .addKeyValue("humanUserCount", humanUserCount)
                    .addKeyValue("trigger", trigger)
                    .addKeyValue("action", "schedule")
                    .log("voice-idle transition");

            return new PendingDisconnect(voiceChannelId, token, future);
        });
    }

    private void cancel(long guildId, String reason, Long voiceChannelId, int humanUserCount) {
        PendingDisconnect removed = pendingDisconnects.remove(guildId);
        if (removed == null) {
            return;
        }

        removed.future().cancel(false);
        log.atInfo()
                .addKeyValue("guildId", guildId)
                .addKeyValue("voiceChannelId", voiceChannelId != null ? voiceChannelId : removed.voiceChannelId())
                .addKeyValue("idleTimeoutMs", idleTimeout().toMillis())
                .addKeyValue("humanUserCount", humanUserCount)
                .addKeyValue("reason", reason)
                .addKeyValue("action", "cancel")
                .log("voice-idle transition");
    }

    private void runDisconnect(Guild guild, long scheduledVoiceChannelId, String token) {
        long guildId = guild.getIdLong();
        PendingDisconnect current = pendingDisconnects.get(guildId);

        if (current == null || !Objects.equals(current.token(), token)) {
            return;
        }

        AudioChannel connectedChannel = voiceGateway.connectedChannel(guild);
        if (connectedChannel == null) {
            pendingDisconnects.remove(guildId, current);
            log.atInfo()
                    .addKeyValue("guildId", guildId)
                    .addKeyValue("voiceChannelId", scheduledVoiceChannelId)
                    .addKeyValue("idleTimeoutMs", idleTimeout().toMillis())
                    .addKeyValue("humanUserCount", -1)
                    .addKeyValue("action", "disconnect-skip-not-connected")
                    .log("voice-idle transition");
            return;
        }

        if (connectedChannel.getIdLong() != scheduledVoiceChannelId) {
            pendingDisconnects.remove(guildId, current);
            log.atInfo()
                    .addKeyValue("guildId", guildId)
                    .addKeyValue("voiceChannelId", connectedChannel.getIdLong())
                    .addKeyValue("idleTimeoutMs", idleTimeout().toMillis())
                    .addKeyValue("humanUserCount", countHumanUsers(connectedChannel))
                    .addKeyValue("action", "disconnect-skip-channel-changed")
                    .log("voice-idle transition");
            return;
        }

        int humanUserCount = countHumanUsers(connectedChannel);
        if (humanUserCount > 0) {
            pendingDisconnects.remove(guildId, current);
            log.atInfo()
                    .addKeyValue("guildId", guildId)
                    .addKeyValue("voiceChannelId", connectedChannel.getIdLong())
                    .addKeyValue("idleTimeoutMs", idleTimeout().toMillis())
                    .addKeyValue("humanUserCount", humanUserCount)
                    .addKeyValue("action", "disconnect-skip-human-present")
                    .log("voice-idle transition");
            return;
        }

        pendingDisconnects.remove(guildId, current);
        log.atInfo()
                .addKeyValue("guildId", guildId)
                .addKeyValue("voiceChannelId", connectedChannel.getIdLong())
                .addKeyValue("idleTimeoutMs", idleTimeout().toMillis())
                .addKeyValue("humanUserCount", humanUserCount)
                .addKeyValue("action", "disconnect")
                .log("voice-idle transition");

        sendIdleDisconnectMessage(guildId);
        voiceSessionLifecycleService.terminate(guild, null, "voice-idle-timeout");
    }

    private int countHumanUsers(AudioChannel channel) {
        return (int) channel.getMembers()
                .stream()
                .filter(member -> isHuman(member))
                .count();
    }

    private boolean isHuman(Member member) {
        return member != null && !member.getUser().isBot();
    }

    private Duration idleTimeout() {
        Duration configured = operationsProperties.getVoiceIdleTimeout();
        if (configured == null || configured.isNegative()) {
            return Duration.ofMinutes(5);
        }
        return configured;
    }

    private void sendIdleDisconnectMessage(long guildId) {
        GuildPlayerState guildState = guildStateRepository.getOrCreate(guildId);
        Long textChannelId = guildState.getLastTextChannelId();
        if (textChannelId == null) {
            return;
        }

        TextChannel textChannel = discordReferenceResolver.resolveTextChannel(guildId, textChannelId);
        if (textChannel == null) {
            log.atWarn()
                    .addKeyValue("guildId", guildId)
                    .addKeyValue("textChannelId", textChannelId)
                    .log("voice-idle notification skipped: text channel not found");
            return;
        }

        textChannel.sendMessage("사용자가 없어서 음성 채널에서 퇴장합니다.").queue(
                null,
                failure -> log.atWarn()
                        .addKeyValue("guildId", guildId)
                        .addKeyValue("textChannelId", textChannelId)
                        .setCause(failure)
                        .log("voice-idle notification send failed")
        );
    }

    private record PendingDisconnect(
            long voiceChannelId,
            String token,
            ScheduledFuture<?> future
    ) {
    }
}
