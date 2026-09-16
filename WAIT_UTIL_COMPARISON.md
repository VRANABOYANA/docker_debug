# WaitUtil vs WaitUtilV1 — Comparison & Recommendation

**Reviewed for:** DWP GYSP acceptance test pack — Java 21, latest Selenium, JUnit 6
**Reviewer note:** Both files were read in full before writing this. Line references match the uploaded files.

---

## 1. TL;DR Recommendation

**Neither class is ready to drop into the framework as-is.** `WaitUtilV1` has the better *design* for a large enterprise pack (bigger API surface, thinner custom code, delegates to Selenium's own `ExpectedConditions`), but it currently **will not compile** and it **drops a security control** (credential redaction) that `WaitUtil` has. `WaitUtil` compiles and is safe to use today, but its public API is narrower and some of it duplicates logic Selenium already provides.

**Recommended path:**
1. Take `WaitUtilV1` as the base (better shape for a big shared pack, easier for the team to extend).
2. Fix the two defects listed in §3 before it goes anywhere near a repo.
3. Port over `WaitUtil`'s password-field redaction and its opacity-forced JS click fallback (real-world escape hatch for `opacity:0`-hidden elements, still available in `V1` only as a *raw* `forceClick` with no opacity handling).
4. Standardise on **one** class name and **one** package-visible entry point — don't ship both into the same test pack.

Do not ship `WaitUtilV1` unmodified. Treat §3 as blocking, not "nice to have."

---

## 2. Side-by-side

| Aspect | `WaitUtil` | `WaitUtilV1` |
|---|---|---|
| Wait engine | `WebDriverWait` | `FluentWait<WebDriver>` |
| Poll interval | 200ms | 100ms |
| Timeout | 30s (fixed) | 30s (fixed, but exposed as `public static final DEFAULT_TIMEOUT`/`DEFAULT_POLL_INTERVAL`) |
| Exceptions silently retried while polling | `NoSuchElementException`, `StaleElementReferenceException` | `NoSuchElementException`, `StaleElementReferenceException`, `ElementNotInteractableException`, `ElementClickInterceptedException` |
| Click strategy | Native click → stale retry → JS click → opacity-forced JS click fallback (handles `opacity:0` hidden elements) | Native click only; intercepted/stale click is treated as "not ready" and re-polled. `forceClick(...)` exists as an explicit, separate public escape hatch (plain JS click, **no** opacity handling) |
| Anchor (`<a>`) disabled detection (`aria-disabled`, `pointer-events:none`) | Yes, built into the click/editable readiness check | Yes, built into `isEnabled`/`isDisabled` |
| Zero-size-but-"displayed" element detection | Not implemented | Implemented in `isVisible()` — a real gap `ExpectedConditions.visibilityOf` misses |
| `waitForHidden` / `waitForInvisible` | Not present | Present, delegates to `ExpectedConditions.invisibilityOf` |
| `waitForEnabled` / `waitForDisabled` / `waitForEditable` | Only used internally, not exposed as separate public methods | Public methods |
| `waitAndClearThenSendKeys` | Not present | Present |
| `waitForAttributeToContain` / `waitForValueToBe` / `waitForSelected` | Not present | Present, delegates to `ExpectedConditions` |
| `waitForVisibleText`, `waitForTextToContain` (case-insensitive) | Present | Present, same behaviour |
| `waitForTitleToContain` / `waitForUrlToContain` | Hand-rolled | Delegates to `ExpectedConditions` |
| `waitForCondition` (arbitrary driver predicate) | Present | Present |
| `waitForPageLoadComplete` | Present | Present, same approach |
| Logging | `System.out.printf`, redacts `type="password"` field text | `System.Logger` (JEP 264, no external dependency, plugs into JUL/whatever bridge the framework wires up) — **but no password redaction** |
| Elapsed-time logging per click | Yes (`waitAndClick` logs ms taken) | Not present |
| Compiles as shipped | **Yes** | **No — see §3.1** |

---

## 3. Defects found (please fix before use)

### 3.1 `WaitUtilV1.java` will not compile — constructor/class name mismatch (blocking)
Line 98:
```java
public final class WaitUtilV1 {
    ...
    private WaitUtil() {
    }
```
A constructor's name must exactly match its enclosing class. This is declared inside `WaitUtilV1` but named `WaitUtil()` — javac will reject it (it's neither a valid constructor nor a valid method, since no return type is given). This looks like a copy-paste from `WaitUtil.java` where only the class name was renamed, not the constructor. **Must be renamed to `private WaitUtilV1() {}`.**

### 3.2 `WaitUtilV1` logs under the wrong class name
Line 84:
```java
private static final System.Logger LOGGER = System.getLogger(WaitUtil.class.getName());
```
This logs everything from `WaitUtilV1` under the logger category `...utils.WaitUtil`. It happens to compile (because `WaitUtil` is a real class in the same package), which is exactly why it's easy to miss in review — but every log line from `WaitUtilV1` will be mis-attributed, which will confuse anyone filtering CI logs by class/category. Should be `System.getLogger(WaitUtilV1.class.getName())`.

### 3.3 `WaitUtilV1` has no credential redaction in logging
`WaitUtil.log(...)` (lines 571–588) explicitly redacts the visible text of any element with `type="password"` before writing it to test output. `WaitUtilV1.logFailure(...)` (lines 451–458) has no equivalent — it doesn't log element *text* at all today, but if anyone later adds `element.getText()` to that log line (a very natural-looking change), a failed wait on a login field could write a password straight into CI logs. On a DWP project this is a real compliance point, not a style nit. **Port the redaction check across regardless of whether text logging is added now.**

### 3.4 `WaitUtilV1` silently drops the opacity-forced click fallback
`WaitUtil.jsClick(...)` handles the common real-world pattern of a clickable element that's deliberately kept at `opacity:0` until some other state is set (common with custom checkboxes/toggles), by temporarily forcing opacity, clicking, then restoring it. `WaitUtilV1.forceClick(...)` is a plain `element.click()` via JS — it does not attempt this. This is a documented, deliberate design decision in `WaitUtilV1`'s javadoc, not a bug, but it means **some tests that currently pass against `WaitUtil` may start failing** if the test pack switches to `WaitUtilV1` unmodified. Confirm with the team whether any current page objects rely on that pattern before cutting over.

---

## 4. Why `WaitUtilV1`'s design is still the better long-term base

- **Smaller surface of custom logic.** Six methods (`waitForHidden`, `waitForAttributeToContain`, `waitForValueToBe`, `waitForSelected`, `waitForTitleToContain`, `waitForUrlToContain`) delegate straight to Selenium's own `ExpectedConditions`, which is maintained upstream and battle-tested across the whole Selenium community — less of "our" code to maintain and debug.
- **Broader out-of-the-box API.** `waitForEnabled`, `waitForDisabled`, `waitForEditable`, `waitForSelected`, `waitForAttributeToContain`, `waitForValueToBe`, `waitAndClearThenSendKeys` are all things a large enterprise pack will need sooner or later (form validation states, checkbox/radio assertions, attribute-driven assertions) — `WaitUtil` doesn't expose these, so teams would end up writing their own ad hoc waits around it, which is exactly the inconsistency a shared util class is meant to prevent.
- **`ElementClickInterceptedException` handled at the wait level, not per-method.** In `WaitUtil`, every click-adjacent method has to individually decide how to react to an intercepted click. In `WaitUtilV1`, it's one line in the shared `RETRYABLE_UI_EXCEPTIONS` list — one place to change behaviour for the whole class, less chance of methods drifting apart over time as the pack grows and multiple people touch it.
- **Zero-size-but-displayed check** (`isVisible`) is a real defect class this class catches that the built-in `ExpectedConditions.visibilityOf` does not — worth keeping regardless of which class wins.

None of this makes `WaitUtil` a bad class — it's smaller, it compiles, and its click-retry/JS-fallback chain is more defensive out of the box. It's a reasonable stopgap if you need something in the repo today while `WaitUtilV1` gets fixed.

---

## 5. Suggested next step

Don't merge these ad hoc in a hurry. Recommended sequence:
1. Fix §3.1 and §3.2 in `WaitUtilV1` (trivial, no behaviour change).
2. Port the password redaction from `WaitUtil` into `WaitUtilV1`'s `logFailure`.
3. Decide, as a team, whether the opacity-forced click fallback is still needed; if yes, add it to `WaitUtilV1.forceClick` (or as a new `forceClickWithOpacityFallback`) rather than silently losing it.
4. Delete/retire the loser so the pack only exports one `WaitUtil*` class — two near-identical utils with overlapping names is itself a source of confusion for a large team.
5. Once settled, publish the companion `WAIT_UTIL_USAGE_GUIDE.md` (provided alongside this doc) so JUnior/Senior automation engineers have one place to look up "which method do I use."
