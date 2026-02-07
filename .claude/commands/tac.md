# TAC (Tactical Agentic Coding) Multi-Agent Orchestration

You are initiating a TAC workflow - out-of-loop isolated development with E2E verification.

## TAC Pattern
```
OPUS (Plan) → Worktrees (Sonnet/Haiku execute in parallel) → OPUS (Integrate & Verify)
```

## Arguments
$ARGUMENTS

## Your Role: Orchestrator (Opus)

### If given a spec file path:
1. Read the spec file
2. Analyze requirements and identify parallel work streams
3. Break into 2-5 independent subtasks
4. For each subtask, assign appropriate model:
   - **opus**: Complex architecture, security-critical, integration
   - **sonnet**: Standard implementation, moderate complexity
   - **haiku**: Simple tasks, boilerplate, tests, documentation

### If given a task description:
1. Analyze the task
2. Determine if it needs decomposition or can be done directly
3. If complex: create subtasks as above
4. If simple: execute directly with appropriate model

## Output Format

After analysis, output a TAC execution plan:

```
## TAC Execution Plan

### Task: [Brief description]

### Decomposition:
| Subtask | Model | Description | Dependencies |
|---------|-------|-------------|--------------|
| subtask-1 | sonnet | ... | none |
| subtask-2 | haiku | ... | none |
| subtask-3 | sonnet | ... | subtask-1 |

### Execution Strategy:
- [ ] Create worktrees for isolated work
- [ ] Execute parallel subtasks
- [ ] Integration review (Opus)
- [ ] E2E verification
- [ ] Merge best results

### Estimated Approach:
[Brief explanation of how subtasks will be coordinated]
```

## Execution Commands

To run TAC orchestration programmatically:
```bash
# With a spec file
python adws/adw_tac_orchestrate.py --spec specs/your-spec.md

# With a task description
python adws/adw_tac_orchestrate.py --task "Your task description"

# Dry run (plan only)
python adws/adw_tac_orchestrate.py --spec specs/your-spec.md --dry-run
```

## Important Notes

- Each subtask runs in an isolated git worktree under `trees/`
- Changes are NOT merged automatically - Opus reviews first
- Failed subtasks don't block others (parallel execution)
- tasks.md is updated with results automatically
