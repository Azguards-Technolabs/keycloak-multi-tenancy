package dev.sultanov.keycloak.multitenancy.tracing;

import brave.Tracing;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;
import zipkin2.reporter.brave.ZipkinSpanHandler;

public final class TracingConfig {

    private static volatile TracingConfig INSTANCE;

    /**
     * Zipkin HTTP collector (v2). Default: production UI host with standard spans path.
     * Override with env {@code ZIPKIN_ENDPOINT} or system property {@code zipkin.endpoint}.
     */
    private static final String ZIPKIN_URL =
            System.getenv().getOrDefault("ZIPKIN_ENDPOINT",
                System.getProperty("zipkin.endpoint",
                    "https://whatatalk-monitoring.azguardstech.com/api/v2/spans"));

    private static final String SERVICE_NAME = "keycloak-multi-tenancy";

    private final Tracing tracing;

    private TracingConfig() {
        var sender    = URLConnectionSender.create(ZIPKIN_URL);
        var reporter  = AsyncReporter.create(sender);

        this.tracing = Tracing.newBuilder()
                .localServiceName(SERVICE_NAME)
                .addSpanHandler(ZipkinSpanHandler.create(reporter))
                .build();

        // Flush on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            reporter.flush();
            tracing.close();
        }));
    }

    public static TracingConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (TracingConfig.class) {
                if (INSTANCE == null) INSTANCE = new TracingConfig();
            }
        }
        return INSTANCE;
    }

    public Tracing getTracing() { return tracing; }
}
