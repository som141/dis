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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private static final PlayerManager INSTANCE = new PlayerManager();

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final AudioPlayerManager audioPlayerManager;

    private PlayerManager() {
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        audioPlayerManager.getConfiguration().setOpusEncodingQuality(AudioConfiguration.OPUS_QUALITY_MAX);
        audioPlayerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        audioPlayerManager.getConfiguration().setFilterHotSwapEnabled(true);

        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true);
        this.audioPlayerManager.registerSourceManager(youtube);

        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    public static PlayerManager getINSTANCE() {
        return INSTANCE;
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
                var info = track.getInfo();
                future.complete(List.of(new Command.Choice(info.title + " - " + info.author, info.uri)));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<Command.Choice> out = new ArrayList<>();
                for (AudioTrack t : playlist.getTracks()) {
                    if (out.size() >= cap) break;
                    var info = t.getInfo();
                    out.add(new Command.Choice(info.title + " - " + info.author, info.uri));
                }
                future.complete(out);
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
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public void loadAndPlay(TextChannel textChannel, String trackURL) {
        final GuildMusicManager musicManager = this.getMusicManager(textChannel.getGuild());

        this.audioPlayerManager.loadItemOrdered(musicManager, trackURL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                musicManager.scheduler.queue(audioTrack, textChannel);
                textChannel.sendMessage("▶️ 재생: " + audioTrack.getInfo().title).queue();
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

                musicManager.scheduler.queue(firstTrack, textChannel);
                textChannel.sendMessage("▶️ 재생 (플레이리스트): " + firstTrack.getInfo().title).queue();
            }

            @Override
            public void noMatches() {
                textChannel.sendMessage("일치하는 결과가 없습니다. " + trackURL).queue();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                textChannel.sendMessage("재생할 수 없습니다. " + e.getMessage()).queue();
            }
        });
    }
}