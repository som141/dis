package discordgateway.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);
    private static final PlayerManager INSTANCE = new PlayerManager();

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final AudioPlayerManager audioPlayerManager;

    private volatile QueueRepository queueRepository;
    private volatile PlayerStateRepository playerStateRepository;
    private volatile GuildPlaybackLockManager playbackLockManager;

    private PlayerManager() {
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        audioPlayerManager.getConfiguration().setOpusEncodingQuality(AudioConfiguration.OPUS_QUALITY_MAX);
        audioPlayerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        audioPlayerManager.getConfiguration().setFilterHotSwapEnabled(true);

        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
                true,
                new Music(),
                new Tv(),
                new Web(),
                new WebEmbedded(),
                new AndroidVr()
        );

        configureYoutubeOauth(youtube);
        this.audioPlayerManager.registerSourceManager(youtube);

        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    public void setQueueRepository(QueueRepository queueRepository) {
        this.queueRepository = Objects.requireNonNull(queueRepository);
    }

    public void setPlayerStateRepository(PlayerStateRepository playerStateRepository) {
        this.playerStateRepository = Objects.requireNonNull(playerStateRepository);
    }

    public void setPlaybackLockManager(GuildPlaybackLockManager playbackLockManager) {
        this.playbackLockManager = Objects.requireNonNull(playbackLockManager);
    }

    private void configureYoutubeOauth(YoutubeAudioSourceManager youtube) {
        String refreshToken = readEnvTrimmed("YOUTUBE_REFRESH_TOKEN");
        boolean oauthInit = Boolean.parseBoolean(System.getenv().getOrDefault("YOUTUBE_OAUTH_INIT", "false"));

        if (refreshToken != null) {
            youtube.useOauth2(refreshToken, true);
            log.info("YouTube OAuth enabled with refresh token.");
            return;
        }

        if (oauthInit) {
            youtube.useOauth2(null, false);
            log.info("YouTube OAuth bootstrap mode enabled. Check console logs for device flow.");
            return;
        }

        log.warn("YouTube OAuth disabled. No YOUTUBE_REFRESH_TOKEN found.");
    }

    private String readEnvTrimmed(String key) {
        String value = System.getenv(key);
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    public static PlayerManager getINSTANCE() {
        return INSTANCE;
    }

    private String trimToMax(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public CompletableFuture<List<Command.Choice>> searchYouTubeChoices(String query, int limit) {
        CompletableFuture<List<Command.Choice>> future = new CompletableFuture<>();

        if (query == null || query.isBlank()) {
            future.complete(List.of());
            return future;
        }

        String identifier = "ytsearch:" + query;
        int cap = Math.max(1, Math.min(limit, 25));

        this.audioPlayerManager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                try {
                    var info = track.getInfo();
                    String name = trimToMax(info.title + " - " + info.author, 100);
                    String value = "https://www.youtube.com/watch?v=" + track.getIdentifier();
                    future.complete(List.of(new Command.Choice(name, value)));
                } catch (Exception e) {
                    future.complete(List.of());
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                try {
                    List<Command.Choice> out = new ArrayList<>();
                    for (AudioTrack track : playlist.getTracks()) {
                        if (out.size() >= cap) break;

                        var info = track.getInfo();
                        String name = trimToMax(info.title + " - " + info.author, 100);
                        String value = "https://www.youtube.com/watch?v=" + track.getIdentifier();

                        out.add(new Command.Choice(name, value));
                    }
                    future.complete(out);
                } catch (Exception e) {
                    future.complete(List.of());
                }
            }

            @Override
            public void noMatches() {
                future.complete(List.of());
            }

            @Override
            public void loadFailed(FriendlyException e) {
                future.complete(List.of());
            }
        });

        return future;
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        QueueRepository repo = this.queueRepository;
        PlayerStateRepository stateRepository = this.playerStateRepository;
        GuildPlaybackLockManager lockManager = this.playbackLockManager;
        if (repo == null) {
            throw new IllegalStateException("QueueRepository is not configured.");
        }
        if (stateRepository == null) {
            throw new IllegalStateException("PlayerStateRepository is not configured.");
        }
        if (lockManager == null) {
            throw new IllegalStateException("GuildPlaybackLockManager is not configured.");
        }

        return this.musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            GuildMusicManager guildMusicManager = new GuildMusicManager(
                    guildId,
                    this.audioPlayerManager,
                    repo,
                    stateRepository,
                    lockManager
            );
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public void loadAndPlay(TextChannel textChannel, String trackUrl) {
        final GuildMusicManager musicManager = this.getMusicManager(textChannel.getGuild());

        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                boolean queued = musicManager.scheduler.queue(audioTrack, textChannel);
                if (queued) {
                    textChannel.sendMessage("📥 대기열에 추가: " + audioTrack.getInfo().title).queue();
                } else {
                    textChannel.sendMessage("▶️ 재생: " + audioTrack.getInfo().title).queue();
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                if (audioPlaylist.getTracks().isEmpty()) {
                    textChannel.sendMessage("플레이리스트가 비어 있습니다.").queue();
                    return;
                }

                AudioTrack firstTrack = audioPlaylist.getSelectedTrack() != null
                        ? audioPlaylist.getSelectedTrack()
                        : audioPlaylist.getTracks().get(0);

                boolean queued = musicManager.scheduler.queue(firstTrack, textChannel);
                if (queued) {
                    textChannel.sendMessage("📥 대기열에 추가 (플레이리스트): " + firstTrack.getInfo().title).queue();
                } else {
                    textChannel.sendMessage("▶️ 재생 (플레이리스트): " + firstTrack.getInfo().title).queue();
                }
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage("일치하는 결과가 없습니다. " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                textChannel.sendMessage("재생할 수 없습니다. " + e.getMessage()).queue();
            }
        });
    }
}
