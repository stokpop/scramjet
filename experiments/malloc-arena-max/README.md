# Experiment: MALLOC_ARENA_MAX=2 and TrimNativeHeapInterval

Question: does `MALLOC_ARENA_MAX=2` reduce the native memory the scramjet JVM holds on to, is
that memory fragmentation, and what does it cost in malloc lock contention? And how does the
JVM's own periodic trimming, `-XX:TrimNativeHeapInterval`, compare, alone and combined?

## Background

glibc `malloc` hands out memory from *arenas*. When a thread finds the arena it wants locked,
glibc creates another one, up to `8 × cores` on 64-bit. Each arena reserves 64 MB of virtual
memory. Freed memory stays in the arena for reuse instead of going back to the OS, so resident
memory (RSS) grows beyond what the JVM itself uses. Two different things hide in that extra memory:

- **Retention**: whole pages that are free, kept by malloc. `malloc_trim` can give them back.
- **Fragmentation**: free space on pages that also hold live allocations. These pages cannot be
  given back until everything on them is freed. The more arenas, the more scattered this gets.

Two ways to limit the extra memory:

- `MALLOC_ARENA_MAX=2` caps the number of arenas: less memory kept per arena and less scattering,
  at the risk of threads waiting on each other's arena lock. It must be in the environment of
  the `java` process:

  ```shell
  MALLOC_ARENA_MAX=2 java -jar scramjet-service.jar
  # or
  export MALLOC_ARENA_MAX=2
  java -jar scramjet-service.jar
  ```

- `-XX:TrimNativeHeapInterval=<millis>` (JDK 21 and later, off by default) makes the JVM call
  `malloc_trim` periodically: retained free pages go back to the OS, all arenas stay.

Both only affect `malloc`. The Java heap is reserved by the JVM with `mmap` directly, so heap
allocation and GC are not affected.

## Method

[`run.sh`](run.sh) starts the service four times, each with `-Xmx256m`, Native Memory Tracking
and a JFR recording, and runs the same phases with the [load generator](../../README.md#load-generator):

| Run | Setting |
|---|---|
| default | nothing |
| arena-max-2 | `MALLOC_ARENA_MAX=2` |
| trim-5s | `-XX:TrimNativeHeapInterval=5000` |
| arena-max-2-trim-5s | both |

| Phase | Load | Purpose |
|---|---|---|
| heap-churn | `churn` scenario, 50 req/s | Java heap churn, expected: no effect |
| native-retain | `native` scenario, 100 req/s, 20 × 64 KB per call, held 100 ms | Many request threads holding native memory at the same time: arenas and retention |
| native-contend | `native` scenario, 200 req/s, 1000 × 1 KB per call, not held | Many small mallocs: arena lock contention |

Native segments are allocated with the FFM API (`Arena.allocate`), which calls `malloc`.
Sizes stay below glibc's 128 KB mmap threshold, otherwise `malloc` maps them directly and the
arenas are not involved.

Measured after each phase:

- **RSS**: resident memory of the process (`/proc/<pid>/status`)
- **VSZ**: virtual size; every arena adds 64 MB
- **NMT**: memory the JVM tracks as committed (`jcmd <pid> VM.native_memory summary`)
- **RSS − NMT**: resident memory the JVM does not account for, mostly freed memory still held
  by malloc. Rough: NMT also counts committed memory that is not resident yet, so it can go negative.
- **arenas**: number of 64 MB aligned arena reservations in `/proc/<pid>/maps`
- **native step latency** and **voluntary context switches** of the Tomcat request threads: a
  contended arena lock ends in a futex wait, which counts as a voluntary context switch

At the end of each run, to separate retention from fragmentation:

- `jcmd <pid> System.native_heap_info` (glibc `malloc_info`): memory malloc got from the OS and
  how much of it is free, in how many chunks. Free pages already trimmed stay in this count
  while no longer resident.
- `jcmd <pid> System.trim_native_heap` (`malloc_trim`): how much RSS a trim gives back. What is
  left above the other runs cannot be trimmed: fragmentation and per-arena overhead.

JFR records `jdk.ResidentSetSize` and `jdk.NativeMemoryUsageTotal` every second, and uses JEP 520
method timing for `SegmentAllocator::allocate` (malloc plus zeroing) and the request method
`OffHeapMemory::nativeChurn` (which also frees).

Run it with a built project (`./mvnw package`) and port 8080 free:

```shell
experiments/malloc-arena-max/run.sh        # 60 s per phase, ~20 minutes
experiments/malloc-arena-max/run.sh 20     # shorter phases
```

Recordings, service logs and load generator reports go to `out/` (not in git).

## Results

Ubuntu, glibc 2.39, OpenJDK 25.0.4, 8 cores, 15 GB. One run per setting, all four in the same
session, so treat differences of about 10 MB as noise. RSS values in MB.

### Memory

| | default | arena-max-2 | trim-5s | both |
|---|---|---|---|---|
| arenas at baseline / at end | 47 / 61 | 1 / 2 | 48 / 62 | 1 / 1 |
| VSZ at end | 5926 MB | 2078 MB | 5925 MB | 2049 MB |
| RSS after native-retain | 401 | 318 | 300 | 331 |
| RSS at end | 402 | 319 | 289 | **276** |
| RSS peak (JFR) | 403 | 320 | 334 | 331 |
| RSS − NMT at end (JFR) | 146 | 36 | 26 | −9 |

The JVM itself asked for about the same in all runs: NMT peaked at 394 to 414 MB.

### Retention vs fragmentation (end of run)

| | default | arena-max-2 | trim-5s | both |
|---|---|---|---|---|
| malloc: from OS / free | 274 / 225 MB | 143 / 85 MB | 259 / 207 MB | 129 / 75 MB |
| free chunks | 12,443 in 64 heaps | 7,423 in 2 heaps | 11,347 in 64 heaps | 7,867 in 2 heaps |
| RSS given back by a final `malloc_trim` | 108 MB | 57 MB | 13 MB | 7 MB |
| RSS after that trim | 295 | **263** | 279 | 272 |

### Latency and contention

| Phase | Metric | default | arena-max-2 | trim-5s | both |
|---|---|---|---|---|---|
| heap-churn | churn p50 / p99 | 4.0 / 10.6 ms | 4.0 / 9.8 ms | 4.0 / 12.6 ms | 3.9 / 11.2 ms |
| native-retain | native p50 / p99 | 102.5 / 104.6 ms | 102.5 / 104.4 ms | 102.5 / 104.4 ms | 102.5 / 104.5 ms |
| native-contend | native p50 / p99 | 2.2 / 3.6 ms | 2.2 / 3.6 ms | 2.1 / 3.7 ms | 2.1 / 3.5 ms |
| native-contend | worker voluntary context switches | 12,353 | 12,439 | 12,393 | 12,494 |
| whole run | `SegmentAllocator.allocate(long)` average / max (6.06 M calls) | 0.47 µs / 12.1 ms | 0.46 µs / 9.5 ms | 0.47 µs / 10.8 ms | 0.47 µs / 10.6 ms |

## Conclusions

- **Heap churn: no effect**, as expected. The Java heap does not go through malloc.
- **Most of the extra memory is retention, not fragmentation.** By default malloc held 225 MB of
  free memory at the end, and one `malloc_trim` gave 108 MB of RSS back. That memory was free
  whole pages, kept by design.
- **Fragmentation is the smaller part.** After trimming, the default run still used about 30 MB
  more than the runs with `MALLOC_ARENA_MAX=2` (295 vs 263 and 272 MB): free space scattered over
  61 arenas on pages that also hold live data, plus per-arena overhead. That is what
  "fragmentation" in `MALLOC_ARENA_MAX` advice refers to, and in this test it is the smaller share.
- **`MALLOC_ARENA_MAX=2`** limits both: 83 MB less RSS at the end, the lowest RSS after a trim,
  and 3.8 GB less virtual size (which matters for tools and limits that look at VSZ).
- **`TrimNativeHeapInterval=5000`** limits retention: 113 MB less RSS at the end, with all arenas
  kept. A final trim found only 13 MB left to give back. It does not reduce fragmentation or
  virtual size.
- **Both combined gives the lowest RSS at the end** (276 MB, 126 MB less than default) and the low
  virtual size of the arena cap. Its floor after a trim equals `MALLOC_ARENA_MAX=2` alone within
  noise, so the combination adds the periodic trim's retention cleanup on top of the arena cap's
  fragmentation and VSZ benefits.
- **Peaks are not lower with trimming.** Trimming every 5 s cleans up after the fact, so the peak
  RSS during load (331 to 334 MB) is no better than the arena cap alone (320 MB). A shorter
  interval trims more often at more CPU cost; the cost of trimming was not measured separately.
- **No measurable contention** at this load (8 cores, up to 200 requests/s, about 100,000
  mallocs/s): latency, context switches and malloc timing are the same within noise for all
  four settings. That does not prove there is none: more cores, more threads allocating natively
  at the same time, or higher allocation rates can show lock waits. Repeat the contention phase
  with higher `--rate` and `--native-segments` on the target hardware before rolling it out.

## Seeing malloc lock contention directly

Without root the signals above are indirect. With root, look for threads waiting on glibc's
internal lock, `__lll_lock_wait_private`, below `malloc` and `free`:

```shell
# where does the JVM wait on the malloc lock, with stacks
sudo perf record -g -p <pid> -- sleep 30
sudo perf report --no-children --symbol-filter=__lll_lock_wait_private

# count lock waits per stack while the load runs
sudo bpftrace -e 'uprobe:/lib/x86_64-linux-gnu/libc.so.6:__lll_lock_wait_private { @[ustack(6)] = count(); }'
```

## Notes on JFR

- JFR cannot see fragmentation itself. The useful signal is the gap between
  `jdk.ResidentSetSize` and `jdk.NativeMemoryUsageTotal`, which needs `-XX:NativeMemoryTracking=summary`.
- JEP 520 method timing (JDK 25) works for application classes and public JDK API such as
  `java.lang.foreign.SegmentAllocator::allocate` (min, average and max per method, see
  `jfr view method-timing out/<run>.jfr`). It gave no events for `jdk.internal` classes such as
  `jdk.internal.misc.Unsafe::allocateMemory`, and none for `Arena::close` (an interface method
  whose code lives in `jdk.internal`), so `free` cannot be timed on its own. Separate several
  filters with `;`.
- `SegmentAllocator::allocate` also zeroes the memory, so its time is malloc plus memset.
