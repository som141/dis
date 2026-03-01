package discordgateway;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

/**
 * ACTIVE일 때만 delegate에게 이벤트를 전달하는 게이트.
 * - SlashCommandInteractionEvent 등 "모든 이벤트"를 전달해야 함.
 * - ReadyEvent는 명령 등록(updateCommands)에 필요하므로 항상 통과(권장).
 */
public class ActiveOnlyListener implements EventListener {

    private final EventListener delegate;

    public ActiveOnlyListener(EventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        // ReadyEvent는 명령 등록 때문에 통과시키는 편이 운영이 편함
        if (event instanceof ReadyEvent) {
            delegate.onEvent(event);
            return;
        }

        if (!ActiveSwitch.isActive()) {
            return; // standby면 이벤트 처리 금지
        }

        delegate.onEvent(event);
    }
}
