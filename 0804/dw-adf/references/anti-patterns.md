# Anti-patterns

Each entry states the unsafe design, when it bites, the invariant it breaks, the observable failure,
the supported alternative, the protecting evaluation, and its scope. Scope for every entry below:
DataWedge 15.0 reviewed, `INTENT` output plug-in, Android host app. An exclusion sends you to a
different skill; these are unsafe designs *inside* this skill's capability.

## AP-1 — Pushing ADF config with `RESET_CONFIG` = `true`

- **Tempting design.** Use the default `RESET_CONFIG` (`true`) so the push "starts clean".
- **Exposure.** Any profile that already has ADF rules — including rules another team, an MX
  configuration, or the DataWedge UI created.
- **Invariant violated.** Ownership. Your app owns *its* rule, not the profile's whole rule set.
- **Observable failure.** Sibling rules disappear. `SET_CONFIG` reports success, so nothing looks
  wrong until a different workflow's scans stop being formatted. No error is emitted.
- **Supported alternative.** `CONFIG_MODE` = `UPDATE` with `RESET_CONFIG` = `false`, which merges the
  existing configuration with the change. Read back first so you know what was there.
- **Protecting evaluation.** `adf-antipattern-reset-config-wipe`.
- **Exception — read AP-11.** `RESET_CONFIG` = `true` is *required* when rule order matters, because
  merging cannot reposition a rule. The rule is not "never use `true`"; it is "use `true` only when
  you exclusively own the profile and re-supply every rule you need, in order."

## AP-2 — Two components owning profile and ADF state

- **Tempting design.** An Activity configures ADF on resume, and a Service (or a second fragment,
  or a view model) also pushes config so "whichever runs first wins".
- **Exposure.** Rapid navigation, configuration change, or a service starting while the Activity is
  mid-transition. Both owners have an in-flight `SET_CONFIG`.
- **Invariant violated.** Single ownership and serialized transitions.
- **Observable failure.** Interleaved commands; the last writer silently wins. Because DataWedge does
  not queue API commands, one of the two pushes may simply be dropped, so the surviving
  configuration is nondeterministic across runs.
- **Supported alternative.** Exactly one `AdfIntentOutputConfigurator` per profile per process,
  enforced by the `LIVE_OWNERS` check in its `init` block, with all results funnelled through
  `DwResultRouter`'s single owner slot.
- **Protecting evaluation.** `adf-antipattern-competing-owner`.

## AP-3 — Registering a second receiver for `RESULT_ACTION`

- **Tempting design.** Each component that sends a DataWedge command registers its own result
  receiver so it can "see its own results".
- **Exposure.** Any process with more than one DataWedge caller.
- **Invariant violated.** Non-overlapping receiver registration; unambiguous result routing.
- **Observable failure.** Duplicate handling of one result: two readbacks, two retries, doubled state
  transitions. With `COMMAND_IDENTIFIER` unchecked, one component consumes the other's result and
  reports success for a command it never sent.
- **Supported alternative.** One registration per process, forwarded via `DwResultRouter.route()`;
  every handler compares `COMMAND_IDENTIFIER` against its own in-flight token and drops
  non-matching, duplicate, and late results.
- **Protecting evaluation.** `adf-antipattern-competing-owner`.

## AP-4 — Retrying from only one readiness edge (one-sided retry)

- **Tempting design.** Re-apply configuration only when a result arrives — or only at
  registration — because "one of them always happens last".
- **Exposure.** R2 (receiver registered) and R4 (ADF applied) complete independently. A `SET_CONFIG`
  result can be broadcast before the host finishes registering, or DataWedge can be slow and answer
  long after registration.
- **Invariant violated.** Every readiness source that may complete last must wake the same
  idempotent reconciliation path.
- **Observable failure.** A missed wakeup: the app waits forever in "applying", with no error. It
  reproduces on roughly one cold start in N and looks like a flake.
- **Supported alternative.** `reconcileAdfState()` is called from `onHostStart()`,
  `onDataWedgeResult()`, the readback handler, and the timeout runnable. All four edges, one function,
  re-entrancy guarded.
- **Protecting evaluations.** `adf-async-completion-order`, `adf-host-contract-result-receiver`.

## AP-5 — Trusting a cached "already configured" flag

- **Tempting design.** Persist `adfConfigured = true` after the first success and skip configuration
  on later launches.
- **Exposure.** DataWedge upgraded or data-cleared, profile edited in the UI, an MX configuration
  applied, another app taking the profile, factory reset of DataWedge settings.
- **Invariant violated.** Freshness. A cached value does not satisfy a contract that requires a fresh
  synchronization signal.
- **Observable failure.** The app believes formatting is active while raw data flows. Downstream
  parsing fails on data that "cannot" be in that format.
- **Supported alternative.** Treat `GET_CONFIG` + `PROCESS_PLUGIN_NAME` readback as the only proof.
  `verifiedByReadback` is per-session state, reset on every invalidation signal — never persisted.
- **Protecting evaluation.** `adf-freshness-getconfig-readback`.

## AP-6 — Firing `SET_CONFIG` and assuming it landed

- **Tempting design.** Broadcast the config and continue, or omit `SEND_RESULT` entirely.
- **Exposure.** DataWedge does not queue API commands; a command sent while DataWedge is busy may be
  ignored, and Zebra recommends delay code before critical commands.
- **Invariant violated.** Acknowledgement before dependent work.
- **Observable failure.** Silent no-op. Scans arrive unformatted with no error anywhere.
- **Supported alternative.** Always set `SEND_RESULT` and `COMMAND_IDENTIFIER`; keep one command in
  flight; arm a timeout that routes into `reconcileAdfState()`; cap attempts and then report a
  blocking reason instead of retrying forever.
- **Protecting evaluations.** `adf-async-completion-order`, `adf-host-contract-result-receiver`.

## AP-7 — An action list with no terminal send

- **Tempting design.** Add only the transformation (`SKIP_AHEAD`, `REPLACE_STRING`, `SEND_NEXT n`)
  and assume the rest of the data follows automatically.
- **Exposure.** Every rule whose final action does not emit the remainder.
- **Invariant violated.** ADF actions execute top to bottom and emit only what they are told to emit.
- **Observable failure.** Truncated payloads — the most common "ADF ate my data" report. Note that a
  profile created with *no* rule instead gets an automatic `Rule0` with a single `SEND_REMAINING`,
  which is why "no rule" looks healthier than "a bad rule".
- **Supported alternative.** End the list with `SEND_REMAINING` unless truncation is the goal.
  `AdfRule.emitsRemainder` flags this at construction time and logs a warning.
- **Protecting evaluation.** `adf-rule-replace-string-bundle` (asserts the terminal send) and fixture
  Case 5.

## AP-8 — Retrying a terminal result code

- **Tempting design.** One generic retry loop for every failed `SET_CONFIG`.
- **Exposure.** `RESULT_ACTION_RESULT_CODE_EMPTY_RULE_NAME`, `UNLICENSED_FEATURE`,
  `OPERATION_NOT_ALLOWED`, `APP_ALREADY_ASSOCIATED`, `PLUGIN_NOT_SUPPORTED`.
- **Invariant violated.** Retry is for transient faults only.
- **Observable failure.** A broadcast loop that never converges, burning battery and flooding logs
  while the real defect (an empty rule name, a missing license, a protected profile) stays hidden.
- **Supported alternative.** `Dw.TERMINAL_RESULT_CODES` short-circuits into
  `AdfState.blockedReason`; the host surfaces it and stops.
- **Protecting evaluation.** `adf-unknown-rfid-source` (licensing stop) and
  `adf-host-contract-result-receiver`.

## AP-9 — Assuming ADF configured once applies to every output plug-in

- **Tempting design.** Configure ADF for `KEYSTROKE` (or omit `OUTPUT_PLUGIN_NAME`) and expect intent
  output to be formatted too.
- **Exposure.** Any profile with more than one output plug-in, and any config that omits the binding
  key.
- **Invariant violated.** ADF binding is per output plug-in.
- **Observable failure.** Keystroke output is formatted while intent output is not, or the config
  appears accepted but binds to nothing.
- **Supported alternative.** Always send `OUTPUT_PLUGIN_NAME` = `INTENT`, and read it back with the
  same value.
- **Protecting evaluation.** `adf-rule-replace-string-bundle`.

## AP-10 — Adding an ADF rule before the intent route works

- **Tempting design.** Configure the profile, intent output, and ADF in one push, then debug whatever
  arrives.
- **Exposure.** New integrations, or a changed `intent_action` / `intent_delivery`.
- **Invariant violated.** Change one variable at a time.
- **Observable failure.** No data arrives, and a missing intent route is indistinguishable from a
  rule that consumed everything. Debugging time goes to the wrong layer.
- **Supported alternative.** Establish the baseline first — scans reach the host **unformatted** —
  then add the rule. See the prerequisite table in [implementation.md](implementation.md).
- **Protecting evaluation.** `adf-criteria-decoder-scoped-rule` (requires a control input).

## AP-11 — Merging a scoped rule behind an unconditional one

- **Tempting design.** Add a length- or content-scoped rule with `RESET_CONFIG` = `false`, per AP-1's
  advice to preserve siblings, and assume the more specific rule wins.
- **Exposure.** Any profile that already holds an unconditional rule — which is *every* profile
  DataWedge auto-provisions, because a profile with no rules gets `Rule0`: no criteria, single
  `SEND_REMAINING`, positioned first.
- **Invariant violated.** Rule evaluation is ordered and **the first match wins**. Specificity does
  not confer priority, and merging appends rather than reorders.
- **Observable failure.** Silent no-op. `SET_CONFIG` returns `SUCCESS`, a readback confirms the rule
  exists, and scans still arrive completely untransformed. Confirmed on DataWedge 15.0.73:
  `Readback rules for ADF: [Rule0, Ean13ThirdFirstThenReversed]` → a 13-character scan came through
  unmodified. Every layer reports success while nothing happens.
- **Supported alternative.** Send `RESET_CONFIG` = `true` with `PARAM_LIST` carrying every rule in
  evaluation order, most specific first, catch-all passthrough last. Re-supply a passthrough yourself:
  once you clear `Rule0`, non-matching input has no rule at all. Then confirm the order by readback —
  `PARAM_LIST` order *is* evaluation order.
- **Protecting evaluation.** `adf-antipattern-rule-order-shadowing`.
- **Scope.** DataWedge 15.0.73 device-verified, TC701, Android 15.

## AP-12 — Enforcing single ownership by throwing from a constructor

- **Tempting design.** Guard the one-owner-per-profile invariant with a process-wide claim set and
  `check(...)`/`throw` in the configurator's constructor, releasing the claim only when the host is
  `isFinishing`.
- **Exposure.** Ordinary Android lifecycle. On activity recreation — rotation, configuration change,
  process-level restart — the framework constructs the new instance **before** destroying the old one,
  so an overlapping claim is normal and transient.
- **Invariant violated.** None, ironically: the ownership invariant is correct. What breaks is the
  enforcement contract — a lifecycle event that the platform guarantees must not be fatal.
- **Observable failure.** Hard crash on a routine event. Confirmed on device:
  `IllegalStateException: An AdfIntentOutputConfigurator already owns profile '<name>'` thrown from
  `<init>` via `MainActivity.onCreate`, then `Force finishing activity` and process death. The guard
  meant to prevent a subtle bug caused a total one.
- **Supported alternative.** Enforce ownership with a **per-profile registry** that returns the
  existing instance (`forProfile(...)`) instead of throwing. The host attaches a settable
  `stateListener` on start and clears it on destroy so a process-scoped owner never retains a
  destroyed Activity. Recreation then reattaches to the same owner and keeps the readiness state it
  had already reached.
- **Protecting evaluation.** `adf-antipattern-ownership-crash`.
- **Scope.** DataWedge-independent; Android lifecycle behaviour, verified on Android 15.

## AP-13 — Treating a non-empty `RESULT_INFO` as failure

- **Tempting design.** Accept a command only when `RESULT == "SUCCESS"` **and** `RESULT_INFO` is
  empty, reasoning that `RESULT_INFO` exists to report problems.
- **Exposure.** Every successful command on DataWedge 15.0.73, which returns
  `{PROFILE_NAME=<profile>}` alongside `RESULT=SUCCESS`.
- **Invariant violated.** `RESULT` is the outcome channel; `RESULT_INFO` is detail. Conflating them
  inverts the success test.
- **Observable failure.** Every success reads as a failure, the retry path runs to exhaustion, and the
  host reports a blocking error while the configuration was in fact applied. Observed as
  `blocked=No DataWedge result after 3 attempts` immediately after three `RESULT=SUCCESS` results.
- **Supported alternative.** Test `RESULT == "SUCCESS"` alone; check `RESULT_INFO` *values* against
  the terminal result codes. And never treat acceptance as creation — only a readback proves that.
- **Protecting evaluation.** `adf-antipattern-result-info-success`.
- **Scope.** DataWedge 15.0.73 device-verified.

## AP-14 — An unbounded verification retry loop

- **Tempting design.** Cap retries on the write path, then loop "until the readback confirms the
  rule", since a readback is cheap and idempotent.
- **Exposure.** Any readback whose shape the parser does not recognise — including the documented-vs-
  actual `PARAM_LIST` mismatch. The write succeeded, so the write-path cap never engages.
- **Invariant violated.** Every retry path needs a bound and a terminal state.
- **Observable failure.** A broadcast storm. Observed on device: `GET_CONFIG` re-sent 12+ times in
  under 5 seconds, interleaved with timeout warnings, with no convergence and no error surfaced.
- **Supported alternative.** Bound the verification path with its own counter and fail into an
  explicit `blockedReason` when exhausted. Report "could not confirm" rather than retrying forever —
  an unconfirmable rule is a real outcome the host must see.
- **Protecting evaluation.** `adf-antipattern-unbounded-readback`.
- **Scope.** DataWedge 15.0.73 device-verified.
