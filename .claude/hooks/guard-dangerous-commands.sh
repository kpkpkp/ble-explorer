#!/usr/bin/env bash
# guard-dangerous-commands.sh — PreToolUse hook for Bash tool
#
# Catches dangerous bash command patterns before execution.
# Exit 0 = allow, Exit 2 = block (stderr message shown to Claude)
#
# Patterns grouped by severity: CRITICAL > HIGH > MEDIUM

# ── Read stdin, extract the bash command ────────────────────────────
INPUT="$(cat)"
COMMAND="$(echo "$INPUT" | jq -r '.tool_input.command // empty')"

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

# ── Strip data regions that aren't executable ───────────────────────
# Heredoc bodies are data, not commands. Remove them before matching.
# Handles: <<EOF, <<'EOF', <<"EOF", <<-EOF (with optional quoting)
COMMAND="$(printf '%s' "$COMMAND" | perl -0777 -pe "s/<<-?\s*['\x22]?(\w+)['\x22]?.*?\n\1(\s*\).*)?$//gms")"

if [[ -z "$COMMAND" ]]; then
  exit 0
fi

deny() {
  echo "[$1] BLOCKED: $2" >&2
  exit 2
}

# ====================================================================
# CRITICAL — Irrecoverable destruction
# ====================================================================

# 1. rm -rf targeting / or ~ or $HOME
if echo "$COMMAND" | grep -qE 'rm\s+-[a-zA-Z]*(rf|fr)[a-zA-Z]*\s+(/\s*$|~/|~\s*$|\$HOME)'; then
  deny "CRITICAL" "Recursive forced delete of root or home directory"
fi

# 2. dd writing to raw block devices
if echo "$COMMAND" | grep -qE 'dd\s+.*of=/dev/(sd[a-z]|nvme[0-9]|vd[a-z]|hd[a-z]|mmcblk[0-9])'; then
  deny "CRITICAL" "dd writing to block device would destroy a disk"
fi

# 3. mkfs — formatting a filesystem
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)mkfs'; then
  deny "CRITICAL" "mkfs formats a filesystem — irrecoverable data loss"
fi

# 4. :> file truncation (allow :>/dev/null and :>/tmp/*)
if echo "$COMMAND" | grep -qE ':>\s*\S+' && \
   ! echo "$COMMAND" | grep -qE ':>\s*/dev/null' && \
   ! echo "$COMMAND" | grep -qE ':>\s*/tmp/'; then
  deny "CRITICAL" "File truncation via :> destroys file contents"
fi

# ====================================================================
# HIGH — Serious targeted destruction
# ====================================================================

# 5. rm -r[f] outside /tmp
if echo "$COMMAND" | grep -qE 'rm\s+-[a-zA-Z]*r' && \
   ! echo "$COMMAND" | grep -qE 'rm\s+-[a-zA-Z]*r[a-zA-Z]*\s+/tmp'; then
  deny "HIGH" "Recursive rm outside /tmp — remove specific files or work in /tmp"
fi

# 6. find with -delete or -exec rm
if echo "$COMMAND" | grep -qE 'find\s+.*(-delete|-exec\s+rm)'; then
  deny "HIGH" "find with -delete/-exec rm does bulk deletion"
fi

# 7. dd from /dev/zero or /dev/urandom to a file
if echo "$COMMAND" | grep -qE 'dd\s+.*if=/dev/(zero|urandom).*of=' && \
   ! echo "$COMMAND" | grep -qE 'of=/dev/null'; then
  deny "HIGH" "dd from /dev/zero or /dev/urandom overwrites the target"
fi

# 8. git clean with force flags
if echo "$COMMAND" | grep -qE 'git\s+clean\s+.*-[a-zA-Z]*[fdx]'; then
  deny "HIGH" "git clean permanently removes untracked files"
fi

# 9. git reset --hard
if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
  deny "HIGH" "git reset --hard discards all uncommitted changes"
fi

# 10. git checkout . or git restore . (discard entire working tree)
if echo "$COMMAND" | grep -qE 'git\s+(checkout|restore)\s+\.\s*$'; then
  deny "HIGH" "Discards all working tree changes"
fi

# 11. git push --force to main/master (allow --force-with-lease)
if echo "$COMMAND" | grep -qE 'git\s+push\s+[^|]*--force($|\s)[^|]*(main|master)' || \
   echo "$COMMAND" | grep -qE 'git\s+push\s+[^|]*(main|master)[^|]*--force($|\s)' || \
   echo "$COMMAND" | grep -qE 'git\s+push\s+-f\s+[^|]*(main|master)'; then
  deny "HIGH" "Force push to main/master rewrites shared history"
fi

# 12. chmod -R 000 or chmod -R 777
if echo "$COMMAND" | grep -qE 'chmod\s+-[a-zA-Z]*R[a-zA-Z]*\s+(000|777)'; then
  deny "HIGH" "Recursive chmod 000/777 is almost always wrong"
fi

# ====================================================================
# MEDIUM — Risky operations
# ====================================================================

# 13. nc -l (network listener)
if echo "$COMMAND" | grep -qE 'nc\s+.*-[a-zA-Z]*l'; then
  deny "MEDIUM" "nc -l opens a network listener"
fi

# 14. curl/wget piped to bash/sh
if echo "$COMMAND" | grep -qE '(curl|wget)\s+.*\|\s*(sudo\s+)?(bash|sh)\b'; then
  deny "MEDIUM" "Piping curl/wget to shell executes unreviewed remote code"
fi

# 15. eval with variable expansion
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)eval\s+.*\$'; then
  deny "MEDIUM" "eval with variable expansion can execute arbitrary code"
fi

# 16. python3/node inline code execution (-c, -e, -m)
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)(python3?|node)\s+-[ce]\s'; then
  deny "MEDIUM" "Inline code execution — use a script file instead"
fi

# 17. python3 -m (module execution, e.g. http.server, pip)
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)python3?\s+-m\s'; then
  deny "MEDIUM" "python3 -m module execution — use a script file or explicit command"
fi

# 18. bash -c / sh -c (shell command wrapping)
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)(bash|sh)\s+-c\s'; then
  deny "MEDIUM" "bash -c wraps arbitrary code — run the command directly"
fi

# 19. curl/wget without pipe (data exfiltration risk in autonomous mode)
# Allow curl to localhost/127.0.0.1 for dashboard health checks
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)(curl|wget)\s' && \
   ! echo "$COMMAND" | grep -qE '(curl|wget)\s+[^|]*(localhost|127\.0\.0\.1)'; then
  deny "MEDIUM" "Network fetch to external host — needs explicit approval"
fi

# 20. ssh connections (remote access risk in autonomous mode)
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)ssh\s'; then
  deny "MEDIUM" "SSH connection — needs explicit approval"
fi

# 21. pip/npm install arbitrary packages (supply chain risk)
# Allow: pip3 install -r <file>, npm ci, npm install (no args = from lockfile)
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)pip3?\s+install\s' && \
   ! echo "$COMMAND" | grep -qE 'pip3?\s+install\s+-r\s'; then
  deny "MEDIUM" "pip install arbitrary package — use -r requirements.txt"
fi
if echo "$COMMAND" | grep -qE '(^|[;&|]\s*)npm\s+install\s+\S' && \
   ! echo "$COMMAND" | grep -qE 'npm\s+install\s+--save-dev\s'; then
  deny "MEDIUM" "npm install arbitrary package — use package.json"
fi

# ====================================================================
# All checks passed
# ====================================================================
exit 0
