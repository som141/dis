package discordgateway.application;

import java.util.function.Supplier;

public final class MusicCommandTraceContext {

    private static final ThreadLocal<MusicCommandTrace> CURRENT = new ThreadLocal<>();

    private MusicCommandTraceContext() {
    }

    public static MusicCommandTrace current() {
        return CURRENT.get();
    }

    public static void runWith(MusicCommandTrace trace, Runnable runnable) {
        MusicCommandTrace previous = CURRENT.get();
        setOrRemove(trace);
        try {
            runnable.run();
        } finally {
            setOrRemove(previous);
        }
    }

    public static <T> T callWith(MusicCommandTrace trace, Supplier<T> supplier) {
        MusicCommandTrace previous = CURRENT.get();
        setOrRemove(trace);
        try {
            return supplier.get();
        } finally {
            setOrRemove(previous);
        }
    }

    private static void setOrRemove(MusicCommandTrace trace) {
        if (trace == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(trace);
    }
}
