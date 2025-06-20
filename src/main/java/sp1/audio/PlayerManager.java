package sp1.audio;

import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.*;

public class PlayerManager {
    private static PlayerManager INSTANCE;
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private PlayerManager() {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        musicManagers = new HashMap<>();
    }

    public static synchronized PlayerManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerManager();
        return INSTANCE;
    }

    public synchronized GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(
                guild.getIdLong(),
                guildId -> {
                    GuildMusicManager manager = new GuildMusicManager(playerManager);
                    guild.getAudioManager().setSendingHandler(manager.getSendHandler());
                    return manager;
                }
        );
    }

    public void loadAndPlay(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, String trackUrl) {
        GuildMusicManager musicManager = getMusicManager(
                ((TextChannel) channel).getGuild()
        );
        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track);
                channel.sendMessage("🎶 큐에 추가되었습니다: " + track.getInfo().title).queue();
            }
            @Override public void playlistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                playlist.getTracks().forEach(musicManager.scheduler::queue);
                channel.sendMessage("🎶 플레이리스트 큐에 추가: " + playlist.getName()).queue();
            }
            @Override public void noMatches() {
                channel.sendMessage("⚠️ 검색 결과가 없습니다.").queue();
            }
            @Override public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                channel.sendMessage("❌ 로드 실패: " + exception.getMessage()).queue();
            }
        });
    }
}
