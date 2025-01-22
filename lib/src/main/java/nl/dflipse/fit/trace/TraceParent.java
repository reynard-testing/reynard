package nl.dflipse.fit.trace;

public class TraceParent {
    public String version;
    public String traceId;
    public String parentSpanId;
    public String traceFlags;

    public TraceParent(String header) {
        String[] parts = header.split("-");
        if (parts.length == 4) {
            this.version = parts[0];
            this.traceId = parts[1];
            this.parentSpanId = parts[2];
            this.traceFlags = parts[3];
        } else {
            throw new IllegalArgumentException("Invalid traceparent header");
        }
    }

    public TraceParent() {
        this.version = "00";
        this.traceId = genTraceId();
        this.parentSpanId = initialSpanId();
        this.traceFlags = "01";
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
