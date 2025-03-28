package nl.dflipse.fit.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.strategy.generators.Generator;

public class Enumerate {

    public static long getGeneratedCount(Generator gen) {
        long i = 0;
        while (gen.generate().size() > 0) {
            i++;
        }
        return i;
    }

    public static List<Faultload> getGenerated(Generator gen) {
        List<Faultload> faultloads = new ArrayList<>();
        while (true) {
            var newFaultloads = gen.generate();

            if (newFaultloads.size() == 0) {
                break;
            }

            faultloads.addAll(newFaultloads);
        }
        return faultloads;
    }

    public static Set<Faultload> getGeneratedSet(Generator gen) {
        return new HashSet<>(getGenerated(gen));
    }
}
