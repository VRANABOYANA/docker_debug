# `WaitUtil` & `AwaitUtil` — Team Guide

The two shared waiting utilities for this test suite, and how to use them
to avoid flaky tests. Read this before writing a new step definition that
waits on anything.

**Package for both:** `a.b.c`

## Contents

- [`WaitUtil` \& `AwaitUtil` — Team Guide](#waitutil--awaitutil--team-guide)
  - [Contents](#contents)
  - [The one-line version](#the-one-line-version)
  - [Why flaky tests happen](#why-flaky-tests-happen)
  - [`WaitUtil` — Selenium / web elements](#waitutil--selenium--web-elements)
    - [Examples](#examples)
    - [Why it doesn't throw](#why-it-doesnt-throw)
    - [A note on `waitAndClick`'s click strategy](#a-note-on-waitandclicks-click-strategy)
  - [`AwaitUtil` — backend / async state](#awaitutil--backend--async-state)
    - [Example — simple eventual consistency](#example--simple-eventual-consistency)
    - [Example — multiple async sources must agree](#example--multiple-async-sources-must-agree)
    - [Example — guarding against a flapping/false-positive read](#example--guarding-against-a-flappingfalse-positive-read)
    - [Example — rich multi-field failure messages](#example--rich-multi-field-failure-messages)
  - [Common mistakes that cause flaky tests](#common-mistakes-that-cause-flaky-tests)
  - [Still flaky after using both classes? Look here next](#still-flaky-after-using-both-classes-look-here-next)
  - [Quick checklist before you write a new wait](#quick-checklist-before-you-write-a-new-wait)

---

## The one-line version

| You are waiting on... | Use |
|---|---|
| A **web element** (button, link, banner text, form field) | [`WaitUtil`](#waitutil--selenium--web-elements) |
| **Browser/page state that isn't a specific element** (page title, URL, page load, window count, alert) | [`WaitUtil`](#waitutil--selenium--web-elements) — `waitForCondition`/`waitForTitleToContain`/`waitForUrlToContain`/`waitForPageLoadComplete` |
| Anything **not driven by Selenium at all** — an API, database, queue, file, async job | [`AwaitUtil`](#awaitutil--backend--async-state) |

If your step touches `WebDriver` or `WebElement` in any way, it's
`WaitUtil` — it has both element-scoped methods (click, read text) and
driver-scoped methods (title, URL, page load, or any custom condition).
`AwaitUtil` is for waits that have nothing to do with the browser at all.

**Never use `Thread.sleep(...)` in test code.** If you're tempted to, one
of these two classes almost certainly already covers your case — see the
[mistakes section](#common-mistakes-that-cause-flaky-tests) for the
sleep-to-method mapping.

---

## Why flaky tests happen

Most flaky UI/API tests fail for the same reason: the test checks something
*before the system has finished doing it*.

- The page has loaded, but the button is still disabled while JS wires up
  its click handler.
- The API returned 200 for your `POST`, but the read replica / search index
  / cache hasn't caught up yet, so the very next `GET` looks stale.
- An async job (email, batch process, projection) has been *triggered* but
  hasn't *finished*.

A fixed `Thread.sleep(2000)` "fixes" this on your laptop and then fails
randomly in CI, because CI is slower and load varies run to run. Polling
with a sensible timeout — what both these classes do — is the actual fix:
try immediately, keep retrying on a short interval, give up only after a
generous timeout, and tell you clearly what you were waiting for when it
does time out.

**Important:** this is the #1 cause of flaky tests, but it is not the
*only* cause. If you've already replaced your sleeps with these classes and
you're still seeing flakiness, don't keep tweaking waits — jump straight to
[Still flaky after using both classes?](#still-flaky-after-using-both-classes-look-here-next)

---

## `WaitUtil` — Selenium / web elements

Handles clicking, reading text, and driver-level conditions, resiliently.
**Never throws** for ordinary timing problems — every method returns
`false` / `Optional.empty()` instead, so you don't need try/catch around
every call.

| Method | Use it when... |
|---|---|
| `waitAndClick(driver, element)` | **Default choice for clicking, for every element type** — buttons, inputs, checkboxes/radios, anchors. Always tries a real native click first (so a genuinely blocked click correctly fails, not silently "succeeds"); only falls back to a JS-forced click when Selenium reports the click was actually intercepted, or for known opacity-hidden elements. Deliberately does **not** require `isDisplayed()` — custom checkbox/radio styling (incl. GOV.UK Design System) commonly keeps the real `<input>` visually hidden behind a styled label, and it's still fully clickable. Anchors get an extra readiness check (`aria-disabled`, CSS `pointer-events`) since HTML has no native "disabled" state for links. Logs how long it took. |
| `safeClick(driver, element)` | You just need a plain native click that waits for `isEnabled()` — no JS fallback. Mostly used internally by `waitAndClick` as a stale-element retry, but fine to call directly for simple cases. |
| `waitForVisibleText(driver, element)` | You need to **read** text off an element (banner, toast, label) — not click it. Returns `Optional<String>`. |
| `waitForTextToContain(driver, element, expected)` | You need to **assert** an element eventually shows expected text (e.g. a `Then` step checking a success banner). |
| `waitForTitleToContain(driver, expected)` | Wait for/assert the **page title** (e.g. after a navigation/redirect) — not scoped to any element. |
| `waitForUrlToContain(driver, expected)` | Wait for/assert the **URL** (e.g. after a client-side route change) — not scoped to any element. |
| `waitForPageLoadComplete(driver)` | Wait for a **navigation to finish loading** (`document.readyState === "complete"`) — the direct replacement for a fixed `sleep(...)` after `navigate()`/`get()`. |
| `waitForCondition(driver, description, condition)` | None of the above fit — any custom driver-level condition (window count, alert present, etc.). Pass your own `Function<WebDriver, Boolean>`. |

### Examples

```java
@When("the user submits the payment")
public void submitPayment() {
    WaitUtil.waitAndClick(driver, submitButton);
}

@Then("a confirmation banner is shown")
public void confirmationBannerShown() {
    boolean shown = WaitUtil.waitForTextToContain(driver, bannerElement, "payment successful");
    assertThat(shown).isTrue();
}
```

**Replacing a fixed sleep after navigation:**

```java
// Before (flaky - fixed guess at how long navigation takes):
Navigation.navigateToURL(startPageURL);
sleep(500);
BrowserDriver.getCurrentDriver().manage().deleteAllCookies();

// After (polls the actual condition):
Navigation.navigateToURL(startPageURL);
WaitUtil.waitForPageLoadComplete(driver);
BrowserDriver.getCurrentDriver().manage().deleteAllCookies();
```

### Why it doesn't throw

`WaitUtil` methods return `false`/`empty` instead of throwing so that a
`When` step (an *action*, like clicking) doesn't itself fail the scenario
with an obscure exception from deep inside a click helper — your `Then`
step's assertion should be what fails, with a clear message.

### A note on `waitAndClick`'s click strategy

`waitAndClick` always tries a **real, native click first**, on every
browser, and only falls back to a JavaScript-forced click when Selenium
reports the click was genuinely blocked (an overlay in the way,
`pointer-events: none`, etc.) or for known opacity-hidden elements. This
means a click that a real user genuinely couldn't perform will now
correctly **fail** the test instead of silently "succeeding" via a JS click
that bypasses those checks.

**If you see a `waitAndClick` failure on a step that used to pass**, that's
very likely surfacing a real UI issue (something visually/logically
blocking the click) — worth investigating the page/element first, rather
than assuming it's the utility being flaky.

---

## `AwaitUtil` — backend / async state

Built on [Awaitility](https://github.com/awaitility/awaitility). Handles
**eventual consistency** — waiting for something to become true on a
system that doesn't update instantly (APIs, databases, replicas, caches,
queues, async jobs). Unlike `WaitUtil`, this **does throw** on timeout,
because it's meant to back `Then` assertions — a timeout here means the
assertion genuinely failed.

| Method | Use it when... |
|---|---|
| `waitUntil(description, condition)` | Simple "wait until this becomes true" — e.g. a job finishes, a record exists. |
| `waitForValue(description, supplier, matcher)` | You want the wait **and** the resulting value, checked against a Hamcrest matcher (e.g. `equalTo(...)`, `hasSize(...)`). |
| `waitUntilConsistentlyTrue(description, condition, holdDuration)` | The value can **flap** — briefly look right then revert (common with load-balanced read replicas / multi-node caches). Requires the condition to hold continuously, not just once. |
| `untilAsserted(description, assertion)` | You're checking **several fields at once** and want a proper assertion failure message (which field, expected vs actual) instead of one generic mismatch. |
| `waitUntilQuietly(description, condition, timeout, pollInterval)` | Rare: you want a `true`/`false` result instead of a thrown exception (e.g. checking something did **not** happen within a window). |

### Example — simple eventual consistency

```java
@Then("the payment status eventually shows as {string}")
public void paymentStatusEventuallyShows(String expectedStatus) {
    AwaitUtil.waitForValue(
            "payment status for " + paymentId,
            () -> paymentApiClient.getStatus(paymentId),
            equalTo(expectedStatus));
}
```

### Example — multiple async sources must agree

```java
AwaitUtil.waitUntil("customer record consistent across DB, index and cache for " + customerId,
        () -> dbRepository.exists(customerId)
                && searchIndexClient.exists(customerId)
                && cacheClient.get(customerId) != null);
```

### Example — guarding against a flapping/false-positive read

```java
AwaitUtil.waitUntilConsistentlyTrue(
        "order status settled for " + orderId,
        () -> orderReadReplica.getStatus(orderId) == OrderStatus.COMPLETE,
        Duration.ofSeconds(5)); // must stay COMPLETE for 5s straight
```

### Example — rich multi-field failure messages

```java
@Then("the projection eventually reflects the update")
public void projectionEventuallyReflectsUpdate() {
    AwaitUtil.untilAsserted("read-model projection for " + aggregateId, () -> {
        var projection = projectionRepository.find(aggregateId);
        assertThat(projection.getStatus()).isEqualTo("UPDATED");
        assertThat(projection.getVersion()).isEqualTo(expectedVersion);
    });
}
```

---

## Common mistakes that cause flaky tests

1. **`Thread.sleep(...)` anywhere in test code.** Map it to the right method
   instead of deleting it blind — ask *what is it actually waiting for*:

   | The sleep was really waiting for... | Replace with |
   |---|---|
   | Page navigation/load to finish | `WaitUtil.waitForPageLoadComplete(driver)` |
   | An element to become clickable/enabled | `WaitUtil.waitAndClick(...)` / `safeClick(...)` |
   | Text/banner to appear | `WaitUtil.waitForVisibleText(...)` / `waitForTextToContain(...)` |
   | Title or URL to change | `WaitUtil.waitForTitleToContain(...)` / `waitForUrlToContain(...)` |
   | Some other driver-level thing (alert, new window) | `WaitUtil.waitForCondition(...)` |
   | A backend/API/DB/queue to reflect a change | `AwaitUtil.waitUntil(...)` / `waitForValue(...)` |
   | Deliberate pacing/rate-limiting (not correctness) | This is one of the few legitimate fixed sleeps — leave it, but comment *why* |

2. **Nesting a poll inside a poll.** Don't wrap an `AwaitUtil.waitUntil(...)`
   around a `WaitUtil` click, or vice versa — each already retries
   internally. Nesting just compounds timeouts (30s × 30s) when something
   is genuinely broken, and CI runs hang instead of failing fast.
3. **Catching and swallowing the timeout exception "just to be safe."**
   If `AwaitUtil` times out, that's a real, useful test failure — let it
   fail. Don't wrap it in try/catch to make a red test go green.
   `waitUntilQuietly` exists for the rare legitimate case; it's not a
   general-purpose "make it stop complaining" tool.
4. **Accepting the first "true" result for something that can flap** (see
   `waitUntilConsistentlyTrue` above). If your API/DB sits behind a
   load-balanced replica set, a single successful poll doesn't always mean
   "done" — it might just mean "one replica out of several has caught up."
5. **Writing a one-off polling loop instead of using these classes.** If
   you find yourself writing a `while` loop with a sleep in it, stop —
   that's exactly what `AwaitUtil`/`WaitUtil` are for. Add a method to the
   shared class instead of duplicating polling logic per test.
6. **No description/alias on the wait.** Always pass a meaningful
   `description` string to `AwaitUtil` methods (e.g. include the entity ID).
   It's what shows up in the failure message and the Cucumber report —
   without it, a timeout just says "condition not met" with zero context
   for whoever picks up the failure next.

---

## Still flaky after using both classes? Look here next

Waits fix exactly one category of flakiness: **timing**. If you've
already replaced sleeps with `WaitUtil`/`AwaitUtil` and you're still seeing
red/green inconsistency, the cause is almost always one of these instead —
and no amount of tweaking a wait will fix them.

**Step 1 — measure before you fix anything.** Run the suite 10-20 times (or
pull recent CI history) and tag each failure by its actual exception type:
`TimeoutException`, `StaleElementReferenceException`,
`ElementClickIntercepted`, an assertion mismatch, `NoSuchElementException`.
Fixing without knowing the distribution of failure types is guesswork.

**Step 2 — capture a screenshot + page source on every failure** if you
don't already. Most "mystery flakiness" becomes obvious the moment you see
the actual DOM state at failure time — a modal was still open, a spinner
was still spinning, the browser was on the wrong page entirely.

**Step 3 — bucket every failure into one of these.** Each has a
*different* fix; `WaitUtil`/`AwaitUtil` only address the first row:

| Category | Symptom | Fix |
|---|---|---|
| **Timing** | Element/value not ready yet | `WaitUtil` / `AwaitUtil` |
| **Test data collisions** | Two tests (or parallel threads) mutate the same record/account | Unique data per run — UUID-suffixed emails, isolated test accounts |
| **Test order dependency** | Test B assumes state left behind by test A | Each scenario sets up its own state; never rely on execution order |
| **Environment/infra flakiness** | Shared test env is slow/unstable, other teams hammering it | Separate/ephemeral env, or retry-at-CI-level for infra-only failures |
| **A real bug in the app** | The "flaky" test is actually catching a real intermittent bug | Fix the app — don't suppress or retry away the test |
| **A bug in the test itself** | A race in the step logic (e.g. reading a value before the triggering action truly completes) | Fix the step, not the wait |

**Step 4 — check test isolation before touching waits again.** This is the
most common root cause once timing is genuinely ruled out. Ask: do
scenarios run in parallel? Do they share a login/account/DB record? Does
test order matter if the suite runs in a different sequence? A perfect
wait cannot fix two tests racing to modify the same customer record.

**Step 5 — quantify the fix, don't vibe-check it.** After fixing a flaky
test, re-run it 20-30 times in isolation before calling it fixed. A test
that fails 1-in-20 will *look* fixed after three clean runs and bite you
again next sprint.

If you're stuck and unsure which bucket a failure belongs to, share the
actual failure message/stack trace/screenshot with the automation
team/channel rather than guessing — a two-minute look at the real evidence
beats another hour tweaking timeouts.

---

## Quick checklist before you write a new wait

- [ ] Is this a `WebElement`/browser condition? → `WaitUtil`. Is it anything else async? → `AwaitUtil`.
- [ ] Did I give it a clear description (for `AwaitUtil`)?
- [ ] Could the value I'm checking flap/revert? → consider `waitUntilConsistentlyTrue`.
- [ ] Am I checking several fields at once? → consider `untilAsserted` for a better failure message.
- [ ] Am I about to write `Thread.sleep`? → don't. Use one of these classes instead.
- [ ] Am I nesting a wait inside another wait? → don't; flatten it into one condition.
- [ ] If it's *still* flaky after all the above → it's probably not a timing problem; see [Still flaky after using both classes?](#still-flaky-after-using-both-classes-look-here-next)

If neither class covers your case, don't invent a custom retry loop in your
step definition — extend `WaitUtil`/`AwaitUtil` instead (and update this
guide); it's cheaper to do it once here than to have the same pattern
copy-pasted across ten step files.
