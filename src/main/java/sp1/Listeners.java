package sp1;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jetbrains.annotations.NotNull;
import sp1.audio.PlayerManager;

public class Listeners extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        String[] args = event.getMessage().getContentRaw().split("\\s+");
        MessageChannel channel = event.getChannel();

        switch (args[0].toLowerCase()) {
            case "!join":
                Member self = event.getGuild().getSelfMember();
                GuildVoiceState selfState = self.getVoiceState();
                GuildVoiceState authorState = event.getMember().getVoiceState();
                if (authorState.inAudioChannel()) {
                    event.getGuild().getAudioManager().openAudioConnection(authorState.getChannel());
                } else {
                    channel.sendMessage("⚠️ 먼저 음성 채널에 입장해주세요!").queue();
                }
                break;

            case "!play":
                if (args.length < 2) {
                    channel.sendMessage("Usage: `!play <YouTube URL or 검색어>`").queue();
                    return;
                }
                String trackUrl = args[1];
                PlayerManager.getInstance().loadAndPlay(channel, trackUrl);
                break;

            case "!skip":
                PlayerManager.getInstance()
                        .getMusicManager(event.getGuild())
                        .scheduler.nextTrack();
                channel.sendMessage("⏭️ 다음 곡으로 넘어갑니다.").queue();
                break;

            case "!leave":
                event.getGuild().getAudioManager().closeAudioConnection();
                channel.sendMessage("👋 음성 채널에서 나갑니다.").queue();
                break;
        }
    }
}
