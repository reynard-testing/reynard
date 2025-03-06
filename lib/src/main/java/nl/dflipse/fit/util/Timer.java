package nl.dflipse.fit.util;

public class Timer {
    private long start;
    private long end;

    public Timer() {
        start();
    }

    public void start() {
        start = System.currentTimeMillis();
    }

    public void stop() {
        end = System.currentTimeMillis();
    }

    public long durationMs() {
        return end - start;
    }

    public double durationSeconds() {
        return durationMs() / 1000.0;
    }
}