package discordgateway;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class ActiveOnlyListener extends ListenerAdapter {

    private final ListenerAdapter delegate;

    public ActiveOnlyListener(ListenerAdapter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!ActiveSwitch.isActive()) {
            return; // standby면 아무것도 처리하지 않음(중복 응답 방지)
        }
        delegate.onMessageReceived(event);
    }
}
