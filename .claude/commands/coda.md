# Session Coda - Permissions Audit

You are performing an end-of-session audit of tool permissions. Be rigorous and precise — lazy audits waste the user's time.

## Instructions

### Step 1: Extract Every Bash Command Prefix

Go through the ENTIRE conversation and extract the **first word** (command prefix) of every Bash tool call you made this session. This is what the permission system matches on.

For each prefix, check whether it exists in `.claude/settings.local.json` `permissions.allow` as `Bash({prefix}:*)`.

Format as a table:
| # | Command Prefix | Example Command | In Allow List? |
|---|---------------|-----------------|----------------|

IMPORTANT:
- Extract the ACTUAL first token of the command string, not a summary
- `nohup sh -c 'adb ...'` → prefix is `nohup`, NOT `adb`
- `sleep 5 && adb ...` → prefix is `sleep`
- `strings /path/to/file` → prefix is `strings`
- `cd /foo && bar` → prefix is `cd`
- Do NOT guess or assume — read the allow list and do an exact match
- Any prefix NOT in the allow list caused a permission prompt that interrupted the user

### Step 2: Identify Permission Interrupts

From the table above, list every command prefix where "In Allow List?" is NO. These are the commands that **interrupted the user with a permission prompt**.

Be explicit: "These N commands caused permission interrupts during this session."

### Step 3: Audit Existing Allow List

Read `.claude/settings.local.json` and review the current `permissions.allow` list.

Identify **stale entries** — entries that are:
- One-off task IDs or temporary file paths
- Full command strings (not `Bash(prefix:*)` patterns) that got auto-added
- Hardcoded absolute paths that won't be reused
- Duplicate or redundant patterns
- Commands from previous sessions no longer relevant

Flag entries that look like auto-added one-offs (they typically contain full paths, comments, or variable assignments).

### Step 4: Recommend Changes

**Additions** — for each missing prefix from Step 2:
- Recommend `Bash({prefix}:*)` pattern
- Briefly note what it's used for

**Removals** — for each stale entry from Step 3:
- Quote the exact entry
- Explain why it's stale

### Step 5: Apply Changes

Ask the user if they want you to update `settings.local.json` with the additions and removals. Keep the file:
- Organized: Bash commands alphabetized, then Read/Skill/Task/WebFetch/WebSearch, then MCP tools
- Clean: no one-off commands, no duplicates, no stale entries
- Valid JSON: watch for trailing commas when removing entries
