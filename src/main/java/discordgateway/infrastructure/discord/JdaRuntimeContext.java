package discordgateway.infrastructure.discord;

import net.dv8tion.jda.api.JDA;

public class JdaRuntimeContext {

    private volatile JDA jda;

    public void bind(JDA jda) {
        this.jda = jda;
    }

    public JDA current() {
        return jda;
    }
}
