#!/usr/bin/env python3
"""
TAC (Tactical Agentic Coding) Orchestration Script

Implements the out-of-loop multi-agent pattern:
1. Opus plans and decomposes task into subtasks
2. Sonnet/Haiku workers execute subtasks in isolated worktrees
3. Opus integrates and verifies results before merge

Usage:
    python adws/adw_tac_orchestrate.py --spec specs/feature-example.md
    python adws/adw_tac_orchestrate.py --task "Add user preferences API"
"""

import argparse
import json
import sys
import os
from pathlib import Path
from datetime import datetime
from typing import Optional
from concurrent.futures import ThreadPoolExecutor, as_completed

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))
from adw_modules.agent import (
    AgentRequest,
    AgentResponse,
    spawn_agent,
    create_worktree,
    remove_worktree,
    list_worktrees,
    get_commit_hash,
)
from adw_modules.paths import get_project_root


def log(msg: str, level: str = "INFO"):
    """Print timestamped log message."""
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] [{level}] {msg}")


def phase_plan(task_description: str, spec_content: Optional[str] = None) -> tuple[bool, list[dict]]:
    """
    Phase 1: Use Opus to plan and decompose the task.

    Returns:
        Tuple of (success, subtasks) where subtasks is a list of dicts with
        {name, description, model, dependencies}
    """
    log("Phase 1: PLANNING (Opus)")

    context = spec_content if spec_content else task_description

    prompt = f"""You are a senior architect planning a development task.

## Task
{context}

## Instructions
1. Analyze the task requirements
2. Break it into 2-5 independent subtasks that can be executed in parallel
3. For each subtask, determine if it needs opus/sonnet/haiku based on complexity:
   - opus: Complex architectural decisions, integration, security-critical
   - sonnet: Standard feature implementation, moderate complexity
   - haiku: Simple tasks, boilerplate, documentation, tests

Output ONLY a JSON array of subtasks with this exact structure (no markdown):
[
  {{
    "name": "subtask-name-kebab-case",
    "description": "Detailed description of what to implement",
    "model": "sonnet",
    "dependencies": []
  }}
]

Focus on making subtasks truly parallelizable - minimize dependencies.
"""

    response = spawn_agent(AgentRequest(
        prompt=prompt,
        model="opus",
        working_dir=get_project_root(),
        agent_name="planner",
        max_turns=10,
    ))

    if not response.success:
        log(f"Planning failed: {response.error}", "ERROR")
        return False, []

    # Parse subtasks from response
    try:
        # Find JSON array in output
        output = response.output
        start = output.find('[')
        end = output.rfind(']') + 1
        if start >= 0 and end > start:
            subtasks = json.loads(output[start:end])
            log(f"Decomposed into {len(subtasks)} subtasks")
            for st in subtasks:
                log(f"  - {st['name']} ({st['model']}): {st['description'][:60]}...")
            return True, subtasks
    except json.JSONDecodeError as e:
        log(f"Failed to parse subtasks JSON: {e}", "ERROR")
        log(f"Raw output: {response.output[:500]}", "DEBUG")

    return False, []


def phase_execute(subtasks: list[dict], parallel: bool = True) -> dict[str, AgentResponse]:
    """
    Phase 2: Execute subtasks in isolated worktrees.

    Returns:
        Dict mapping subtask name to AgentResponse
    """
    log("Phase 2: EXECUTING (Workers)")

    project_root = get_project_root()
    results = {}

    def execute_subtask(subtask: dict) -> tuple[str, AgentResponse]:
        name = subtask["name"]
        model = subtask.get("model", "sonnet")
        description = subtask["description"]

        log(f"  Starting: {name} ({model})")

        # Create isolated worktree
        success, worktree_path, error = create_worktree(project_root, name)
        if not success:
            return name, AgentResponse(success=False, output="", error=f"Worktree creation failed: {error}")

        # Build implementation prompt
        prompt = f"""You are implementing a subtask in an isolated git worktree.

## Subtask: {name}
{description}

## Instructions
1. Implement the required changes
2. Write clean, production-ready code
3. Add tests if appropriate
4. Commit your changes with a clear message
5. Summarize what you implemented

## Context
- Working in isolated worktree: {worktree_path}
- Changes will be reviewed before merging to main
- Focus on this subtask only - other subtasks run in parallel
"""

        response = spawn_agent(AgentRequest(
            prompt=prompt,
            model=model,
            working_dir=worktree_path,
            agent_name=f"worker-{name}",
            max_turns=30,
        ))

        # Get commit hash if successful
        if response.success:
            response.commit_hash = get_commit_hash(worktree_path)
            log(f"  Completed: {name} -> {response.commit_hash}")
        else:
            log(f"  Failed: {name} - {response.error}", "ERROR")

        return name, response

    if parallel:
        # Execute in parallel
        with ThreadPoolExecutor(max_workers=4) as executor:
            futures = {executor.submit(execute_subtask, st): st for st in subtasks}
            for future in as_completed(futures):
                name, response = future.result()
                results[name] = response
    else:
        # Execute sequentially
        for subtask in subtasks:
            name, response = execute_subtask(subtask)
            results[name] = response

    return results


def phase_integrate(subtasks: list[dict], results: dict[str, AgentResponse]) -> tuple[bool, str]:
    """
    Phase 3: Use Opus to review, verify, and integrate results.

    Returns:
        Tuple of (success, summary)
    """
    log("Phase 3: INTEGRATING (Opus)")

    project_root = get_project_root()

    # Build summary of results for Opus
    results_summary = []
    for st in subtasks:
        name = st["name"]
        res = results.get(name)
        if res:
            status = "✅ SUCCESS" if res.success else "❌ FAILED"
            results_summary.append(f"""
### {name}
- Status: {status}
- Commit: {res.commit_hash or 'N/A'}
- Output: {res.output[:500] if res.output else 'No output'}
- Error: {res.error or 'None'}
""")

    results_text = "\n".join(results_summary)

    prompt = f"""You are the integration lead reviewing parallel development work.

## Subtask Results
{results_text}

## Instructions
1. Review each subtask's results
2. For successful subtasks:
   - Check if the work is complete and correct
   - Identify any conflicts or integration issues
3. For failed subtasks:
   - Analyze the failure
   - Determine if manual intervention is needed
4. Create a merge plan:
   - Which worktrees to merge (in order)
   - Any manual steps needed
   - E2E verification steps

## Output Format
Provide:
1. Summary of what was accomplished
2. List of worktrees ready to merge (or empty if none)
3. Any issues requiring manual attention
4. Recommended E2E tests to run

Be thorough but concise.
"""

    response = spawn_agent(AgentRequest(
        prompt=prompt,
        model="opus",
        working_dir=project_root,
        agent_name="integrator",
        max_turns=20,
    ))

    if not response.success:
        log(f"Integration review failed: {response.error}", "ERROR")
        return False, response.error or "Integration failed"

    log("Integration review complete")
    return True, response.output


def update_tasks_md(subtasks: list[dict], results: dict[str, AgentResponse]):
    """Update tasks.md with results."""
    project_root = get_project_root()
    tasks_file = project_root / "tasks.md"

    if not tasks_file.exists():
        return

    # Read current content
    content = tasks_file.read_text()

    # Find insertion point after "## Completed Tasks"
    insert_marker = "## Completed Tasks"
    insert_pos = content.find(insert_marker)
    if insert_pos < 0:
        return

    insert_pos = content.find('\n', insert_pos) + 1

    # Build new entries
    new_entries = []
    for st in subtasks:
        name = st["name"]
        res = results.get(name)
        if res and res.success:
            new_entries.append(f"[✅ {name}, {res.commit_hash}] {st['description'][:60]} {{{st['model']}}}")
        elif res:
            error_short = (res.error or "unknown")[:30]
            new_entries.append(f"[❌ {name}, {error_short}] {st['description'][:60]} {{{st['model']}}}")

    if new_entries:
        entries_text = '\n'.join(new_entries) + '\n'
        new_content = content[:insert_pos] + entries_text + content[insert_pos:]
        tasks_file.write_text(new_content)
        log(f"Updated tasks.md with {len(new_entries)} entries")


def main():
    parser = argparse.ArgumentParser(description="TAC Multi-Agent Orchestration")
    parser.add_argument("--spec", type=str, help="Path to spec file")
    parser.add_argument("--task", type=str, help="Task description (if no spec)")
    parser.add_argument("--no-parallel", action="store_true", help="Execute subtasks sequentially")
    parser.add_argument("--dry-run", action="store_true", help="Plan only, don't execute")
    args = parser.parse_args()

    if not args.spec and not args.task:
        parser.error("Either --spec or --task is required")

    log("=" * 60)
    log("TAC ORCHESTRATION STARTING")
    log("=" * 60)

    # Load spec content if provided
    spec_content = None
    if args.spec:
        spec_path = Path(args.spec)
        if spec_path.exists():
            spec_content = spec_path.read_text()
            log(f"Loaded spec: {args.spec}")
        else:
            log(f"Spec file not found: {args.spec}", "ERROR")
            sys.exit(1)

    task_description = args.task or f"Implement the following spec:\n\n{spec_content}"

    # Phase 1: Plan
    success, subtasks = phase_plan(task_description, spec_content)
    if not success or not subtasks:
        log("Planning failed - aborting", "ERROR")
        sys.exit(1)

    if args.dry_run:
        log("Dry run - stopping after planning")
        print(json.dumps(subtasks, indent=2))
        sys.exit(0)

    # Phase 2: Execute
    results = phase_execute(subtasks, parallel=not args.no_parallel)

    # Update tasks.md
    update_tasks_md(subtasks, results)

    # Phase 3: Integrate
    success, summary = phase_integrate(subtasks, results)

    log("=" * 60)
    log("TAC ORCHESTRATION COMPLETE")
    log("=" * 60)
    print("\n" + summary)

    # Exit with appropriate code
    all_success = all(r.success for r in results.values())
    sys.exit(0 if all_success and success else 1)


if __name__ == "__main__":
    main()
