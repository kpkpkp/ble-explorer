# Feature: Dashboard Real-Time Clock + Schedule-Aware Power Buttons

## User Story

As a user viewing the bridge dashboard, I want to see the current bridge time
updating live (not just on page load), and I want the ON/OFF buttons to be
disabled when a schedule is managing power — so I don't accidentally override
the scheduler and wonder why the lights came back on.

## Requirements

1. **Live clock** — show `HH:MM:SS` updating every second, on the same status
   line as Uptime, to the left of it. No vertical expansion.
2. **Schedule-aware ON/OFF** — when a schedule entry with pattern `*` (power
   timer) exists and is enabled, disable the ON and OFF buttons and add a
   visual hint (e.g. dimmed, tooltip). When no power schedule exists, buttons
   work normally.

## Technical Design

### 1. Live Clock (client-side interpolation)

The bridge already reports `time` in `/status` (polled every 1s). Rather than
adding a separate WebSocket or faster poll, use a JS `setInterval(1000)` that:
- Seeds from `d.time` on each poll (resyncs every 1s)
- Increments a local `Date` object between polls
- Renders `HH:MM:SS` into a new `<span id="clk">` element

This avoids any firmware changes for the clock.

### 2. Layout Change (line 95 of html.h)

Current:
```html
<div class="sr"><span>Uptime: <span id="ut">--</span></span><span id="hp"></span></div>
```

New:
```html
<div class="sr"><span><span id="clk"></span> | Uptime: <span id="ut">--</span></span><span id="hp"></span></div>
```

The clock text fits naturally — `00:18:45` is 8 chars, well within the line.

### 3. ON/OFF Button Guard

On each `syncUI()` call, check if any schedule entry is a power timer (`*`)
and is enabled. If so, add `disabled` attribute to the ON/OFF buttons and
dim them. Poll data already includes schedule entries (from `loadSched`).

The guard checks the `SC` array (already populated by `loadSched()`):
```js
var hasPwSch = SC.some(function(e){ return e.enabled && e.pattern === '*'; });
```

Disable buttons:
```js
document.querySelector('.bon').disabled = hasPwSch;
document.querySelector('.bof').disabled = hasPwSch;
```

### 4. Disabled Button Styling

Add CSS for `button:disabled` to dim without looking broken:
```css
button:disabled{opacity:.35;cursor:not-allowed}
```

## Files to Modify

| File | Change |
|------|--------|
| `firmware/elk-bridge/src/html.h` | Add `<span id="clk">` to status line, add clock JS, add schedule-aware button disable logic, add disabled button CSS |

No firmware C++ changes needed — all client-side.

## Testing

1. Open dashboard, verify clock shows and ticks every second
2. Verify clock resyncs on each status poll (no drift over minutes)
3. With a `*` schedule entry enabled: ON/OFF buttons are dimmed/disabled
4. Remove/disable all `*` schedule entries: ON/OFF buttons re-enable
5. Verify no vertical layout expansion on mobile (480px viewport)
