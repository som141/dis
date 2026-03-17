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
import discordgateway.application.MusicCommandTrace;
import discordgateway.application.MusicCommandTraceContext;
import discordgateway.application.event.MusicEvent;
import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.bootstrap.AppProperties;
import discordgateway.bootstrap.YouTubeProperties;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final AudioPlayerManager audioPlayerManager;
    private final QueueRepository queueRepository;
    private final PlayerStateRepository playerStateRepository;
    private final GuildPlaybackLockManager playbackLockManager;
    private final MusicEventPublisher musicEventPublisher;
    private final MusicEventFactory musicEventFactory;
    private final String nodeName;

    public PlayerManager(
            QueueRepository queueRepository,
            PlayerStateRepository playerStateRepository,
            GuildPlaybackLockManager playbackLockManager,
            AppProperties appProperties,
            YouTubeProperties youTubeProperties,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        this.queueRepository = queueRepository;
        this.playerStateRepository = playerStateRepository;
        this.playbackLockManager = playbackLockManager;
        this.musicEventPublisher = musicEventPublisher;
        this.musicEventFactory = musicEventFactory;
        this.nodeName = resolveNodeName(appProperties.getNodeName());
        this.audioPlayerManager = createAudioPlayerManager(youTubeProperties);
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
                        if (out.size() >= cap) {
                            break;
                        }

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
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            GuildMusicManager guildMusicManager = new GuildMusicManager(
                    guildId,
                    this.audioPlayerManager,
                    queueRepository,
                    playerStateRepository,
                    playbackLockManager,
                    musicEventPublisher,
                    musicEventFactory,
                    nodeName
            );
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public void loadAndPlay(TextChannel textChannel, String trackUrl) {
        final GuildMusicManager musicManager = this.getMusicManager(textChannel.getGuild());
        final long guildId = textChannel.getGuild().getIdLong();
        final MusicCommandTrace trace = MusicCommandTraceContext.current();

        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    boolean queued = musicManager.scheduler.queue(audioTrack, textChannel);
                    if (queued) {
                        textChannel.sendMessage("🎵 대기열에 추가: " + audioTrack.getInfo().title).queue();
                    } else {
                        textChannel.sendMessage("▶️ 재생: " + audioTrack.getInfo().title).queue();
                    }
                });
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (audioPlaylist.getTracks().isEmpty()) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        trackUrl,
                                        MusicEvent.TransitionSource.COMMAND,
                                        "empty_playlist",
                                        "Playlist did not contain any tracks."
                                )
                        );
                        textChannel.sendMessage("플레이리스트가 비어 있습니다.").queue();
                        return;
                    }

                    AudioTrack firstTrack = audioPlaylist.getSelectedTrack() != null
                            ? audioPlaylist.getSelectedTrack()
                            : audioPlaylist.getTracks().get(0);

                    boolean queued = musicManager.scheduler.queue(firstTrack, textChannel);
                    if (queued) {
                        textChannel.sendMessage("🎵 대기열에 추가 (플레이리스트): " + firstTrack.getInfo().title).queue();
                    } else {
                        textChannel.sendMessage("▶️ 재생 (플레이리스트): " + firstTrack.getInfo().title).queue();
                    }
                });
            }

            @Override
            public void noMatches() {
                MusicCommandTraceContext.runWith(trace, () -> {
                    musicEventPublisher.publish(
                            musicEventFactory.trackLoadFailed(
                                    guildId,
                                    trackUrl,
                                    MusicEvent.TransitionSource.COMMAND,
                                    "no_matches",
                                    "No matching track was found."
                            )
                    );
                    textChannel.sendMessage("일치하는 결과가 없습니다. " + trackUrl).queue();
                });
            }

            @Override
            public void loadFailed(FriendlyException e) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    musicEventPublisher.publish(
                            musicEventFactory.trackLoadFailed(
                                    guildId,
                                    trackUrl,
                                    MusicEvent.TransitionSource.COMMAND,
                                    "load_failed",
                                    safeFailureMessage(e)
                            )
                    );
                    textChannel.sendMessage("재생할 수 없습니다. " + e.getMessage()).queue();
                });
            }
        });
    }

    private AudioPlayerManager createAudioPlayerManager(YouTubeProperties youTubeProperties) {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();

        manager.getConfiguration().setOpusEncodingQuality(AudioConfiguration.OPUS_QUALITY_MAX);
        manager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        manager.getConfiguration().setFilterHotSwapEnabled(true);

        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(
                true,
                new Music(),
                new Tv(),
                new Web(),
                new WebEmbedded(),
                new AndroidVr()
        );

        configureYoutubeOauth(youtube, youTubeProperties);
        manager.registerSourceManager(youtube);

        AudioSourceManagers.registerRemoteSources(
                manager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
        AudioSourceManagers.registerLocalSource(manager);
        return manager;
    }

    private void configureYoutubeOauth(YoutubeAudioSourceManager youtube, YouTubeProperties properties) {
        String refreshToken = trimToNull(properties.getRefreshToken());
        boolean oauthInit = properties.isOauthInit();

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

    private String trimToMax(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveNodeName(String configuredNodeName) {
        String trimmed = trimToNull(configuredNodeName);
        return trimmed != null ? trimmed : "discord-gateway";
    }

    private String safeFailureMessage(FriendlyException e) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return "Unknown load failure";
        }
        return e.getMessage();
    }
}
