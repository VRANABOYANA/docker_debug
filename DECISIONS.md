# Engineering Decisions

## ADR-001: Java 21 and JUnit 6

**Status:** Accepted

The Maven compiler uses `release=21`. JUnit artifacts are aligned through the JUnit 6.1.3 BOM. Surefire and Failsafe are both 3.5.5, above JUnit 6's minimum supported 3.0.0 line.

**Reason:** one explicit runtime/toolchain contract prevents developer and CI drift. The BOM prevents mixed JUnit Platform/Jupiter versions.

## ADR-002: Keep WebElement as the first-class POM API

**Status:** Accepted

Existing public signatures such as `waitAndClick(WebDriver, WebElement)` remain intact.

**Reason:** the established Page Object Model and its approximately 4,000 scenarios should not be rewritten merely to adopt better wait behaviour. Intelligence belongs beneath the stable page-object API.

**Constraint:** use PageFactory/proxied elements for DOM nodes that may be replaced. A manually cached raw `WebElement` cannot always recover from replacement.

## ADR-003: Copy Playwright behaviour, not its API

**Status:** Accepted

Before clicking, `WaitUtil` checks visibility, enabled/ARIA state, element stability and whether the centre point receives pointer events. Transient Selenium interaction failures are polled until timeout.

**Reason:** page-object callers should state the intended action rather than assemble low-level waits at every call site.

## ADR-004: Never auto-escalate to JavaScript click

**Status:** Accepted

`waitAndClick` performs only a native Selenium click. `forceClick` is a separately named method.

**Reason:** automatic JavaScript fallback can bypass overlays, disabled states and real usability defects, producing false-green acceptance tests.

## ADR-005: Separate UI and non-UI waiting

**Status:** Accepted

- DOM/browser conditions use `WaitUtil` and `WebDriverWait`.
- API/DB/queue/file/background-job conditions use `AwaitUtil` and Awaitility.

**Reason:** each mechanism has different failure semantics and retryable exception types. Nesting them compounds timeouts and weakens diagnostics.

## ADR-006: Fail fast on unexpected Awaitility exceptions

**Status:** Accepted

Default `AwaitUtil` methods do not call blanket `ignoreExceptions()`. Opt-in methods require explicit exception classes.

**Reason:** a programming error such as `NullPointerException` must not be disguised as a 30-second timeout.

## ADR-007: Public-site tests are opt-in

**Status:** Accepted

`mvn test` runs unit tests. `mvn -Pui verify` additionally runs `*IT` classes against official GOV.UK Design System pages.

**Reason:** a public dependency must not make every normal build flaky. A production organisation should mirror or own its acceptance fixtures.

## ADR-008: Fixed sleeps are prohibited

**Status:** Accepted

Production and test Java code must wait for observable evidence. A fixed delay is allowed only for a documented protocol/rate-limit requirement that has no observable condition.

**Reason:** fixed delays waste time on fast systems and remain flaky on slow systems.
