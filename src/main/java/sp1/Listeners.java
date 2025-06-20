package sp1;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import sp1.audio.PlayerManager;
import sp1.audio.GuildMusicManager;

import java.util.List;

public class Listeners extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // 봇 자체 메시지 무시
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty()) return;

        String[] parts = content.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        switch (cmd) {
            case "!join":
            case "들어와":
            case "나와":
                joinChannel(event);
                break;

            case "!leave":
            case "퇴장":
            case "나가":
                leaveChannel(event);
                break;

            case "!play":
            case "노래":
                if (parts.length < 2) {
                    event.getChannel().sendMessage("❗ 사용법: `!play <검색어 또는 URL>`").queue();
                } else {
                    playMusic(event, parts[1]);
                }
                break;

            case "!stop":
            case "정지":
                stopMusic(event);
                break;

            case "!skip":
            case "스킵":
                skipMusic(event);
                break;


            case "!list":
                printList(event);
                break;


            case "!extract":
                removeList(event);
        }
    }

    private void removeList(MessageReceivedEvent event) {
        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(event.getGuild());

    }

    private void printList(MessageReceivedEvent event) {
        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(event.getGuild());
        List<String> s= gm.scheduler.showList();
        event.getChannel().sendMessage("현재 음악 대기열 = "+ s).queue();

    }

    private void joinChannel(MessageReceivedEvent event) {
        Member author = event.getMember();
        if (author == null || author.getVoiceState() == null || !author.getVoiceState().inAudioChannel()) {
            event.getChannel().sendMessage("⚠️ 먼저 음성 채널에 들어가세요!").queue();
            return;
        }
        VoiceChannel vc = (VoiceChannel) author.getVoiceState().getChannel();
        AudioManager am = event.getGuild().getAudioManager();
        am.openAudioConnection(vc);
        event.getChannel().sendMessage("✅ 음성 채널에 입장했습니다.").queue();
    }

    private void leaveChannel(MessageReceivedEvent event) {
        AudioManager am = event.getGuild().getAudioManager();
        if (!am.isConnected()) {
            event.getChannel().sendMessage("⚠️ 봇이 음성 채널에 들어와 있지 않습니다.").queue();
            return;
        }
        am.closeAudioConnection();
        event.getChannel().sendMessage("👋 음성 채널에서 퇴장했습니다.").queue();
    }

    private void playMusic(MessageReceivedEvent event, String query) {
        Member author = event.getMember();
        Guild guild = event.getGuild();
        TextChannel textChannel = (TextChannel) event.getChannel();

        if (author == null || author.getVoiceState() == null || !author.getVoiceState().inAudioChannel()) {
            textChannel.sendMessage("⚠️ 먼저 음성 채널에 들어가세요!").queue();
            return;
        }
        AudioManager am = guild.getAudioManager();
        if (!guild.getSelfMember().getVoiceState().inAudioChannel()) {
            VoiceChannel vc = (VoiceChannel) author.getVoiceState().getChannel();
            am.openAudioConnection(vc);
        }

        // URL 또는 검색 모드
        String trackUrl = query.startsWith("http") ? query : "ytsearch:" + query;
        PlayerManager.getINSTANCE().loadAndPlay(textChannel, trackUrl, author);
    }

    private void stopMusic(MessageReceivedEvent event) {
        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(event.getGuild());
        // 현재 곡 중지
        gm.audioPlayer.stopTrack();
        // 큐 비우기 (TrackScheduler 에 clearQueue() 구현 필요)
        gm.scheduler.clearQueue();
        event.getChannel().sendMessage("⏹️ 재생을 중지하고 큐를 비웠습니다.").queue();
    }

    private void skipMusic(MessageReceivedEvent event) {
        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(event.getGuild());
        gm.scheduler.nextTrack();
        event.getChannel().sendMessage("⏭️ 다음 곡으로 건너뜁니다.").queue();
    }

}
