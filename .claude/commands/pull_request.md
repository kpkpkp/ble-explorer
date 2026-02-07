# Create Pull Request

Push the current branch and create a pull request.

## Instructions

- Generate a PR title in format: `<type>: <description>`
- The PR body should include:
  - Summary of changes (bullet points)
  - Link to any relevant spec file in `specs/`
  - Testing notes (what was tested, how to verify)
- If on `master` or `main`, create a new branch first

## Run

1. Run `git branch --show-current` to check current branch
2. If on master/main, create a new branch: `git checkout -b <descriptive-branch-name>`
3. Run `git log origin/master..HEAD --oneline` to see commits to include
4. Run `git diff origin/master...HEAD --stat` to summarize changes
5. Run `git push -u origin <branch_name>` to push the branch
6. Run `gh pr create --title "<pr_title>" --body "<pr_body>" --base master` to create the PR

## Report

- Return the PR URL
- Show summary of what's included in the PR
