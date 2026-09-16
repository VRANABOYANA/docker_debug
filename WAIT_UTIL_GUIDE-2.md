# WaitUtil — Usage Guide for Automation Engineers

**Audience:** everyone writing step definitions / page objects in this acceptance test pack, junior or senior.
**Goal:** one place to look up "which method do I use for X", so nobody reaches for `Thread.sleep(...)` again.

> This guide assumes the fixes in `WAIT_UTIL_COMPARISON.md` §3 have been applied and the team has settled on a single class (referred to below as `WaitUtil` — swap in whichever name/package the team lands on).

---

## 1. The one rule

**Never write `Thread.sleep(...)` in a test, step definition, or page object.** Every situation a fixed sleep is used for has a method below that polls the real condition instead — it returns as soon as the page is actually ready, and still waits long enough on a slow page. A fixed sleep is either wasted time (most runs) or not long enough (the run that actually breaks in CI).

If you hit a situation genuinely not covered below, don't reach for `sleep` — ask in the team channel or raise a ticket to extend `WaitUtil`. That's the whole point of a shared class: one fix benefits every test, instead of everyone hand-rolling their own wait.

---

## 2. Quick lookup — "I want to..."

| I want to... | Use this |
|---|---|
| Click a button/link/checkbox and not care about timing | `WaitUtil.waitAndClick(driver, element)` |
| Click, and I've been told this exact element is a known/documented special case Selenium can't click normally | `WaitUtil.forceClick(driver, element)` — see §4 before reaching for this |
| Type into a field | `WaitUtil.waitAndSendKeys(driver, element, "value")` |
| Clear a field first, then type | `WaitUtil.waitAndClearThenSendKeys(driver, element, "value")` |
| Read the visible text of a banner/label/toast | `WaitUtil.waitForVisibleText(driver, element)` → `Optional<String>` |
| Assert a banner/toast eventually contains some text | `WaitUtil.waitForTextToContain(driver, element, "expected")` (case-insensitive) |
| Wait for an element to appear | `WaitUtil.waitForVisible(driver, element)` |
| Wait for an element to disappear (e.g. a spinner) | `WaitUtil.waitForHidden(driver, element)` |
| Wait for a button/field to become enabled | `WaitUtil.waitForEnabled(driver, element)` |
| Wait for a button/field to become disabled | `WaitUtil.waitForDisabled(driver, element)` |
| Wait for a field to become editable | `WaitUtil.waitForEditable(driver, element)` |
| Wait for a checkbox/radio to become selected | `WaitUtil.waitForSelected(driver, element)` |
| Wait for an attribute to contain a value | `WaitUtil.waitForAttributeToContain(driver, element, "class", "active")` |
| Wait for an input's `value` to equal something | `WaitUtil.waitForValueToBe(driver, element, "expected")` |
| Wait for the page title to contain something | `WaitUtil.waitForTitleToContain(driver, "Dashboard")` |
| Wait for the URL to contain a fragment | `WaitUtil.waitForUrlToContain(driver, "/dashboard")` |
| Wait after a `driver.get()`/navigation before doing anything else | `WaitUtil.waitForPageLoadComplete(driver)` |
| Wait for something not covered above (window count, alert present, custom JS condition) | `WaitUtil.waitForCondition(driver, "description for logs", d -> ...)` |

---

## 3. Copy-paste examples

### Cucumber step definitions
```java
@When("I click the continue button")
public void iClickContinue() {
    assertThat(WaitUtil.waitAndClick(driver, continueButton)).isTrue();
}

@When("I enter {string} into the reference field")
public void iEnterReference(String value) {
    assertThat(WaitUtil.waitAndSendKeys(driver, referenceField, value)).isTrue();
}

@Then("the dashboard page eventually loads")
public void dashboardEventuallyLoads() {
    assertThat(WaitUtil.waitForTitleToContain(driver, "Dashboard")).isTrue();
}

@Then("I see a confirmation containing {string}")
public void iSeeConfirmationContaining(String expected) {
    assertThat(WaitUtil.waitForTextToContain(driver, confirmationBanner, expected)).isTrue();
}
```

### Plain JUnit 6 test
```java
@Test
void submittingFormShowsConfirmation() {
    Navigation.navigateToUrl(startPageUrl);
    WaitUtil.waitForPageLoadComplete(driver);

    WaitUtil.waitAndSendKeys(driver, referenceField, "AB123456C");
    WaitUtil.waitAndClick(driver, submitButton);

    assertTrue(WaitUtil.waitForTextToContain(driver, confirmationBanner, "submitted"));
}
```

### Before / after (removing a fixed sleep)
```java
// Before — flaky: either wastes time or isn't long enough
Navigation.navigateToUrl(startPageUrl);
Thread.sleep(500);
driver.manage().deleteAllCookies();

// After — waits only as long as actually needed
Navigation.navigateToUrl(startPageUrl);
WaitUtil.waitForPageLoadComplete(driver);
driver.manage().deleteAllCookies();
```

---

## 4. `forceClick` — read this before using it

`forceClick` bypasses Selenium's normal actionability checks and clicks via raw JavaScript. It exists for one reason: a real, on-screen element that a real user could click, but Selenium's click model can't reach for some documented, understood reason.

**Don't reach for it just because `waitAndClick` returned `false`.** A failed `waitAndClick` almost always means the locator is wrong, the element genuinely isn't ready, or there's a real bug in the page under test — reaching for `forceClick` at that point just hides the real problem from the test, and the next person who touches that test has no idea why it's there. If you think you need it:
1. Confirm in the browser dev tools *why* Selenium can't click it normally.
2. Leave a one-line comment above the `forceClick` call explaining that reason.
3. If you're not sure, ask a senior automation engineer before adding it — this is reviewed harder than a normal `waitAndClick` call in PRs.

---

## 5. Common mistakes to avoid

- **Don't wrap these calls in your own try/catch for Selenium exceptions.** Every method here already swallows ordinary Selenium/timing failures and returns `false` / `Optional.empty()`. If you're catching `WebDriverException` around a `WaitUtil` call, you're catching something that will never be thrown for a timing reason — delete the try/catch and just assert on the boolean.
- **Don't loop-and-retry a `WaitUtil` call yourself.** If `waitAndClick` returns `false` after its own internal retries, calling it again in a `while` loop just re-runs the same 30-second wait — it won't magically succeed. Fix the locator/page instead.
- **Don't pass `null` for driver or element.** These are treated as genuine programming errors (a bug in your test code) and will throw `NullPointerException`/`IllegalArgumentException` immediately rather than returning `false` — that's intentional, so a broken page object fails loudly in CI instead of silently reporting "element not found."
- **Don't log element text yourself for password fields.** If you need to log a field's state for debugging, use `WaitUtil`'s own diagnostic logging (triggered automatically on failure) rather than calling `element.getText()`/`getAttribute("value")` directly in your step — it doesn't know to redact it the way `WaitUtil` does.
- **Don't assume a `false` return means "element not found."** It also covers "found, but never became ready/enabled/visible in time" and "an ordinary Selenium exception occurred." If you need to distinguish these for debugging, check the test/CI log output — every method logs a diagnostic line on failure.

---

## 6. FAQ

**Q: My click keeps returning `false` but the element is clearly on the page.**
A: Check whether it's actually *enabled* and *displayed* — `waitAndClick` won't click a technically-present-but-disabled or zero-size element. If it's an `<a>` tag, also check for `aria-disabled="true"` or `pointer-events: none` in the CSS — both are treated as "not clickable" even though `isEnabled()` alone would say `true`.

**Q: Do I need to change the timeout for a slow page?**
A: Talk to the team first — the shared timeout exists precisely so every wait behaves consistently across the whole pack. A one-off `Thread.sleep` bolted on top of a `WaitUtil` call defeats the point of using it in the first place.

**Q: I need to wait on something not in the table above (e.g. number of open browser windows, an alert being present).**
A: Use `waitForCondition(driver, "description", d -> ...)` — it takes any driver-level predicate. Give it a clear description string; that's what shows up in the failure log.

**Q: Can I use these methods outside of Cucumber steps, e.g. directly in a JUnit 6 `@Test`?**
A: Yes — nothing here is Cucumber-specific. It only needs a `WebDriver` and, for element-scoped methods, a `WebElement`.
