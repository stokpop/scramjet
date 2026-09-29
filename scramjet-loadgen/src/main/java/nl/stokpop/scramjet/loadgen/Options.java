package nl.stokpop.scramjet.loadgen;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

record Options(
        String baseUrl,
        Duration duration,
        double rate,
        Duration timeout,
        String scenario,
        int delayMillis,
        int matrixSize,
        int churnObjects,
        int leakItems,
        int nativeSegments,
        int nativeKb,
        String report,
        boolean insecure,
        boolean help) {

    static final List<String> SCENARIOS = List.of("basic", "churn", "leak", "native");

    static final String USAGE = """
            Usage: java -jar scramjet-loadgen.jar [options]

              --url <base url>        target, default http://localhost:8080
              --duration <duration>   how long to generate load: 30s, 2m, PT1M or plain seconds, default 30s
              --rate <req/s>          requests started per second, default 10
              --timeout <duration>    per request timeout, default 10s
              --scenario <name>       basic, churn, leak or native, default basic
              --delay-ms <millis>     duration param for /delay calls, default 100
              --matrix-size <n>       matrixSize param for /cpu/magic-identity-check calls, default 100
              --churn-objects <n>     short-lived BigDecimals created per /memory/churn call, default 100000
              --leak-items <n>        music scores retained per /memory/grow call (~1.8 KB each), default 100
              --native-segments <n>   malloc'ed segments per /memory/native/churn call, default 20
              --native-kb <n>         size of each native segment in KB, default 64 (below glibc's 128 KB mmap threshold)
              --report <name>         ascii (report at the end) or live (also a row per second during the run), default ascii
              --insecure              skip TLS certificate and host name verification (test environments only)
              --help                  show this help

            Scenarios, each alternating its calls:
              basic  /delay and /cpu/magic-identity-check
              churn  /memory/churn (high allocation rate, garbage right away) and /delay
              leak   /memory/grow (retained forever, heap grows until OutOfMemoryError) and /delay
              native /memory/native/churn (malloc, hold for --delay-ms, free) and /delay
            The /delay calls show how growing GC pressure hurts otherwise cheap requests.""";

    static Builder builder() {
        return new Builder();
    }

    static Options parse(String... args) {
        Builder builder = builder();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--help") || arg.equals("-h")) {
                builder.help(true);
                continue;
            }
            if (arg.equals("--insecure")) {
                builder.insecure(true);
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--url" -> builder.baseUrl(value);
                case "--duration" -> builder.duration(parseDuration(value));
                case "--rate" -> builder.rate(parsePositiveDouble(arg, value));
                case "--timeout" -> builder.timeout(parseDuration(value));
                case "--scenario" -> builder.scenario(parseChoice("scenario", value, SCENARIOS));
                case "--delay-ms" -> builder.delayMillis(parseNonNegativeInt(arg, value));
                case "--matrix-size" -> builder.matrixSize((int) parsePositiveDouble(arg, value));
                case "--churn-objects" -> builder.churnObjects((int) parsePositiveDouble(arg, value));
                case "--leak-items" -> builder.leakItems((int) parsePositiveDouble(arg, value));
                case "--native-segments" -> builder.nativeSegments((int) parsePositiveDouble(arg, value));
                case "--native-kb" -> builder.nativeKb((int) parsePositiveDouble(arg, value));
                case "--report" -> builder.report(parseChoice("report", value, Report.NAMES));
                default -> throw new IllegalArgumentException("Unknown option " + arg);
            }
        }
        return builder.build();
    }

    static final class Builder {
        private String baseUrl = "http://localhost:8080";
        private Duration duration = Duration.ofSeconds(30);
        private double rate = 10;
        private Duration timeout = Duration.ofSeconds(10);
        private String scenario = "basic";
        private int delayMillis = 100;
        private int matrixSize = 100;
        private int churnObjects = 100_000;
        private int leakItems = 100;
        private int nativeSegments = 20;
        private int nativeKb = 64;
        private String report = "ascii";
        private boolean insecure;
        private boolean help;

        private Builder() {
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return this;
        }

        Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        Builder rate(double rate) {
            this.rate = rate;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        Builder delayMillis(int delayMillis) {
            this.delayMillis = delayMillis;
            return this;
        }

        Builder matrixSize(int matrixSize) {
            this.matrixSize = matrixSize;
            return this;
        }

        Builder churnObjects(int churnObjects) {
            this.churnObjects = churnObjects;
            return this;
        }

        Builder leakItems(int leakItems) {
            this.leakItems = leakItems;
            return this;
        }

        Builder nativeSegments(int nativeSegments) {
            this.nativeSegments = nativeSegments;
            return this;
        }

        Builder nativeKb(int nativeKb) {
            this.nativeKb = nativeKb;
            return this;
        }

        Builder report(String report) {
            this.report = report;
            return this;
        }

        Builder insecure(boolean insecure) {
            this.insecure = insecure;
            return this;
        }

        Builder help(boolean help) {
            this.help = help;
            return this;
        }

        Options build() {
            if (rate <= 0) {
                throw new IllegalArgumentException("rate must be positive: " + rate);
            }
            return new Options(baseUrl, duration, rate, timeout, scenario, delayMillis, matrixSize, churnObjects, leakItems,
                    nativeSegments, nativeKb,
                    report, insecure, help);
        }
    }

    private static String parseChoice(String kind, String value, List<String> choices) {
        String choice = value.toLowerCase(Locale.ROOT);
        if (!choices.contains(choice)) {
            throw new IllegalArgumentException("Unknown " + kind + " " + value + ", choose one of " + choices);
        }
        return choice;
    }

    static Duration parseDuration(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.startsWith("p")) {
                return Duration.parse(v.toUpperCase(Locale.ROOT));
            }
            if (v.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2)));
            }
            if (v.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            if (v.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            if (v.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(v));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Not a valid duration: " + value);
        }
    }

    private static int parseNonNegativeInt(String option, String value) {
        try {
            int i = Integer.parseInt(value);
            if (i < 0) {
                throw new IllegalArgumentException(option + " must not be negative: " + value);
            }
            return i;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " is not a whole number: " + value);
        }
    }

    private static double parsePositiveDouble(String option, String value) {
        try {
            double d = Double.parseDouble(value);
            if (d <= 0) {
                throw new IllegalArgumentException(option + " must be positive: " + value);
            }
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " is not a number: " + value);
        }
    }
}
