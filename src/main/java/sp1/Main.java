package sp1;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {
    public static void main(String[] args) throws Exception {
        JDA jda = JDABuilder.createDefault(
                        "MTM4NTYzMjc3NjQyNTk2NzgwNw.GbV9c5.KCqGaESgvDlLbvQv06tC6pirym8a6t8XuoqZv4",
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(new Listeners())
                .build()
                .awaitReady();
        System.out.println("봇 가동 완료!");
    }
}
