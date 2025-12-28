package dev.reynard.junit.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.reynard.junit.strategy.util.Pair;

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
            return;
        }

        timers.get(tag).stop();
    }

    public long durationMs(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        return timers.get(tag).durationNs();
    }

    public double durationSeconds(String tag) {
        if (!timers.containsKey(tag)) {
            throw new IllegalStateException("Timer with tag " + tag + " not started");
        }

        return timers.get(tag).durationS();
    }

    public List<Pair<String, Long>> getTimingsNs() {
        return timers.entrySet()
                .stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue().durationNs()))
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
            start = System.nanoTime();
            state = TimerState.RUNNING;
        }

        public void stop() {
            if (state == TimerState.RUNNING) {
                end = System.nanoTime();
                state = TimerState.STOPPED;
            }
        }

        public long durationNs() {
            if (state != TimerState.STOPPED) {
                return System.nanoTime() - start;
            }

            return end - start;
        }

        public double durationS() {
            return durationNs() / 1_000_000_000.0;
        }

        public double durationMs() {
            return durationNs() / 1_000_000.0;
        }
    }
}