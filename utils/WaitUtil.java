package uk.com.acceptancetests.utils;

import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Selenium waits and actions for Page Object Model {@link WebElement}s.
 *
 * <p>The existing WebElement-first signatures are retained. Actions emulate
 * Playwright's useful behaviour: wait for visibility, enabled/editable state,
 * stability and pointer-event reachability, then perform the native Selenium
 * action. Ordinary Selenium timing failures return {@code false} or an empty
 * {@link Optional}. Invalid caller arguments fail immediately.</p>
 *
 * <p>This class never sleeps and never silently falls back to JavaScript.
 * {@link #forceClick(WebDriver, WebElement)} is deliberately explicit.</p>
 */
public final class WaitUtil {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);

    private static final System.Logger LOGGER = System.getLogger(WaitUtil.class.getName());

    private WaitUtil() {
    }

    /** Existing compatibility entry point; performs a native actionability-aware click. */
    public static boolean safeClick(WebDriver driver, WebElement element) {
        return waitAndClick(driver, element);
    }

    /** Waits until actionable and retries a native Selenium click until success or timeout. */
    public static boolean waitAndClick(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        return waitFor(driver, "element to become actionable and accept a native click", ignored -> {
            try {
                if (!isActionable(driver, element)) {
                    return false;
                }
                element.click();
                return true;
            } catch (NoSuchElementException
                     | StaleElementReferenceException
                     | ElementNotInteractableException transientFailure) {
                return false;
            }
        }, element);
    }

    /** Explicitly bypasses normal actionability checks. Use only for a documented exception. */
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
            try {
                if (!isEditable(element)) {
                    return false;
                }
                element.sendKeys(keys);
                return true;
            } catch (NoSuchElementException | StaleElementReferenceException | ElementNotInteractableException failure) {
                return false;
            }
        }, element);
    }

    public static boolean waitAndClearThenSendKeys(
            WebDriver driver,
            WebElement element,
            CharSequence... keys) {
        requireElementArguments(driver, element);
        requireKeys(keys);
        return waitFor(driver, "element to become editable, clear and accept input", ignored -> {
            try {
                if (!isEditable(element)) {
                    return false;
                }
                element.clear();
                element.sendKeys(keys);
                return true;
            } catch (NoSuchElementException | StaleElementReferenceException | ElementNotInteractableException failure) {
                return false;
            }
        }, element);
    }

    /** Existing compatibility signature. */
    public static Optional<String> waitForVisibleText(WebDriver driver, WebElement element) {
        requireElementArguments(driver, element);
        try {
            String text = newWait(driver).until(ignored -> nonBlankVisibleText(element));
            return Optional.of(text);
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

    public static boolean waitForPageLoadComplete(WebDriver driver) {
        return waitForCondition(driver, "document.readyState to become complete", currentDriver -> {
            if (!(currentDriver instanceof JavascriptExecutor javascriptExecutor)) {
                return true;
            }
            return "complete".equals(javascriptExecutor.executeScript("return document.readyState;"));
        });
    }

    private static boolean isActionable(WebDriver driver, WebElement element) {
        return isVisible(element)
                && isEnabled(element)
                && isStable(driver, element)
                && receivesPointerEvents(driver, element);
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

    private static boolean isStable(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return true;
        }
        Object stable = javascriptExecutor.executeAsyncScript("""
                const element = arguments[0];
                const done = arguments[arguments.length - 1];
                if (!element || !element.isConnected) { done(false); return; }
                const first = element.getBoundingClientRect();
                requestAnimationFrame(() => requestAnimationFrame(() => {
                  if (!element.isConnected) { done(false); return; }
                  const second = element.getBoundingClientRect();
                  done(first.x === second.x && first.y === second.y &&
                       first.width === second.width && first.height === second.height);
                }));
                """, element);
        return Boolean.TRUE.equals(stable);
    }

    private static boolean receivesPointerEvents(WebDriver driver, WebElement element) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return true;
        }
        Object receivesEvents = javascriptExecutor.executeScript("""
                const element = arguments[0];
                if (!element || !element.isConnected) return false;
                if (getComputedStyle(element).pointerEvents === 'none') return false;
                const rect = element.getBoundingClientRect();
                const x = rect.left + rect.width / 2;
                const y = rect.top + rect.height / 2;
                const hit = document.elementFromPoint(x, y);
                return hit === element || (hit !== null && element.contains(hit));
                """, element);
        return Boolean.TRUE.equals(receivesEvents);
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

    private static WebDriverWait newWait(WebDriver driver) {
        WebDriverWait webDriverWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        webDriverWait.pollingEvery(DEFAULT_POLL_INTERVAL);
        return webDriverWait;

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
