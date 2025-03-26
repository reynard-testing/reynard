package nl.dflipse.fit.strategy.util;

public class SpaceEstimate {

    public static long spaceSize(long modes, long points) {
        return (long) (Math.pow(1 + modes, points));
    }

    public static long spaceSize(long modes, long points, long subset) {
        return (long) (Math.pow(modes, subset) * Math.pow(1 + modes, points - subset));
    }

    public static long nonEmptySpaceSize(long modes, long points) {
        return spaceSize(modes, points) - 1;
    }

    public static long nonEmptySpaceSize(long modes, long points, long subset) {
        return spaceSize(modes, points, subset) - 1;
    }

}
