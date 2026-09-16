package uk.com.acceptancetests.utils;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Selenium waits and actions for Page Object Model {@link WebElement}s.
 *
 * <p><b>Method names and signatures are unchanged from the previous version of this
 * class, so no calling code (step definitions, page objects) needs to change.</b>
 * Only the internals were refactored.</p>
 *
 * <p><b>What changed and why:</b></p>
 * <ul>
 *   <li>Every wait now goes through a single, shared retry loop
 *       ({@link #newWait(WebDriver)}) that automatically ignores the handful of
 *       exceptions Selenium throws when an element simply isn't ready yet
 *       (stale reference, not yet in the DOM, briefly not interactable, or
 *       momentarily covered by another element mid-render). Previously each
 *       method had to remember to catch these itself; a missed catch in one
 *       method was a common source of flaky/inconsistent failures. Centralising
 *       it here means every wait behaves the same way.</li>
 *   <li>The old "stability" and "pointer-event hit-testing" checks (custom
 *       JavaScript modelled on Playwright's actionability engine) have been
 *       removed. They worked, but they added non-obvious browser JavaScript
 *       that most of the team doesn't write day-to-day, and they duplicated
 *       what {@code ElementClickInterceptedException} already tells us for
 *       free. Clicking is now: wait until visible and enabled, attempt a
 *       native click, and if Selenium reports the element was intercepted or
 *       moved, just retry — same outcome, far simpler code.</li>
 *   <li>There is no {@code Thread.sleep(...)} anywhere in this class, and
 *       there should be none in any code that calls it either — every method
 *       here already polls until the condition is true or the timeout is
 *       reached. See WAIT_UTIL_GUIDE.md for the list of methods to reach for
 *       instead of a fixed sleep.</li>
 *   <li>The only two places this class still touches JavaScript are
 *       {@link #forceClick(WebDriver, WebElement)} and
 *       {@link #waitForPageLoadComplete(WebDriver)}. Both are one line of
 *       plain, widely-documented JavaScript (a native click, and a check of
 *       {@code document.readyState}) — not anything Playwright-specific, and
 *       both are explained inline.</li>
 * </ul>
 */
public final class WaitUtilV2 {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);

    private static final System.Logger LOGGER = System.getLogger(WaitUtilV2.class.getName());

    /**
     * Exceptions that are a normal, expected part of polling a live page and must
     * never be allowed to fail a wait outright — they just mean "not ready yet,
     * try again on the next poll". This is the single place that decides which
     * exceptions are safe to retry, so every wait method behaves consistently.
     */
    private static final List<Class<? extends Throwable>> RETRYABLE_UI_EXCEPTIONS = List.of(
            NoSuchElementException.class,
            StaleElementReferenceException.class,
            ElementNotInteractableException.class,
            ElementClickInterceptedException.class);

    private WaitUtilV2() {
    }

    /** Existing compatibility entry point; performs a native actionability-aware click. */
    public static boolean safeClick(WebDriver driver, WebElement element) {
        return waitAndClick(driver, element);
    }

    /**
     * Waits until the element is visible and enabled, then clicks it.
     * If the click is intercepted (something briefly overlapping it, e.g. a
     * loading spinner or animating banner) or the element goes stale mid-click,
     * that is treated as "not ready yet" and retried automatically until the
     * timeout — no custom JavaScript involved.
     */
    public static boolean waitAndClick(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become clickable and accept a native click", ignored -> {
            if (!isActionable(element)) {
                return false;
            }
            element.click();
            return true;
        }, element);
    }

    /**
     * Explicitly bypasses normal actionability checks and clicks via JavaScript.
     * Use only for a documented exception (e.g. an element a real user could
     * click but Selenium's click model can't reach). The script itself is one
     * line: it calls the browser's native {@code element.click()} the same way
     * a page's own JavaScript would, and reports back whether it ran.
     */
    public static boolean forceClick(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return false;
        }
        try {
            Object result = javascriptExecutor.executeScript(
                    "arguments[0].click(); return true;", element);
            return Boolean.TRUE.equals(result);
        } catch (WebDriverException failure) {
            logFailure("forceClick", element, failure);
            return false;
        }
    }

    public static boolean waitForVisible(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become visible", ignored -> isVisible(element), element);
    }

    public static boolean waitForHidden(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become hidden", ignored -> isHidden(element), element);
    }

    /** Naming alias for teams that use Selenium's 'invisible' terminology. */
    public static boolean waitForInvisible(WebDriver driver, WebElement element) {
        return waitForHidden(driver, element);
    }

    public static boolean waitForEnabled(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become enabled", ignored -> isEnabled(element), element);
    }

    public static boolean waitForDisabled(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become disabled", ignored -> isDisabled(element), element);
    }

    public static boolean waitForEditable(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become editable", ignored -> isEditable(element), element);
    }

    public static boolean waitAndSendKeys(WebDriver driver, WebElement element, CharSequence... keys) {
        requireElementArguments(driver, element);
        requireKeys(keys);
        return waitFor(driver, "element to become editable and accept input", ignored -> {
            if (!isEditable(element)) {
                return false;
            }
            element.sendKeys(keys);
            return true;
        }, element);
    }

    public static boolean waitAndClearThenSendKeys(
            WebDriver driver,
            WebElement element,
            CharSequence... keys) {
        requireElementArguments(driver, element);
        requireKeys(keys);
        return waitFor(driver, "element to become editable, clear and accept input", ignored -> {
            if (!isEditable(element)) {
                return false;
            }
            element.clear();
            element.sendKeys(keys);
            return true;
        }, element);
    }

    /** Existing compatibility signature. */
    public static Optional<String> waitForVisibleText(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        try {
            String text = newWait(driver).until(ignored -> nonBlankVisibleText(element));
            return Optional.ofNullable(text);
        } catch (WebDriverException failure) {
            logFailure("waitForVisibleText", element, failure);
            return Optional.empty();
        }
    }

    /** Existing compatibility signature; comparison is case-insensitive. */
    public static boolean waitForTextToContain(
            WebDriver driver,
            WebElement element,
            String expectedText) {
        requireElementArguments(driver, element);
        Objects.requireNonNull(expectedText, "expectedText must not be null");
        return waitFor(driver, "visible text to contain '" + expectedText + "'", ignored -> {
            String actual = nonBlankVisibleText(element);
            return actual != null
                    && actual.toLowerCase(Locale.ROOT).contains(expectedText.toLowerCase(Locale.ROOT));
        }, element);
    }

    public static boolean waitForAttributeToContain(
            WebDriver driver,
            WebElement element,
            String attributeName,
            String expectedFragment) {
        requireElementArguments(driver, element);
        requireNonBlank(attributeName, "attributeName");
        Objects.requireNonNull(expectedFragment, "expectedFragment must not be null");
        return waitFor(driver, "attribute '%s' to contain '%s'".formatted(attributeName, expectedFragment),
                ignored -> {
                    String actual = element.getAttribute(attributeName);
                    return actual != null && actual.contains(expectedFragment);
                }, element);
    }

    public static boolean waitForValueToBe(
            WebDriver driver,
            WebElement element,
            String expectedValue) {
        requireElementArguments(driver, element);
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        return waitFor(driver, "value to equal '" + expectedValue + "'",
                ignored -> expectedValue.equals(element.getAttribute("value")), element);
    }

    public static boolean waitForSelected(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become selected", ignored -> element.isSelected(), element);
    }

    /** Existing driver-level condition signature. */
    public static boolean waitForCondition(
            WebDriver driver,
            String description,
            Function<WebDriver, Boolean> condition) {
        Objects.requireNonNull(driver, "driver must not be null");
        requireNonBlank(description, "description");
        Objects.requireNonNull(condition, "condition must not be null");
        return waitFor(driver, description, condition, null);
    }

    public static boolean waitForTitleToContain(WebDriver driver, String expectedTitle) {
        Objects.requireNonNull(expectedTitle, "expectedTitle must not be null");
        return waitForCondition(driver, "title to contain '" + expectedTitle + "'", currentDriver -> {
            String title = currentDriver.getTitle();
            return title != null && title.contains(expectedTitle);
        });
    }

    public static boolean waitForUrlToContain(WebDriver driver, String expectedFragment) {
        Objects.requireNonNull(expectedFragment, "expectedFragment must not be null");
        return waitForCondition(driver, "URL to contain '" + expectedFragment + "'", currentDriver -> {
            String url = currentDriver.getCurrentUrl();
            return url != null && url.contains(expectedFragment);
        });
    }

    /**
     * Waits for the browser's own page-load flag to report "complete". This is
     * the standard one-line {@code document.readyState} check used across the
     * Selenium ecosystem (not a Playwright concept) — it just asks the browser
     * "have you finished loading this page yet?".
     */
    public static boolean waitForPageLoadComplete(WebDriver driver) {
        return waitForCondition(driver, "document.readyState to become complete", currentDriver -> {
            if (!(currentDriver instanceof JavascriptExecutor javascriptExecutor)) {
                return true;
            }
            return "complete".equals(javascriptExecutor.executeScript("return document.readyState;"));
        });
    }

    /**
     * An element is actionable when a real user could click it: it is on
     * screen, has a real size, and is not disabled. This intentionally does
     * not attempt to detect animation or overlap in JavaScript — if something
     * is briefly covering the element, Selenium itself will throw
     * {@link ElementClickInterceptedException} when the click is attempted,
     * and that is already retried automatically (see {@link #newWait(WebDriver)}).
     */
    private static boolean isActionable(WebElement element) {
        return isVisible(element) && isEnabled(element);
    }

    private static boolean isVisible(WebElement element) {
        try {
            return element.isDisplayed()
                    && element.getRect() != null
                    && element.getRect().getWidth() > 0
                    && element.getRect().getHeight() > 0;
        } catch (NoSuchElementException | StaleElementReferenceException failure) {
            return false;
        }
    }

    private static boolean isHidden(WebElement element) {
        try {
            return !element.isDisplayed();
        } catch (NoSuchElementException | StaleElementReferenceException detached) {
            return true;
        }
    }

    private static boolean isEnabled(WebElement element) {
        try {
            return element.isEnabled()
                    && !"true".equalsIgnoreCase(element.getAttribute("aria-disabled"))
                    && element.getAttribute("disabled") == null;
        } catch (NoSuchElementException | StaleElementReferenceException failure) {
            return false;
        }
    }

    private static boolean isDisabled(WebElement element) {
        try {
            return !element.isEnabled()
                    || "true".equalsIgnoreCase(element.getAttribute("aria-disabled"))
                    || element.getAttribute("disabled") != null;
        } catch (NoSuchElementException | StaleElementReferenceException failure) {
            return false;
        }
    }

    private static boolean isEditable(WebElement element) {
        try {
            String tagName = element.getTagName();
            boolean editableTag = "input".equalsIgnoreCase(tagName)
                    || "textarea".equalsIgnoreCase(tagName)
                    || "true".equalsIgnoreCase(element.getAttribute("contenteditable"));
            return editableTag
                    && isVisible(element)
                    && isEnabled(element)
                    && element.getAttribute("readonly") == null;
        } catch (NoSuchElementException | StaleElementReferenceException failure) {
            return false;
        }
    }

    private static String nonBlankVisibleText(WebElement element) {
        try {
            if (!isVisible(element)) {
                return null;
            }
            String text = element.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            return text.trim();
        } catch (NoSuchElementException | StaleElementReferenceException failure) {
            return null;
        }
    }

    /**
     * Runs {@code condition} against the shared, flake-tolerant wait. Any
     * timeout, or any exception the condition itself throws that isn't in
     * {@link #RETRYABLE_UI_EXCEPTIONS}, is logged with the failing element for
     * context and turned into a plain {@code false} rather than propagating —
     * callers get a clean boolean instead of having to catch Selenium
     * exceptions themselves.
     */
    private static boolean waitFor(
            WebDriver driver,
            String description,
            Function<WebDriver, Boolean> condition,
            WebElement diagnosticElement) {
        try {
            return Boolean.TRUE.equals(newWait(driver).withMessage(description).until(condition));
        } catch (WebDriverException failure) {
            logFailure(description, diagnosticElement, failure);
            return false;
        }
    }

    /**
     * The single wait configuration used by every method in this class. It
     * polls every {@link #DEFAULT_POLL_INTERVAL} up to {@link #DEFAULT_TIMEOUT}
     * and quietly retries the exceptions listed in
     * {@link #RETRYABLE_UI_EXCEPTIONS} instead of failing on the first one —
     * this is what actually prevents most "flaky" failures, since a single
     * mistimed poll no longer fails the whole wait.
     */
    private static Wait<WebDriver> newWait(WebDriver driver) {
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(DEFAULT_TIMEOUT)
                .pollingEvery(DEFAULT_POLL_INTERVAL);
        for (Class<? extends Throwable> retryable : RETRYABLE_UI_EXCEPTIONS) {
            wait.ignoring(retryable);
        }
        return wait;
    }

    private static void requireElementArguments(WebDriver driver, WebElement element) {
        Objects.requireNonNull(driver, "driver must not be null");
        Objects.requireNonNull(element, "element must not be null");
    }

    private static void requireKeys(CharSequence[] keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.length == 0) {
            throw new IllegalArgumentException("at least one key sequence is required");
        }
        for (CharSequence key : keys) {
            Objects.requireNonNull(key, "key sequence must not be null");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void logFailure(String operation, WebElement element, RuntimeException failure) {
        LOGGER.log(System.Logger.Level.WARNING,
                "{0} failed; element={1}; cause={2}: {3}",
                operation,
                element == null ? "n/a" : safely(element::toString),
                failure.getClass().getSimpleName(),
                failure.getMessage());
    }

    private static Object safely(Callable<?> supplier) {
        try {
            return supplier.call();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }
}
