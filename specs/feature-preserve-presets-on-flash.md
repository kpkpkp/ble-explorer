# Feature: Preserve User Presets Across Firmware Flash

## User Story

As a user reflashing the bridge (OTA or USB), I want my custom presets to
survive the update. OTA normally preserves NVS, but USB flash can erase it,
and OTA partition layout changes could too. A backup/restore step makes this
safe regardless of flash method.

## Technical Design

The bridge already has the API surface needed:

- `GET /api/presets` — returns all user presets as JSON
- `POST /api/presets` — saves a single preset (name + steps)

### Changes to `scripts/deploy.sh`

Before flashing:
1. `GET /api/presets` → save response to a temp file
2. Also backup schedule: `GET /api/schedule` → save to temp file

After bridge comes back:
3. For each preset in the backup: `POST /api/presets` with the preset data
4. For each schedule entry: `POST /api/schedule` to restore

### Schedule backup

The schedule API (`GET /api/schedule`) already returns entries in a format
compatible with `POST /api/schedule` for saving. Same pattern as presets.

## Files to Modify

| File | Change |
|------|--------|
| `scripts/deploy.sh` | Add backup before flash, restore after reboot |

No firmware changes needed — existing API endpoints handle everything.

## Testing

1. Create a user preset via dashboard, run `deploy.sh`, verify preset survives
2. Create a schedule entry, run `deploy.sh`, verify schedule survives
3. Test with no presets/schedule (empty backup) — should be a no-op
4. Test with bridge unreachable pre-flash (skip backup gracefully)
