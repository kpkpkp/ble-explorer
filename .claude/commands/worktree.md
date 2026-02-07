# Git Worktree Management

Manage isolated git worktrees for TAC development.

## Arguments
$ARGUMENTS

## Commands

### List worktrees
```bash
git worktree list
```

### Create a worktree
```bash
# Create branch and worktree in trees/ directory
git branch tac/WORKTREE_NAME master
git worktree add trees/WORKTREE_NAME tac/WORKTREE_NAME
```

### Remove a worktree
```bash
git worktree remove trees/WORKTREE_NAME --force
git branch -D tac/WORKTREE_NAME  # optional: also delete branch
```

### Check worktree status
```bash
cd trees/WORKTREE_NAME && git status && git log --oneline -5
```

## Usage Patterns

**TAC Development:**
1. Create worktree for isolated feature work
2. Make changes in isolation
3. Commit in worktree
4. Review changes from main repo
5. Merge or cherry-pick to main

**Parallel Experiments:**
1. Create multiple worktrees
2. Try different approaches in each
3. Compare results
4. Keep best implementation

## Directory Structure
```
WitnessMark/
├── trees/           # Worktree directory
│   ├── feature-a/   # Isolated worktree
│   ├── feature-b/   # Another worktree
│   └── experiment/  # Experimental worktree
└── ...              # Main repo
```

## Notes
- Worktrees share git objects but have separate working directories
- Each worktree can be on a different branch
- Changes in one worktree don't affect others until merged
- Use `git worktree prune` to clean up stale references
