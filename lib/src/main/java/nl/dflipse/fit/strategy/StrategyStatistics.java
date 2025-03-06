package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyStatistics {
    private Map<String, Integer> generatorCount = new HashMap<>();
    private Map<String, Integer> prunerCount = new HashMap<>();
    private List<Long> timings = new ArrayList<>();

    private int totalRun = 0;
    private int totalGenerated = 0;
    private int totalPruned = 0;

    public void incrementGenerator(String generator, int count) {
        generatorCount.put(generator, generatorCount.getOrDefault(generator, 0) + count);
        totalGenerated += count;
    }

    public void incrementPruner(String pruner, int count) {
        prunerCount.put(pruner, prunerCount.getOrDefault(pruner, 0) + count);
    }

    public void incrementPruned(int count) {
        totalPruned += count;
    }

    private long getAverageTime() {
        return (long) timings.stream()
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0.0);
    }

    public void registerTime(long time) {
        timings.add(time);
        totalRun++;
    }

    // ---- Helper functions for reporting ----

    private String padRight(String s, int n) {
        return padRight(s, n, " ");
    }

    private String padRight(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return s + padding;
    }

    private String padLeft(String s, int n) {
        return padLeft(s, n, " ");
    }

    private String padLeft(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return padding + s;
    }

    private String padBoth(String s, int n) {
        return padBoth(s, n, " ");
    }

    private String padBoth(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd / 2);
        return padding + s + padding;
    }

    private int getMaxKeyLength(Map<String, Integer> map) {
        return map.keySet().stream().mapToInt(String::length).max().orElse(0);
    }

    private String asPercentage(int num, int div) {
        double percentage = 100d * num / (double) div;
        return String.format("%1.1f", percentage);
    }

    public void report() {
        int maxWidth = 32;
        String prunePercentage = asPercentage(totalPruned, totalGenerated);
        System.out.println(padBoth(" Stats ", maxWidth, "-"));
        System.out.println("Total generated     : " + totalGenerated);
        System.out.println("Total pruned        : " + totalPruned + " (" + prunePercentage + "%)");
        System.out.println("Total run           : " + totalRun);
        System.out.println("Average time        : " + getAverageTime() + " (ms)");

        System.out.println();
        System.out.println(padBoth(" Generators ", maxWidth, "-"));
        int maxGeneratorKeyLength = getMaxKeyLength(generatorCount);
        for (var entry : generatorCount.entrySet()) {
            String key = padRight(entry.getKey(), maxGeneratorKeyLength);
            System.out.println(key + " : " + entry.getValue());
        }

        int maxPrunerKeyLength = getMaxKeyLength(prunerCount);
        System.out.println();
        System.out.println(padBoth(" Pruners ", maxWidth, "-"));
        for (var entry : prunerCount.entrySet()) {
            String key = padRight(entry.getKey(), maxPrunerKeyLength);
            int value = entry.getValue();
            System.out.println(key + " : " + value + " (" + asPercentage(value, totalGenerated) + "%)");
        }
    }
}
