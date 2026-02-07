# TAC (Tactical Agentic Coding) Reference

This document describes the TAC course repositories and how they relate to WitnessMark's agentic coding infrastructure.

## Overview

TAC is an 8-lesson course on "agentic coding" - a paradigm where AI agents execute development workflows autonomously rather than just generating code for humans to copy/paste.

The TAC repos are located at `/home/kpkpk/tac-1` through `/home/kpkpk/tac-8`.

## Course Progression

| Level | Theme | Key Concept |
|-------|-------|-------------|
| TAC-1 | Introduction | AI Coding vs Agentic Coding |
| TAC-2 | Structure | Slash Commands, Specs, AI Docs |
| TAC-3 | Automation | AI Developer Workflows (ADWs) |
| TAC-4 | Control | Lifecycle Hooks |
| TAC-5 | Extension | MCP Servers (Playwright) |
| TAC-6 | Isolation | Git Worktrees |
| TAC-7 | Context | Documentation-Driven Development |
| TAC-8 | Scale | Multi-Agent Systems |

---

## TAC-1: Introduction to Agentic Coding

**Key Files:**
- `.claude/settings.json` - Basic permissions
- `programmable/` - Scripts to invoke Claude programmatically (Python, Shell, TypeScript)
- `programmable/prompt.md` - Example agentic prompt with RUN/CREATE/REPORT verbs

**Core Teaching:** The difference between asking AI to generate code vs. having agents execute entire workflows (branch, code, run, commit).

**WitnessMark Status:** Complete (have `settings.local.json`, `.env.sample`)

---

## TAC-2: Project Structure for Agents

**Key Files:**
- `.claude/commands/` - Slash command templates
  - `prime.md` - Onboard to codebase
  - `install.md` - Set up dependencies
  - `tools.md` - List available tools
- `specs/` - Feature specifications
- `ai_docs/` - LLM documentation (API references)
- `agents/` - Agent execution logs

**Core Teaching:** Organizing a codebase so agents can navigate it effectively.

**WitnessMark Status:** Complete (have `commands/`, `specs/`, `adws/`)

---

## TAC-3: AI Developer Workflows (ADWs)

**Key Files:**
- `adws/` - Workflow scripts
  - `health_check.py` - Basic health check
  - `trigger_webhook.py` - React to GitHub webhooks
  - `trigger_cron.py` - Poll GitHub issues
  - `adw_plan_build.py` - Plan then build workflow
- `.claude/commands/` expanded with:
  - `bug.md` - Plan bug fixes
  - `chore.md` - Plan maintenance tasks
  - `feature.md` - Plan new features
  - `implement.md` - Execute a plan

**Core Teaching:** Automating the Plan -> Implement cycle. Agents read specs and execute them.

**WitnessMark Status:** Partial (have `adws/adw_build_and_test.py`, could add more workflows)

---

## TAC-4: Lifecycle Hooks

**Key Files:**
- `.claude/hooks/` - Event-driven scripts
  - `pre_tool_use.py` - Runs BEFORE any tool
  - `post_tool_use.py` - Runs AFTER any tool
  - `notification.py` - Handle notifications
  - `stop.py` - Runs when agent stops
  - `subagent_stop.py` - Runs when subagent stops
- `.claude/settings.json` - Hook configuration

**Core Teaching:** Fine-grained control over agent behavior through lifecycle events.

**WitnessMark Status:** Not implemented

---

## TAC-5: MCP (Model Context Protocol) Integration

**Key Files:**
- `.mcp.json` - MCP server configuration
- `playwright-mcp-config.json` - Playwright browser automation

**Example `.mcp.json`:**
```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp@latest", "--isolated", "--headless"]
    }
  }
}
```

**Core Teaching:** Extending agents with external tools via MCP protocol.

**WitnessMark Status:** Not implemented (could add Playwright for E2E tests)

---

## TAC-6: Isolated Execution

**Key Files:**
- `adws/adw_plan_build_iso.py` - Isolated workflow using git worktrees
- `trees/` - Directory for isolated worktrees

**Core Teaching:** Running agents in isolated git worktrees for safe, parallel execution.

**WitnessMark Status:** Not implemented

---

## TAC-7: Documentation-Driven Development

**Key Files:**
- `app_docs/` - Feature documentation with assets
- `ai_docs/` expanded with:
  - `claude_code_cli_reference.md`
  - `claude_code_sdk.md`
  - `e2b.md` (cloud execution)
- `.claude/commands/conditional_docs.md` - Guide for which docs to read

**Core Teaching:** Comprehensive documentation improves agent performance.

**WitnessMark Status:** Partial (have docs in `beacon/` and `manager/`, could consolidate)

---

## TAC-8: Multi-Agent Systems

**Structure:** Contains 5 sub-projects, each demonstrating a different architecture:

| App | Focus |
|-----|-------|
| `app1__agent_layer_primitives` | Minimum vs Scaled Agentic Layer patterns |
| `app2__multi_agent_todone` | Multi-agent task delegation via `tasks.md` |
| `app3__out_loop_multi_agent_task_board` | Out-of-loop orchestration |
| `app4__agentic_prototyping` | Rapid prototyping with agents |
| `app5__nlq_to_sql_aea` | Agent-Environment-Action architecture |

**Core Teaching:** The "12 Leverage Points of Agentic Coding":

**In Agent (Core Four):**
1. Context
2. Model
3. Prompt
4. Tools

**Through Agent:**
5. Standard Output
6. Types
7. Docs
8. Tests
9. Architecture
10. Plans
11. Templates
12. AI Developer Workflows

**WitnessMark Status:** Not implemented

---

## WitnessMark TAC Level Summary

| TAC Level | Concept | WitnessMark |
|-----------|---------|-------------|
| TAC-1 | Permissions, `.env.sample` | ✅ Complete |
| TAC-2 | Commands, Specs | ✅ Complete |
| TAC-3 | ADWs, Plan->Implement | ✅ Complete |
| TAC-4 | Hooks | ❌ Not started |
| TAC-5 | MCP Servers | ✅ Complete (kapture) |
| TAC-6 | Git Worktrees | ✅ Complete |
| TAC-7 | Consolidated Docs | ⚠️ Partial |
| TAC-8 | Multi-Agent | ✅ Complete |

**Current Level: TAC-8 (multi-agent orchestration)**

### TAC-8 Implementation Details

WitnessMark implements TAC-8 multi-agent orchestration:

**Commands:**
- `/tac` - Initiates TAC workflow
- `/worktree` - Manual worktree management

**Files:**
- `tasks.md` - Central task board
- `adws/adw_tac_orchestrate.py` - Orchestration script
- `adws/adw_modules/agent.py` - Agent spawning utilities

**Workflow:**
1. Opus plans and decomposes task into subtasks
2. Subtasks assigned to Sonnet/Haiku based on complexity
3. Each subtask runs in isolated git worktree (`trees/`)
4. Workers execute in parallel
5. Opus reviews results and integrates
6. E2E verification before merge

---

## Common Patterns Across All TAC Repos

### Directory Structure
```
project/
├── .claude/
│   ├── commands/      # Slash command templates
│   ├── hooks/         # Lifecycle hooks (TAC-4+)
│   └── settings.json  # Permissions
├── adws/              # AI Developer Workflows
├── specs/             # Feature specifications
├── ai_docs/           # LLM reference documentation
├── app/               # Application code
└── .mcp.json          # MCP server config (TAC-5+)
```

### The Plan -> Implement Cycle
1. `/chore`, `/bug`, or `/feature` creates a plan in `specs/`
2. `/implement specs/plan.md` executes the plan
3. Agent reads spec, makes changes, runs validation

### Permission Model
All TAC repos use `.claude/settings.json` to explicitly allow/deny tools:
```json
{
  "permissions": {
    "allow": ["Read", "Write", "Bash(uv:*)"],
    "deny": ["Bash(rm -rf:*)"]
  }
}
```

---

## Upgrading WitnessMark

To reach higher TAC levels, consider:

**TAC-4 (Hooks):**
- Add `.claude/hooks/pre_tool_use.py` for logging
- Add `.claude/hooks/stop.py` for session summaries

**TAC-5 (MCP):**
- Add `.mcp.json` with Playwright for automated E2E testing

**TAC-6 (Isolation):**
- Modify ADWs to use git worktrees for safe parallel execution

**TAC-7 (Docs):**
- Create root `README.md` consolidating all project docs
- Add `ai_docs/` with Cloudflare Workers/D1 references

**TAC-8 (Multi-Agent):**
- Add `tasks.md` for multi-agent task delegation
- Create parallel ADWs for different subsystems
