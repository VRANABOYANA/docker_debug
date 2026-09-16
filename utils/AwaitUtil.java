package uk.com.acceptancetests.utils;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.ThrowingRunnable;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Waits for asynchronous non-UI state such as API responses, database rows,
 * messages, files and background jobs. WebElement waits belong in {@link WaitUtil}.
 *
 * <p>Unexpected exceptions fail fast. A caller must explicitly name any known,
 * transient exception that is safe to retry.</p>
 */
public final class AwaitUtil {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    private AwaitUtil() {
    }

    public static void waitUntil(String description, Callable<Boolean> condition) {
        waitUntil(description, condition, DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    public static void waitUntil(
            String description,
            Callable<Boolean> condition,
            Duration timeout,
            Duration pollInterval) {
        await(description, condition, timeout, pollInterval).until(condition);
    }

    @SafeVarargs
    public static void waitUntilIgnoring(
            String description,
            Callable<Boolean> condition,
            Class<? extends Throwable>... ignoredExceptions) {
        waitUntilIgnoring(
                description,
                condition,
                DEFAULT_TIMEOUT,
                DEFAULT_POLL_INTERVAL,
                ignoredExceptions);
    }

    @SafeVarargs
    public static void waitUntilIgnoring(
            String description,
            Callable<Boolean> condition,
            Duration timeout,
            Duration pollInterval,
            Class<? extends Throwable>... ignoredExceptions) {
        ConditionFactory factory = await(description, condition, timeout, pollInterval);
        factory = ignoreOnly(factory, ignoredExceptions);
        factory.until(condition);
    }

    public static <T> T waitForValue(
            String description,
            Callable<T> supplier,
            Matcher<? super T> matcher) {
        return waitForValue(description, supplier, matcher, DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    public static <T> T waitForValue(
            String description,
            Callable<T> supplier,
            Matcher<? super T> matcher,
            Duration timeout,
            Duration pollInterval) {
        Objects.requireNonNull(matcher, "matcher must not be null");
        return await(description, supplier, timeout, pollInterval).until(supplier, matcher);
    }

    @SafeVarargs
    public static <T> T waitForValueIgnoring(
            String description,
            Callable<T> supplier,
            Matcher<? super T> matcher,
            Duration timeout,
            Duration pollInterval,
            Class<? extends Throwable>... ignoredExceptions) {
        Objects.requireNonNull(matcher, "matcher must not be null");
        ConditionFactory factory = await(description, supplier, timeout, pollInterval);
        factory = ignoreOnly(factory, ignoredExceptions);
        return factory.until(supplier, matcher);
    }

    public static void waitUntilConsistentlyTrue(
            String description,
            Callable<Boolean> condition,
            Duration holdDuration) {
        waitUntilConsistentlyTrue(
                description,
                condition,
                DEFAULT_TIMEOUT,
                DEFAULT_POLL_INTERVAL,
                holdDuration);
    }

    public static void waitUntilConsistentlyTrue(
            String description,
            Callable<Boolean> condition,
            Duration timeout,
            Duration pollInterval,
            Duration holdDuration) {
        ConditionFactory factory = await(description, condition, timeout, pollInterval);
        requirePositive(holdDuration, "holdDuration");
        if (holdDuration.compareTo(timeout) >= 0) {
            throw new IllegalArgumentException(
                    "holdDuration (%s) must be shorter than timeout (%s)"
                            .formatted(holdDuration, timeout));
        }
        factory.during(holdDuration).until(condition);
    }

    public static void untilAsserted(String description, ThrowingRunnable assertion) {
        untilAsserted(description, assertion, DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    public static void untilAsserted(
            String description,
            ThrowingRunnable assertion,
            Duration timeout,
            Duration pollInterval) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        await(description, assertion, timeout, pollInterval).untilAsserted(assertion);
    }

    public static boolean waitUntilQuietly(
            String description,
            Callable<Boolean> condition,
            Duration timeout,
            Duration pollInterval) {
        try {
            waitUntil(description, condition, timeout, pollInterval);
            return true;
        } catch (ConditionTimeoutException timeoutException) {
            return false;
        }
    }

    private static ConditionFactory await(
            String description,
            Object operation,
            Duration timeout,
            Duration pollInterval) {
        requireArguments(description, operation, timeout, pollInterval);
        return Awaitility.await(description)
                .atMost(timeout)
                .pollInterval(pollInterval);
    }

    @SafeVarargs
    private static ConditionFactory ignoreOnly(
            ConditionFactory factory,
            Class<? extends Throwable>... ignoredExceptions) {
        Objects.requireNonNull(ignoredExceptions, "ignoredExceptions must not be null");
        if (ignoredExceptions.length == 0) {
            throw new IllegalArgumentException("at least one ignored exception type is required");
        }
        ConditionFactory configured = factory;
        for (Class<? extends Throwable> exceptionType : ignoredExceptions) {
            configured = configured.ignoreException(
                    Objects.requireNonNull(exceptionType, "ignored exception type must not be null"));
        }
        return configured;
    }

    private static void requireArguments(
            String description,
            Object operation,
            Duration timeout,
            Duration pollInterval) {
        Objects.requireNonNull(description, "description must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        Objects.requireNonNull(operation, "condition/supplier/assertion must not be null");
        requirePositive(timeout, "timeout");
        requirePositive(pollInterval, "pollInterval");
        if (pollInterval.compareTo(timeout) >= 0) {
            throw new IllegalArgumentException(
                    "pollInterval (%s) must be shorter than timeout (%s)"
                            .formatted(pollInterval, timeout));
        }
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive, was " + duration);
        }
    }
}
