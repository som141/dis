package discordgateway;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
public class Main {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("token");

        JDA jda = JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .addEventListeners(new Listeners())
                .build()
                .awaitReady();
        System.out.println("bot start!");
    }
}
