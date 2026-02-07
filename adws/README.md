# AI Developer Workflows (ADWs)

Executable scripts that orchestrate development tasks for WitnessMark.

## Usage
ADWs are single-file Python scripts designed to be run with `uv`:

```bash
uv run adws/adw_build_and_test.py
```

## Available Workflows

### adw_build_and_test.py
Builds the worker from beacon templates and runs E2E tests.

```bash
# Full build and test
uv run adws/adw_build_and_test.py

# Skip build, just run tests
uv run adws/adw_build_and_test.py --skip-build

# Build only, skip tests
uv run adws/adw_build_and_test.py --skip-tests
```

### adw_plan_build.py
Automated Plan → Build workflow that invokes Claude programmatically.

```bash
# Full workflow: plan a task, implement it, then build & test
uv run adws/adw_plan_build.py "Add a logout button to the header"

# Use existing spec (skip planning)
uv run adws/adw_plan_build.py --spec specs/feature-logout.md

# Plan only (don't implement)
uv run adws/adw_plan_build.py --plan-only "Add dark mode support"

# Specify task type
uv run adws/adw_plan_build.py --type feature "User authentication"
uv run adws/adw_plan_build.py --type bug "Fix login redirect loop"
```

### adw_health_check.py
Verifies the WitnessMark system is operational.

```bash
# Full health check (local tools + all domains)
uv run adws/adw_health_check.py

# Verbose output
uv run adws/adw_health_check.py --verbose

# Check specific domain only
uv run adws/adw_health_check.py --domain alpr.icu

# Local checks only (no network)
uv run adws/adw_health_check.py --local-only
```

## ADW Modules

The `adw_modules/` directory contains shared utilities for workflows:

- **paths.py** - Project path utilities (`get_project_root()`, `get_beacon_dir()`, etc.)
- **runner.py** - Command execution (`run_command()`, `run_claude()`)

### Using Modules in Workflows

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

from adw_modules.runner import run_claude, run_command
from adw_modules.paths import get_project_root
```

## Creating New Workflows

1. Create a new Python file in `adws/`
2. Add the uv script header for dependencies:
   ```python
   # /// script
   # requires-python = ">=3.10"
   # dependencies = ["some-package"]
   # ///
   ```
3. Import shared utilities from `adw_modules/`
4. Implement your workflow logic
5. Make it executable: `chmod +x adws/your_workflow.py`
