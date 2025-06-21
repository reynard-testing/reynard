package dev.reynard.junit.strategy.util;

public class StringFormat {
    public static String padRight(String s, int n) {
        return padRight(s, n, " ");
    }

    public static String padRight(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return s + padding;
    }

    public static String padLeft(String s, int n) {
        return padLeft(s, n, " ");
    }

    public static String padLeft(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd);
        return padding + s;
    }

    public static String padBoth(String s, int n) {
        return padBoth(s, n, " ");
    }

    public static String padBoth(String s, int n, String character) {
        int toAdd = Math.max(0, n - s.length());
        String padding = character.repeat(toAdd / 2);
        return padding + s + padding;
    }

    public static double percentage(long num, long div) {
        return 100d * num / (double) div;
    }

    public static String asPercentage(long num, long div) {
        return String.format("%1.1f", percentage(num, div));
    }
}
