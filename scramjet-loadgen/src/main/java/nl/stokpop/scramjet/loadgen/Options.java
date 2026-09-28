package nl.stokpop.scramjet.loadgen;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;

record Options(
        String baseUrl,
        Duration duration,
        double rate,
        Duration timeout,
        int delayMillis,
        int matrixSize,
        boolean insecure,
        boolean help) {

    static final String USAGE = """
            Usage: java -jar scramjet-loadgen.jar [options]

              --url <base url>        target, default http://localhost:8080
              --duration <duration>   how long to generate load: 30s, 2m, PT1M or plain seconds, default 30s
              --rate <req/s>          requests started per second, default 10
              --timeout <duration>    per request timeout, default 10s
              --delay-ms <millis>     duration param for /delay calls, default 100
              --matrix-size <n>       matrixSize param for /cpu/magic-identity-check calls, default 100
              --insecure              skip TLS certificate and host name verification (test environments only)
              --help                  show this help

            Scenario: alternates /delay and /cpu/magic-identity-check calls.""";

    static Options parse(String... args) {
        String baseUrl = "http://localhost:8080";
        Duration duration = Duration.ofSeconds(30);
        double rate = 10;
        Duration timeout = Duration.ofSeconds(10);
        int delayMillis = 100;
        int matrixSize = 100;
        boolean insecure = false;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--help") || arg.equals("-h")) {
                help = true;
                continue;
            }
            if (arg.equals("--insecure")) {
                insecure = true;
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            String value = args[++i];
            switch (arg) {
                case "--url" -> baseUrl = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
                case "--duration" -> duration = parseDuration(value);
                case "--rate" -> rate = parsePositiveDouble(arg, value);
                case "--timeout" -> timeout = parseDuration(value);
                case "--delay-ms" -> delayMillis = (int) parsePositiveDouble(arg, value);
                case "--matrix-size" -> matrixSize = (int) parsePositiveDouble(arg, value);
                default -> throw new IllegalArgumentException("Unknown option " + arg);
            }
        }
        return new Options(baseUrl, duration, rate, timeout, delayMillis, matrixSize, insecure, help);
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
