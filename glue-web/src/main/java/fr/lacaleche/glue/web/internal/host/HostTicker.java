package fr.lacaleche.glue.web.internal.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Client-tick lifecycle checks for host-owned surfaces. Client thread only. */
public final class HostTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static final List<BooleanSupplier> HOSTS = new ArrayList<>();

    private HostTicker() {
    }

    /** Runs the check after every client tick until it returns false or throws. */
    public static void track(BooleanSupplier host) {
        HOSTS.add(Objects.requireNonNull(host, "host"));
    }

    public static void tick() {
        if (HOSTS.isEmpty()) return;
        for (BooleanSupplier host : List.copyOf(HOSTS)) {
            boolean keep;
            try {
                keep = host.getAsBoolean();
            } catch (RuntimeException exception) {
                LOGGER.error("Web host lifecycle check failed and was removed", exception);
                keep = false;
            }
            if (!keep) HOSTS.remove(host);
        }
    }
}
