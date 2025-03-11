package nl.dflipse.fit.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.dflipse.fit.strategy.util.Pair;

public class TaggedTimer {
    public static final String DEFAULT_TAG = "";
    private final Map<String, Timer> timers = new HashMap<>();

    public void start() {
        start(DEFAULT_TAG);
    }

    public void start(String tag) {
        if (!timers.containsKey(tag)) {
            timers.put(tag, new Timer());
        }

        timers.get(tag).start();
    }

    public void stop() {
        stop(DEFAULT_TAG);
    }

    public void stop(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        timers.get(tag).stop();
    }

    public long durationMs() {
        return durationMs(DEFAULT_TAG);
    }

    public long durationMs(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        return timers.get(tag).durationMs();
    }

    public double durationSeconds() {
        return durationSeconds(DEFAULT_TAG);
    }

    public double durationSeconds(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        return timers.get(tag).durationSeconds();
    }

    public List<Pair<String, Long>> getTimings() {
        return timers.entrySet()
                .stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue().durationMs()))
                .filter(pair -> pair.second() > 0)
                .toList();
    }

    private class Timer {
        private long start;
        private long end;

        private TimerState state = TimerState.INIT;

        private enum TimerState {
            INIT, RUNNING, STOPPED;
        }

        public void start() {
            switch (state) {
                case INIT:
                    start = System.currentTimeMillis();
                    state = TimerState.RUNNING;
                    break;

                default:
                    throw new IllegalStateException("Timer already started");
            }
        }

        public void stop() {
            switch (state) {
                case INIT:
                    throw new IllegalStateException("Timer not started");

                case RUNNING:
                    end = System.currentTimeMillis();
                    state = TimerState.STOPPED;
                    break;

                case STOPPED:
                    throw new IllegalStateException("Timer already stopped");
            }
        }

        public long durationMs() {
            if (state != TimerState.STOPPED) {
                return -1;
            }

            return end - start;
        }

        public double durationSeconds() {
            return durationMs() / 1000.0;
        }
    }
}