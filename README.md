# Wait Utilities Demo — Java 21 + JUnit 6

A small Maven reference project for replacing fixed sleeps in a Selenium Page Object Model acceptance-test pack.

The project contains two deliberately separate utilities:

- `WaitUtil`: Selenium/WebElement waits and Playwright-inspired actionability.
- `AwaitUtil`: non-UI eventual consistency for APIs, databases, queues, files and background jobs.

## Baseline

- Java 21
- JUnit 6.1.3 (Jupiter)
- Selenium 4.49.0
- Awaitility 4.3.0
- Maven Surefire/Failsafe 3.5.5

## Prerequisites

- JDK 21: `java -version`
- Maven 3.9+: `mvn -version`
- Chrome or Chromium only when running the UI profile
- Internet access only when Maven first resolves dependencies and when the UI profile opens the GOV.UK examples

Selenium Manager, included with Selenium, resolves a compatible Chrome driver automatically in a normal connected environment.

## Commands

Run fast unit tests only:

```bash
mvn clean test
```

Run unit tests plus headless UI tests:

```bash
mvn -Pui clean verify
```

Run the UI tests with a visible browser:

```bash
mvn -Pui -Dui.headless=false clean verify
```

Override the public example host if it is mirrored internally:

```bash
mvn -Pui -Dgovuk.base.url=https://your-internal-mirror.example clean verify
```

## POM usage

Existing PageFactory fields stay unchanged:

```java
@FindBy(id = "continue")
private WebElement continueButton;

public boolean continueJourney() {
    return WaitUtil.waitAndClick(driver, continueButton);
}
```

Input actions wait for an editable element:

```java
WaitUtil.waitAndClearThenSendKeys(driver, nationalInsuranceNumber, "QQ123456C");
```

Backend state uses `AwaitUtil`, not `WaitUtil`:

```java
AwaitUtil.waitUntil(
        "payment reaches COMPLETED",
        () -> paymentClient.status(paymentId) == COMPLETED);
```

Known transient exceptions are explicit:

```java
AwaitUtil.waitUntilIgnoring(
        "projection becomes available",
        () -> repository.find(id).isPresent(),
        TemporaryServiceUnavailableException.class);
```

## Public UI examples

The opt-in tests use official GOV.UK Design System example pages and cover:

- actionable and disabled buttons;
- checkboxes and selected state;
- title, URL and page-load conditions;
- visible error text;
- text input, exact value and CSS-class attributes.

These tests are examples, not a dependency-health guarantee. Keep them outside the default build and mirror the pages internally if CI reliability requires it.

## Important boundaries

- Do not combine implicit waits with these explicit waits. The UI setup sets implicit wait to zero.
- A normal click is always native Selenium. It never hides an overlay or interaction defect with JavaScript.
- `forceClick` is explicit, exceptional and should have a documented reason at its call site.
- PageFactory proxy elements can be resolved again. Avoid manually caching raw elements that the DOM replaces.
- Do not nest `WaitUtil` and `AwaitUtil`; nested polling multiplies timeout duration.

See [DECISIONS.md](DECISIONS.md), [TEST-COVERAGE.md](TEST-COVERAGE.md), and [specs/001-wait-utilities.md](specs/001-wait-utilities.md).
