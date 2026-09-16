# Test Coverage

| Capability | Unit test | GOV.UK UI test |
| --- | --- | --- |
| Native actionable click | `WaitUtilTest.nativeClickSucceedsWhenElementIsActionable` | `clicksAnActionableButtonAndChecksPageTitleAndUrl` |
| Existing `safeClick` signature | `existingSafeClickSignatureUsesTheSameNativeAction` | — |
| Visible/non-blank text | `readsTrimmedVisibleText` | `readsAVisibleErrorAndEntersAValue` |
| Editable input and send keys | `clearsAndTypesOnlyWhenEditable` | `readsAVisibleErrorAndEntersAValue` |
| Hidden/disabled states | `detectsDisabledAndHiddenStates` | `recognisesADisabledButton` |
| Value/attribute/selected state | `waitsForExactValueAttributeAndSelectedState` | error-input and checkbox tests |
| Title/URL/page-ready conditions | `waitsForDriverLevelTitleUrlAndPageState` | actionable-button test and every page navigation |
| Explicit forced click | `forceClickIsExplicitAndRequiresJavascriptSupport` | — |
| Invalid caller arguments | `invalidArgumentsFailFast` | — |
| Eventual boolean condition | `AwaitUtilTest.waitsUntilConditionBecomesTrue` | — |
| Eventual value | `returnsTheMatchingValue` | — |
| Retried assertions | `retriesAssertionErrors` | — |
| Unexpected exception propagation | `unexpectedExceptionsFailFast` | — |
| Explicit transient exception | `retriesOnlyAnExplicitlyIgnoredException` | — |
| Quiet timeout | `quietlyReturnsFalseOnlyForTimeout` | — |
| Timing validation | `rejectsBlankDescriptionsAndInvalidTiming` | — |
| Stability window | `verifiesAConditionRemainsStable` | — |

External UI tests demonstrate integration and selector validity. Unit tests own the deterministic behavioural contract.
