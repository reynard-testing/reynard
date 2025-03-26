package nl.dflipse.fit.strategy.util;

public class SpaceEstimate {

    public static long spaceSize(long modes, long points) {
        return (long) (Math.pow(1 + modes, points));
    }

    public static long nonEmptySpaceSize(long modes, long points) {
        return spaceSize(modes, points) - 1;
    }

}
