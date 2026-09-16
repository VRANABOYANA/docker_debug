# Waiting in this framework — WaitUtil & AwaitUtil

This is the one page to read before you write (or fix) a wait in this project.
It assumes no prior knowledge of Playwright or browser JavaScript — you don't
need either to use or extend these classes.

## The one rule

**Never write `Thread.sleep(...)` in a test, step definition, or page object.**

A fixed sleep either wastes time (you waited 5 seconds for something that was
ready in 200ms) or isn't long enough (the one time the environment is slow, the
test fails for no real reason). Every method below polls repeatedly until the
thing you're waiting for actually happens, up to a timeout — so it's both
faster on the good days and more reliable on the bad ones.

If you find a `Thread.sleep(...)` anywhere, replace it using the table below.

| You see this | Replace with |
|---|---|
| Sleep then click | `WaitUtil.waitAndClick(driver, element)` |
| Sleep then type | `WaitUtil.waitAndSendKeys(driver, element, "text")` |
| Sleep, waiting for a spinner/element to disappear | `WaitUtil.waitForHidden(driver, element)` |
| Sleep, waiting for something to appear | `WaitUtil.waitForVisible(driver, element)` |
| Sleep after a page navigation | `WaitUtil.waitForPageLoadComplete(driver)` |
| Sleep waiting for an API/DB/queue result | `AwaitUtil.waitUntil(...)` or `AwaitUtil.waitForValue(...)` |
| Sleep "just in case" with no clear condition | Ask in the team channel — there's almost always a real condition to wait on instead |

## Two classes, two jobs

- **`WaitUtil`** — waits on things on the page: is this button visible, is
  this field editable, click this, type into that. Anything involving a
  `WebElement`.
- **`AwaitUtil`** — waits on things that have nothing to do with the browser:
  an API returning 200, a row appearing in the database, a message landing on
  a queue, a background job finishing.

If your wait involves a `WebElement` or `WebDriver`, use `WaitUtil`.
If it involves an API client, a repository/DAO, or a message consumer, use
`AwaitUtil`. Don't mix them — don't poll a `WebElement` inside `AwaitUtil`, and
don't call an API client inside `WaitUtil`.

## Using WaitUtil

Every method takes the `WebDriver` and the `WebElement` (or the driver alone
for page-level checks like the URL or title) and returns a plain `boolean` —
`true` if the condition happened before the timeout, `false` if it didn't.
Nothing throws an exception for an ordinary timeout; that's a deliberate
choice so your test can assert on the result the same way it asserts on
anything else:

```java
WebElement submitButton = driver.findElement(By.id("submit"));

assertTrue(WaitUtil.waitAndClick(driver, submitButton),
        "Submit button should become clickable");
```

### Method reference

| Method | What it waits for |
|---|---|
| `waitAndClick` / `safeClick` | Element becomes visible + enabled, then clicks it |
| `forceClick` | Bypasses the above and clicks via the browser directly — see "The one exception" below |
| `waitForVisible` | Element is displayed and has a real size |
| `waitForHidden` / `waitForInvisible` | Element is no longer displayed |
| `waitForEnabled` | Element is enabled (not `disabled`, not `aria-disabled="true"`) |
| `waitForDisabled` | The opposite of the above |
| `waitForEditable` | Element is an `input`/`textarea`/`contenteditable`, visible, enabled, and not `readonly` |
| `waitAndSendKeys` | Element becomes editable, then types the given keys |
| `waitAndClearThenSendKeys` | Same, but clears the field first |
| `waitForVisibleText` | Returns the element's trimmed visible text once it's non-blank, as an `Optional<String>` |
| `waitForTextToContain` | Visible text contains the given text (case-insensitive) |
| `waitForAttributeToContain` | A named HTML attribute contains a given fragment |
| `waitForValueToBe` | The element's `value` attribute equals a given string |
| `waitForSelected` | Element (checkbox/radio/option) is selected |
| `waitForCondition` | Escape hatch for a custom driver-level check you write yourself |
| `waitForTitleToContain` | Page title contains the given text |
| `waitForUrlToContain` | Current URL contains the given fragment |
| `waitForPageLoadComplete` | The browser reports the page has finished loading |

All of these use the same default timeout (30 seconds) and poll interval
(100 milliseconds). There are no separate "custom timeout" overloads on
purpose — if you find yourself needing a much longer wait for one specific
case, that's usually a sign the thing you're waiting for should be checked
with `AwaitUtil` instead (e.g. it's really an API/background-job wait wearing
a UI costume), so ask before adding a one-off timeout parameter.

### Why "not ready yet" doesn't fail your test

While Selenium is polling, it can hit ordinary, expected exceptions — the
element went stale because the page re-rendered, it briefly wasn't in the DOM
yet, or a click landed half a pixel too early and got intercepted by
something still animating in. None of these mean the test failed; they mean
"try again on the next poll," and `WaitUtil` already does that for you
automatically. You do not need to write your own `try`/`catch` around a
`WaitUtil` call for these — if you find yourself doing that, you probably
don't need to.

### The one exception: `forceClick`

`waitAndClick` clicks the way a real user would, through Selenium's own click.
Very occasionally an element is genuinely clickable to a human but Selenium's
click model can't reach it (e.g. it's positioned in a way Selenium
mis-measures). For that documented, rare case, `forceClick` clicks the element
directly through the browser instead of through Selenium. Use it only when
`waitAndClick` has a known, explained reason not to work — not as a default
"try this if the first one doesn't work" fallback, because it skips the
visible/enabled checks entirely and can click things a real user couldn't.

### Do I need to know JavaScript to use this class?

No. Two methods (`forceClick` and `waitForPageLoadComplete`) run one line of
JavaScript each internally, but you never write or touch that JavaScript
yourself — you just call the Java method. Both lines are explained in the
code comments in plain English if you're curious, but understanding them is
never required to use `WaitUtil`.

## Using AwaitUtil

Same idea, for anything that isn't a browser element:

```java
AwaitUtil.waitUntil("customer record to appear in the database",
        () -> customerRepository.findById(id).isPresent());

Customer customer = AwaitUtil.waitForValue("customer status to become ACTIVE",
        () -> customerRepository.findById(id).orElseThrow(),
        hasProperty("status", equalTo("ACTIVE")));
```

Unlike `WaitUtil`, `AwaitUtil` throws on timeout by default (a genuinely
unavailable API/DB row usually should fail the test loudly, not silently
return `false`). If you want a non-throwing version, use
`waitUntilQuietly`, which returns a `boolean` the same way `WaitUtil` does.

If your condition can legitimately throw a specific, known exception while
the data isn't ready yet (e.g. a client throws `NotFoundException` until the
record exists), name that exception explicitly with the `*Ignoring` variants
(`waitUntilIgnoring`, `waitForValueIgnoring`). Don't add exceptions to the
ignore list "just in case" — only exceptions you understand and expect.

## Quick troubleshooting

- **Test fails intermittently on a click** → check whether something else on
  the page (a banner, spinner, sticky header) could be covering the element
  right as the click happens. `waitAndClick` already retries this
  automatically, so if it's still failing, the element may need a longer
  real-world settle time — check with the team before reaching for
  `forceClick`.
- **`waitForVisibleText` keeps returning empty** → the element's text is
  either always blank at the point you're checking, or the element itself
  never becomes visible. Confirm with `waitForVisible` first.
- **A wait always times out immediately** → make sure you're not waiting on
  the wrong driver/element (e.g. one captured before a page navigation, now
  stale for the *rest of the test*, not just mid-poll — `WaitUtil` retries
  transient staleness but can't recover an element that's genuinely gone for
  good; re-find it from the page object first).
- **You're tempted to add a `Thread.sleep(...)`** → there's a method above for
  it. If there truly isn't, that's a sign this guide needs a new method, not
  a sign you need a sleep — raise it with the team instead of adding one.
