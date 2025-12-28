package dev.reynard.junit.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.strategy.components.generators.Generator;

public class Enumerate {

    public static long getGeneratedCount(Generator gen) {
        long i = 0;
        while (gen.generate() != null) {
            i++;
        }
        return i;
    }

    public static List<Faultload> getGenerated(Generator gen) {
        List<Faultload> faultloads = new ArrayList<>();
        while (true) {
            var newFaultload = gen.generate();

            if (newFaultload == null) {
                break;
            }

            faultloads.add(newFaultload);
        }
        return faultloads;
    }

    public static Set<Faultload> getGeneratedSet(Generator gen) {
        return new HashSet<>(getGenerated(gen));
    }
}
