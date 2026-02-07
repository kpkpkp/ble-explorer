# TAC-Autonomous: Fully Unattended Development & Verification

You are executing a TAC-Autonomous workflow. **You will run to completion without asking any questions.**

## Input
$ARGUMENTS

## Autonomy Rules (MANDATORY)

1. **NEVER ask "should I proceed?"** - Always proceed
2. **NEVER ask "which option?"** - Pick the sensible default, document why
3. **NEVER ask for confirmation** - Make decisions, move forward
4. **On failure:** Retry 2x with different approach, then log and continue
5. **On ambiguity:** Make a decision, document it, proceed
6. **Report results ONLY at END** - No mid-task status questions

## Input Detection

**If input looks like a file path** (contains `/` or ends in `.md`):
1. Read the spec file
2. Extract requirements and success criteria
3. Proceed to TAC execution

**If input is text description:**
1. Parse as task description
2. Infer success criteria (default: builds + tests pass)
3. Proceed to TAC execution

## TAC-Autonomous Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 1: PLAN (You, Opus)                                      │
│  - Parse input (spec file or text)                              │
│  - Identify success criteria / E2E test                         │
│  - Decompose into 1-5 subtasks                                  │
│  - Assign models: opus/sonnet/haiku                             │
│  - ALL decisions made here, none deferred                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 2: EXECUTE (Parallel subagents)                          │
│  - Spawn Task agents with explicit autonomy instruction         │
│  - Each works in isolation (worktree if code changes)           │
│  - Failures don't block siblings                                │
│  - Each agent runs with: "Complete without questions.           │
│    On error: retry twice, then log and continue."               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 3: VERIFY (E2E Test as Oracle)                           │
│  - Run defined E2E test / success criteria                      │
│  - Tests PASS → Proceed to integrate                            │
│  - Tests FAIL → Auto-fix attempt (2x), then report failure      │
│  - NO human approval gate here                                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 4: INTEGRATE & REPORT                                    │
│  - Merge successful changes                                     │
│  - Compile final report                                         │
│  - Present summary to user (first interaction since start)      │
└─────────────────────────────────────────────────────────────────┘
```

## Subagent Spawn Template

When spawning Task agents, ALWAYS include this preamble:

```
AUTONOMOUS MODE: Complete this task without asking questions.
- Make reasonable decisions when facing ambiguity
- On errors: retry twice with different approach, then log "[BLOCKED] reason" and continue
- Do not ask for clarification - infer from context or pick sensible default
- Report completion status, not progress updates
```

## Success Criteria Detection

| Input Contains | Inferred Success Criteria |
|----------------|---------------------------|
| "test" or "verify" | Run specified tests, all pass |
| "build" or "deploy" | Build succeeds, app launches |
| "fix" or "bug" | Specific behavior corrected |
| "monitor" or "watch" | Run for specified duration, report metrics |
| "implement" or "add" | Feature works, tests pass |
| Spec file with `## Success Criteria` | Use those criteria |
| None of above | Project builds successfully |

## Error Handling Matrix

| Error Type | Action |
|------------|--------|
| Build failure | Read error, fix, retry (2x max) |
| Test failure | Read failure, fix code, retry (2x max) |
| Device unreachable | Reconnect attempt, skip device if fails |
| Merge conflict | Auto-resolve if possible, else keep both versions |
| Unknown error | Log `[UNHANDLED] error`, continue with other tasks |
| All retries exhausted | Log `[BLOCKED] task: reason`, proceed to next |

## Final Report Format

```markdown
## TAC-Autonomous Execution Report

**Task:** [Original input]
**Duration:** [Start to end time]
**Status:** [SUCCESS | PARTIAL | FAILED]

### Completed
- [x] Subtask 1: description
- [x] Subtask 2: description

### Blocked (if any)
- [ ] Subtask 3: [BLOCKED] reason after 2 retries

### Verification
- E2E Test: [PASS|FAIL]
- Build: [PASS|FAIL]

### Changes Made
- `file1.dart`: description
- `file2.kt`: description

### Decisions Made Autonomously
1. [Decision]: [Reasoning]
2. [Decision]: [Reasoning]
```

## Example Invocations

```bash
# With spec file
/tac-autonomous specs/feature-string-lights.md

# With inline task
/tac-autonomous "Add retry logic to BLE connections with exponential backoff, verify with 10-minute stability test"

# With bug description
/tac-autonomous "Fix: message board not receiving image data after MTU negotiation"
```

## BEGIN EXECUTION

Parse the input above and execute the TAC-Autonomous flow. Do not ask questions. Report only at completion.
