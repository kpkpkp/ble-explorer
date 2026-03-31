# Feature: About Button + Online Documentation

## User Story

As a user of the bridge dashboard, I want an "About" link in the header that
opens a documentation overlay explaining the project's features, and a
"Report a Bug" link that opens a GitHub Issue.

## Requirements

1. **Header layout**: Left-justify "ESP32 String Lights", right-justify "About"
   link. Same line, no vertical growth.
2. **About overlay**: Modal/overlay (not a new page — the bridge serves one HTML
   file) containing:
   - Project name and firmware version
   - Feature summary (schedule, patterns, presets, color calibration, BLE bridge)
   - How it works (ESP32-C3 → BLE → LED strips)
   - No mention of Android app
   - "Report a Bug" link → GitHub Issues new issue URL
   - Close button / click-outside-to-dismiss
3. **GitHub Issues link**: `https://github.com/kpkpkp/ble-explorer/issues/new`
4. **ADW integration**: `.claude/commands/bug.md` already exists for bug planning.
   GitHub Issues are the intake; the ADW processes them.

## Technical Design

All changes in `html.h` — no firmware C++ changes, no new endpoints.

### Header Change
```
Current:  <h1>ESP32 String Lights</h1>           (centered)
New:      <div style="display:flex;justify-content:space-between;align-items:center">
            <h1>ESP32 String Lights</h1>
            <a onclick="showAbout()">About</a>
          </div>
```

### About Modal
A hidden `<div>` overlay with fixed positioning, shown/hidden by JS toggle.
Content is static HTML embedded in the dashboard. Dismissed by clicking
the X button or the backdrop.

### Report a Bug
Inside the about modal: a link to
`https://github.com/kpkpkp/ble-explorer/issues/new?labels=bug&template=bug_report.md`

## Subtask Decomposition (TAC-8.5)

| # | Task | Agent | Complexity |
|---|------|-------|------------|
| 1 | Write about modal HTML + CSS + JS (overlay, content, show/hide) | Sonnet | Medium |
| 2 | Modify header layout (left-justify title, right-justify About link) | Sonnet | Low |
| 3 | Write documentation content (features, architecture, no Android) | Sonnet | Medium |
| 4 | Integrate all changes into html.h, build, flash, verify | Opus | High |

## Files to Modify

| File | Change |
|------|--------|
| `firmware/elk-bridge/src/html.h` | Header layout, modal CSS, modal HTML, JS toggle, doc content |

## Testing

1. Dashboard loads — header shows title left, About right, same height
2. Click About — modal opens with documentation
3. Documentation mentions: schedule, patterns, presets, calibration, BLE bridge
4. Documentation does NOT mention: Android, Kotlin, Jetpack Compose
5. "Report a Bug" link opens GitHub Issues in new tab
6. Click X or backdrop — modal closes
7. No vertical growth in header
8. Build succeeds, OTA flash, verify on live bridge
