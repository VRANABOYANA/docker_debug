# Agent Instructions: Specification-Driven Development

These rules apply to every automated change in this repository.

## Source of truth

1. Read the relevant file in `specs/` before editing production code.
2. If requested behaviour is absent or ambiguous, update or add a numbered specification first.
3. A specification must state the problem, required behaviour, measurable acceptance criteria and out-of-scope items.
4. Do not infer a breaking API change from an implementation preference. Record it as an explicit decision requiring owner approval.

## Required workflow

1. Translate the request into a specification delta.
2. Identify compatibility, reliability, diagnostic and suite-runtime risks.
3. Add or update deterministic tests that express the acceptance criteria.
4. Implement the smallest production change that satisfies those tests.
5. Run the narrowest relevant test, then `mvn clean test`.
6. Run `mvn -Pui clean verify` only when UI behaviour/selectors changed and the browser/public fixture is available.
7. Search Java sources for prohibited fixed sleeps and blanket exception swallowing.
8. Update README, coverage mapping and ADRs when commands, contracts or trade-offs change.
9. Report what was verified and any verification that could not be run.

## Compatibility contract

- Java source and target release remain 21.
- Tests use JUnit 6 Jupiter; do not introduce JUnit 4 or Vintage.
- `WebDriver + WebElement` is the first-class POM API.
- Preserve existing public method names, parameter order and return types unless a specification explicitly authorises a breaking release.
- New overloads must be unambiguous and must not make existing calls compile differently.
- Page objects own element definitions. Step definitions call page behaviours, not utility internals.

## Waiting rules

- UI/DOM state uses `WaitUtil` and Selenium explicit waits.
- API, database, queue, file and background-job state uses `AwaitUtil` and Awaitility.
- Never nest `WaitUtil` and `AwaitUtil` polling loops.
- Never add a fixed sleep as a synchronisation strategy.
- Never combine implicit waits with these explicit waits.
- Native click is the default contract. JavaScript click must remain explicit, rare and documented.
- Default Awaitility operations fail fast on unexpected exceptions. Ignore only named, known-transient types.
- A timeout must explain the intended condition; descriptions may not be blank.

## Test design

- Unit tests must not require network access, a real browser, wall-clock seconds or test-order dependence.
- UI tests use the `*IT` suffix and run only through the `ui` Maven profile.
- Test observable behaviour, not private implementation details.
- Cover success, retry, timeout/failure semantics, invalid input and exception policy when changing a wait primitive.
- Do not weaken assertions or extend timeouts merely to make a flaky test pass; diagnose the missing condition.

## Definition of done

A change is complete only when:

- the specification and acceptance criteria are current;
- compatibility impact is stated;
- relevant JUnit 6 tests pass on Java 21;
- no prohibited wait pattern was introduced;
- default and UI-profile boundaries remain intact;
- documentation and decision records match the delivered behaviour.
