# Git Commit

Create a git commit for the current changes.

## Instructions

- Generate a concise commit message based on the staged/unstaged changes
- Format: `<type>: <description>`
- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`
- The description should be:
  - Present tense (e.g., "add", "fix", "update")
  - 50 characters or less
  - No period at the end
- Examples:
  - `feat: add photo upload endpoint`
  - `fix: resolve login redirect loop`
  - `chore: update dependencies`
  - `docs: add TAC reference documentation`

## Run

1. Run `git status` to see what files have changed
2. Run `git diff` to understand the changes (use `git diff --cached` if already staged)
3. Run `git add -A` to stage all changes (or selectively add specific files)
4. Run `git commit -m "<generated_commit_message>"` to create the commit

## Report

- Show the commit hash and message
- List files changed with `git show --stat HEAD`
