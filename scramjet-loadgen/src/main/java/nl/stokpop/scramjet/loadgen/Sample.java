package nl.stokpop.scramjet.loadgen;

/**
 * Outcome of one request.
 *
 * @param step              scenario step name
 * @param offsetNanos       scheduled start, relative to the start of the run
 * @param responseTimeNanos measured from the scheduled start
 * @param failure           failure reason (HTTP status or exception name), null on success
 */
record Sample(String step, long offsetNanos, long responseTimeNanos, String failure) {

    boolean ok() {
        return failure == null;
    }
}
