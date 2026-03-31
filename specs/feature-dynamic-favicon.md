# Feature: Dynamic Favicon Reflecting Strip Color

## User Story

As a user with the dashboard open in a browser tab, I want the favicon to
show the current color of the lights so I can see strip state at a glance.
When lights are off, the browser's default globe icon is fine — no custom icon.

## Behavior

- **Lights ON with color**: favicon is a solid 16x16 square of that RGB color
- **Lights OFF or color (0,0,0)**: remove `<link rel="icon">` so browser
  shows its default favicon (dark globe)

## Technical Design

- No `<link rel="icon">` in static HTML — starts with browser default
- `updFav()` called from `syncUI()`: checks strip power + color
- If ON and non-black: create/update a `<link rel="icon">` with a canvas
  data URL filled with the color
- If OFF or black: remove the link element if it exists
- Canvas created once lazily, reused across calls

## Files to Modify

| File | Change |
|------|--------|
| `firmware/elk-bridge/src/html.h` | Add `updFav()` function, call from `syncUI()` |
