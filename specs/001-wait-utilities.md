# Specification 001: Shared Wait Utilities

**Status:** Implemented  
**Target:** Java 21, JUnit 6  
**Owners:** Acceptance-test framework team

## Problem

The acceptance pack uses fixed sleeps and locally assembled waits. This produces avoidable delay, inconsistent diagnostics and flaky timing across approximately 4,000 scenarios.

## Required behaviour

1. Existing `WebDriver` + `WebElement` public method signatures remain source compatible.
2. A normal click waits for visible, enabled, stable and pointer-receiving state, then uses a native Selenium click.
3. A normal click must not automatically use JavaScript.
4. Common POM actions support visibility, hidden, enabled, disabled, editable, input, text, attribute, value and selection waits.
5. Driver waits support title, URL, arbitrary conditions and page ready state.
6. Ordinary Selenium timeout/interactability failures return `false` or `Optional.empty()` to preserve the existing compatibility model.
7. Invalid arguments fail fast.
8. Backend/eventual-consistency waits time out by throwing and fail fast on unexpected exceptions.
9. Transient non-UI exceptions are retryable only when their types are explicitly supplied.
10. No Java source contains a fixed sleep.
11. Default Maven tests are deterministic and do not require a browser or external site.
12. An opt-in Maven profile demonstrates the UI behaviour against official GOV.UK-style components.

## Acceptance criteria

- `mvn clean test` passes on JDK 21 and executes the JUnit 6 unit tests.
- `mvn -Pui clean verify` passes when Chrome and the GOV.UK Design System example pages are available.
- Surefire does not execute `*IT` UI tests during the default test phase.
- The POM demo declares elements with `@FindBy WebElement`.
- Static search finds no fixed sleep call in `src/**/*.java`.
- `waitAndClick` contains no path to `forceClick`.
- `AwaitUtil` contains no blanket exception-ignore call.

## Out of scope

- Replacing Selenium with Playwright.
- Converting all POM fields to `By` locators.
- Making a third-party public site an always-on CI dependency.
- Silently bypassing application interaction defects.
