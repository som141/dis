package discordgateway.common.command;

import org.slf4j.MDC;

import java.util.function.Supplier;

public final class MusicCommandTraceContext {

    private static final ThreadLocal<MusicCommandTrace> CURRENT = new ThreadLocal<>();
    private static final String MDC_COMMAND_ID = "commandId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_PRODUCER = "producer";
    private static final String MDC_SCHEMA_VERSION = "schemaVersion";

    private MusicCommandTraceContext() {
    }

    public static MusicCommandTrace current() {
        return CURRENT.get();
    }

    public static void runWith(MusicCommandTrace trace, Runnable runnable) {
        MusicCommandTrace previous = CURRENT.get();
        MdcSnapshot previousMdc = captureMdc();
        setOrRemove(trace);
        try {
            runnable.run();
        } finally {
            setOrRemove(previous);
            restoreMdc(previousMdc);
        }
    }

    public static <T> T callWith(MusicCommandTrace trace, Supplier<T> supplier) {
        MusicCommandTrace previous = CURRENT.get();
        MdcSnapshot previousMdc = captureMdc();
        setOrRemove(trace);
        try {
            return supplier.get();
        } finally {
            setOrRemove(previous);
            restoreMdc(previousMdc);
        }
    }

    private static void setOrRemove(MusicCommandTrace trace) {
        if (trace == null) {
            CURRENT.remove();
            clearMdc();
            return;
        }
        CURRENT.set(trace);
        putOrRemove(MDC_COMMAND_ID, trace.commandId());
        putOrRemove(MDC_CORRELATION_ID, trace.commandId());
        putOrRemove(MDC_PRODUCER, trace.producer());
        MDC.put(MDC_SCHEMA_VERSION, Integer.toString(trace.schemaVersion()));
    }

    private static MdcSnapshot captureMdc() {
        return new MdcSnapshot(
                MDC.get(MDC_COMMAND_ID),
                MDC.get(MDC_CORRELATION_ID),
                MDC.get(MDC_PRODUCER),
                MDC.get(MDC_SCHEMA_VERSION)
        );
    }

    private static void restoreMdc(MdcSnapshot snapshot) {
        putOrRemove(MDC_COMMAND_ID, snapshot.commandId());
        putOrRemove(MDC_CORRELATION_ID, snapshot.correlationId());
        putOrRemove(MDC_PRODUCER, snapshot.producer());
        putOrRemove(MDC_SCHEMA_VERSION, snapshot.schemaVersion());
    }

    private static void clearMdc() {
        MDC.remove(MDC_COMMAND_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_PRODUCER);
        MDC.remove(MDC_SCHEMA_VERSION);
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    private record MdcSnapshot(
            String commandId,
            String correlationId,
            String producer,
            String schemaVersion
    ) {
    }
}
