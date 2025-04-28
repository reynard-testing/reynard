package nl.dflipse.fit.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.dflipse.fit.strategy.util.Pair;

public class TaggedTimer {
    private final Map<String, Timer> timers = new LinkedHashMap<>();

    public void start(String tag) {
        if (!timers.containsKey(tag)) {
            timers.put(tag, new Timer());
        }

        timers.get(tag).start();
    }

    public void stop(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        timers.get(tag).stop();
    }

    public long durationMs(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        return timers.get(tag).durationMs();
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
            start = System.currentTimeMillis();
            state = TimerState.RUNNING;
        }

        public void stop() {
            if (state == TimerState.RUNNING ) {
                end = System.currentTimeMillis();
                state = TimerState.STOPPED;
            }
        }

        public long durationMs() {
            if (state != TimerState.STOPPED) {
                return System.currentTimeMillis() - start;
            }

            return end - start;
        }

        public double durationSeconds() {
            return durationMs() / 1000.0;
        }
    }
}