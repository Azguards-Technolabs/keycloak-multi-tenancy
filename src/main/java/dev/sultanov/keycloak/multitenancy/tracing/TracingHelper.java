package dev.sultanov.keycloak.multitenancy.tracing;

import brave.Span;
import brave.Tracer;
import brave.propagation.Propagation;
import java.util.Map;
import org.apache.commons.lang3.ObjectUtils;

public final class TracingHelper {

    /** Brave {@link Propagation.Getter} needs {@code (carrier, key) -> value}; {@link Map#get} is only {@code (key)}. */
    private static final Propagation.Getter<Map<String, String>, String> HEADER_MAP_GETTER =
            (carrier, key) -> ObjectUtils.isEmpty(carrier) || ObjectUtils.isEmpty(key) ? null : carrier.get(key);

    private TracingHelper() {}

    /** Start a SERVER span, extracting context from headers if available. */
    public static Span startServerSpan(String spanName, java.util.Map<String, String> headers) {
        java.util.Map<String, String> caseInsensitiveHeaders = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (ObjectUtils.isNotEmpty(headers)) {
            caseInsensitiveHeaders.putAll(headers);
        }

        var tracing = TracingConfig.getInstance().getTracing();
        var extracted = tracing.propagation().extractor(HEADER_MAP_GETTER).extract(caseInsensitiveHeaders);
        return tracer().nextSpan(extracted).name(spanName).kind(Span.Kind.SERVER).start();
    }

    /** Start a SERVER span without context extraction. */
    public static Span startServerSpan(String spanName) {
        return startServerSpan(spanName, java.util.Collections.emptyMap());
    }

    /** Start a CLIENT span for an outbound HTTP call. */
    public static Span startClientSpan(String spanName) {
        return tracer().nextSpan().name(spanName).kind(Span.Kind.CLIENT).start();
    }

    /** Finish span once; tag error when {@code error} is non-null. */
    public static void finishSpan(Span span, Throwable error) {
        if (ObjectUtils.isEmpty(span)) {
            return;
        }
        if (ObjectUtils.isNotEmpty(error)) {
            String msg = error.getMessage();
            span.tag("error", msg != null ? msg : error.getClass().getName());
        }
        span.finish();
    }

    /**
     * Inject B3 multi headers into the given map so downstream
     * services (e.g. user-service) continue the same trace.
     */
    public static void injectB3Headers(Span span, Map<String, String> headers) {
        if (ObjectUtils.isEmpty(span)) return;
        var ctx = span.context();
        headers.put("X-B3-TraceId",  ctx.traceIdString());
        headers.put("X-B3-SpanId",   Long.toHexString(ctx.spanId()));
        if (ctx.parentIdAsLong() != 0L)
            headers.put("X-B3-ParentSpanId", Long.toHexString(ctx.parentIdAsLong()));
        headers.put("X-B3-Sampled",  ctx.sampled() == Boolean.TRUE ? "1" : "0");
    }

    /** Start a SERVER span, extracting context from JAX-RS headers. */
    public static Span startServerSpan(String spanName, jakarta.ws.rs.core.HttpHeaders headers) {
        return startServerSpan(spanName, getHeadersMap(headers));
    }

    private static java.util.Map<String, String> getHeadersMap(jakarta.ws.rs.core.HttpHeaders headers) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (ObjectUtils.isNotEmpty(headers)) {
            headers.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) result.put(k, v.get(0));
            });
        }
        return result;
    }

    public static Tracer tracer() {
        return TracingConfig.getInstance().getTracing().tracer();
    }
}
