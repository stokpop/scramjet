package nl.stokpop.scramjet.domain;

public record ParallelInfo(
        String name,
        int activeThreadCount,
        int parallelism,
        boolean asyncMode,
        int poolSize,
        int queuedSubmissionCount,
        long queuedTaskCount,
        int runningThreadCount,
        long stealCount) {
}
