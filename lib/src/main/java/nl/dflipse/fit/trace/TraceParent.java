package io.github.delanoflipse.fit.trace;

public class TraceParent {
    public String version;
    public String traceId;
    public String parentSpanId;
    public String traceFlags;

    public static TraceParent fromHeader(String header) {
        String[] parts = header.split("-");
        if (parts.length == 4) {
            return new TraceParent(parts[0], parts[1], parts[2], parts[3]);
        } else {
            throw new IllegalArgumentException("Invalid traceparent header");
        }
    }

    public TraceParent() {
        this(genTraceId());
    }

    public TraceParent(String traceId) {
        this("00", traceId, initialSpanId(), "01");
    }

    public TraceParent(String traceId, String parentSpanId) {
        this("00", traceId, parentSpanId, "01");
    }

    public TraceParent(String version, String traceId, String parentSpanId, String traceFlags) {
        this.version = version;
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.traceFlags = traceFlags;
    }

    private static String genId(int numberOfBytes) {
        byte[] bytes = new byte[numberOfBytes];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static String genTraceId() {
        return genId(16);
    }

    public static String genSpanId() {
        return genId(8);
    }

    public static String initialSpanId() {
        return "0000000000000001";
    }

    public String toString() {
        return this.version + "-" + this.traceId + "-" + this.parentSpanId + "-" + this.traceFlags;
    }
}
