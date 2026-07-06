package nl.stokpop.scramjet.util;

import java.time.Duration;

public final class Calculator {

    private Calculator() {
    }

    /**
     * Primality test based on https://en.wikipedia.org/wiki/Primality_test#Pseudocode
     * Finding a prime is somewhat slower: the artificial delay is only applied to primes.
     */
    public static boolean isPrime(long n, long artificialDelayMillis) {
        if (n <= 3) {
            return n > 1;
        }
        if (n % 2 == 0 || n % 3 == 0) {
            return false;
        }
        long i = 5;
        while (i * i <= n) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
            i = i + 6;
        }
        Sleeper.sleep(Duration.ofMillis(artificialDelayMillis));
        return true;
    }
}
