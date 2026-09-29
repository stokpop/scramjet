#!/usr/bin/env bash
# Compare scramjet-service by default, with MALLOC_ARENA_MAX=2, with -XX:TrimNativeHeapInterval=5000 and with both:
# native memory retention, fragmentation and malloc lock contention.
# Usage: experiments/malloc-arena-max/run.sh [phase seconds, default 60]
# Needs: Linux with glibc, Java 25 (java, jcmd, jfr on PATH), python3, curl, a built project (./mvnw package), port 8080 free.
set -euo pipefail

PHASE_SECONDS=${1:-60}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT="$ROOT/experiments/malloc-arena-max/out"
JAR=$(ls "$ROOT"/scramjet-service/target/scramjet-service-*.jar | grep -v original)
LOADGEN="$ROOT/scramjet-loadgen/target/scramjet-loadgen.jar"
mkdir -p "$OUT"

# Number of glibc malloc arenas: 64 MB aligned anonymous reservations (rw part followed by ---p part).
arenas() {
  python3 - "$1" <<'EOF'
import sys
MB64 = 64 * 1024 * 1024
maps = [line.split() for line in open(f"/proc/{sys.argv[1]}/maps")]
count = 0
for a, b in zip(maps, maps[1:]):
    if len(a) == 5 and len(b) == 5 and a[1] == 'rw-p' and b[1] == '---p':
        s1, e1 = (int(x, 16) for x in a[0].split('-'))
        s2, e2 = (int(x, 16) for x in b[0].split('-'))
        if e1 == s2 and e2 - s1 == MB64 and s1 % MB64 == 0:
            count += 1
print(count)
EOF
}

# Sum of voluntary context switches of the Tomcat request threads; a contended malloc lock ends in a futex wait.
worker_switches() {
  local pid=$1 sum=0 task
  for task in /proc/"$pid"/task/*; do
    if [[ $(cat "$task/comm" 2>/dev/null) == http-nio-8080-e* ]]; then
      sum=$((sum + $(awk '/^voluntary_ctxt_switches/{print $2}' "$task/status" 2>/dev/null || echo 0)))
    fi
  done
  echo "$sum"
}

measure() {
  local pid=$1 label=$2 rss vsz nmt
  rss=$(awk '/VmRSS/{print int($2/1024)}' /proc/"$pid"/status)
  vsz=$(awk '/VmSize/{print int($2/1024)}' /proc/"$pid"/status)
  nmt=$(jcmd "$pid" VM.native_memory summary scale=MB | awk -F'committed=' '/^Total/{split($2, a, "MB"); print a[1]}')
  printf "  %-16s RSS=%5s MB  VSZ=%6s MB  NMT=%5s MB  RSS-NMT=%5s MB  arenas=%3s\n" \
    "$label" "$rss" "$vsz" "$nmt" "$((rss - nmt))" "$(arenas "$pid")"
}

# Runs a loadgen scenario and prints the native step latency and the worker context switches it caused.
load() {
  local pid=$1 label=$2; shift 2
  local before after
  before=$(worker_switches "$pid")
  java -jar "$LOADGEN" --duration "${PHASE_SECONDS}s" --timeout 5s "$@" > "$OUT/$NAME-$label.txt"
  after=$(worker_switches "$pid")
  printf "  %-16s %s  worker voluntary context switches: %d\n" "$label" \
    "$(awk '/^(churn|native) /{printf "p50=%s p99=%s max=%s ms", $8, $11, $12}' "$OUT/$NAME-$label.txt")" "$((after - before))"
}

# JEP 520 method timing. jdk.internal classes such as Unsafe cannot be timed, so time the public FFM
# allocation (malloc plus zeroing) and the whole request method (which also frees the memory).
METHOD_TIMING="java.lang.foreign.SegmentAllocator::allocate;nl.stokpop.scramjet.controller.OffHeapMemory::nativeChurn"

# Free memory inside malloc (glibc malloc_info via jcmd): what trimming could give back.
malloc_free() {
  jcmd "$1" System.native_heap_info | python3 -c "
import sys, xml.etree.ElementTree as ET
text = sys.stdin.read()
root = ET.fromstring(text[text.index('<malloc'):text.rindex('</malloc>') + 9])
heaps = root.findall('heap')
free = [t for h in heaps for t in h.findall('total') if t.get('type') in ('fast', 'rest')]
system = sum(int(s.get('size')) for h in heaps for s in h.findall('system') if s.get('type') == 'current')
mb = lambda b: b / 2**20
print('  %-16s %d heaps, %.0f MB from OS, %.0f MB of it free in %d chunks'
      % ('malloc_info', len(heaps), mb(system), mb(sum(int(t.get('size')) for t in free)), sum(int(t.get('count')) for t in free)))"
}

SERVICE_PID=""
trap '[[ -n "$SERVICE_PID" ]] && kill "$SERVICE_PID" 2>/dev/null' EXIT

# run <name> <environment assignment> [extra JVM options...]
run() {
  NAME=$1; local environment=$2; shift 2
  echo "== $NAME =="
  env "$environment" java -Xmx256m -XX:NativeMemoryTracking=summary "$@" \
    "-XX:StartFlightRecording=filename=$OUT/$NAME.jfr,settings=default,jdk.ResidentSetSize#period=1s,jdk.NativeMemoryUsageTotal#period=1s,method-timing=$METHOD_TIMING" \
    -jar "$JAR" > "$OUT/$NAME-service.log" 2>&1 &
  local pid=$!
  SERVICE_PID=$pid
  for _ in $(seq 1 60); do curl -s -o /dev/null localhost:8080/actuator/health && break; sleep 1; done
  echo "  MALLOC_ARENA_MAX=$(tr '\0' '\n' < /proc/$pid/environ | awk -F= '/^MALLOC_ARENA_MAX=/{print $2}')" \
    " TrimNativeHeapInterval=$(jcmd "$pid" VM.flags -all | awk '/TrimNativeHeapInterval/{print $4}') ms"
  sleep 3
  measure "$pid" "baseline"

  load "$pid" "heap-churn" --scenario churn --rate 50
  measure "$pid" "after heap churn"

  # 20 x 64 KB per call, held 100 ms, so many request threads hold native memory at the same time
  load "$pid" "native-retain" --scenario native --rate 100 --native-segments 20 --native-kb 64 --delay-ms 100
  measure "$pid" "after native"

  # many small mallocs per call, nothing held: stresses the arena locks
  load "$pid" "native-contend" --scenario native --rate 200 --native-segments 1000 --native-kb 1 --delay-ms 0
  sleep 2
  measure "$pid" "end"
  jcmd "$pid" JFR.dump name=1 filename="$OUT/$NAME.jfr" > /dev/null

  # Retention vs fragmentation: malloc_trim returns whole free pages; what stays is pinned by live chunks.
  malloc_free "$pid"
  printf "  %-16s %s\n" "malloc_trim" "$(jcmd "$pid" System.trim_native_heap | grep -o 'RSS.*')"
  measure "$pid" "after trim"

  kill "$pid"; wait "$pid" 2>/dev/null || true
  SERVICE_PID=""
}

RUNS=(default arena-max-2 trim-5s arena-max-2-trim-5s)
run default X=1
run arena-max-2 MALLOC_ARENA_MAX=2
run trim-5s X=1 -XX:TrimNativeHeapInterval=5000
run arena-max-2-trim-5s MALLOC_ARENA_MAX=2 -XX:TrimNativeHeapInterval=5000

echo "== JFR: RSS and NMT over time (before the final trim) =="
for NAME in "${RUNS[@]}"; do
  jfr print --json --events jdk.ResidentSetSize,jdk.NativeMemoryUsageTotal "$OUT/$NAME.jfr" | python3 -c "
import json, sys
events = json.load(sys.stdin)['recording']['events']
rss = [e['values']['size'] for e in events if e['type'] == 'jdk.ResidentSetSize']
nmt = [e['values']['committed'] for e in events if e['type'] == 'jdk.NativeMemoryUsageTotal']
mb = lambda b: int(b / 2**20)
print('  %-20s RSS peak %4d MB, end %4d MB | NMT peak %4d MB, end %4d MB | end gap %4d MB'
      % ('$NAME', mb(max(rss)), mb(rss[-1]), mb(max(nmt)), mb(nmt[-1]), mb(rss[-1] - nmt[-1])))"
done
echo "== JFR method timing (JEP 520), whole run =="
for NAME in "${RUNS[@]}"; do
  echo "  $NAME:"
  jfr view method-timing "$OUT/$NAME.jfr" | grep -E "allocate\(long\)|nativeChurn" | sed 's/^/    /'
done
echo "Details per phase in $OUT"
