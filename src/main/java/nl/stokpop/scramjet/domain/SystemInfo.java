package nl.stokpop.scramjet.domain;

import java.util.List;

public record SystemInfo(
        int availableProcessors,
        long maxMemory,
        long freeMemory,
        long totalMemory,
        int threads,
        List<String> threadNames) {
}
