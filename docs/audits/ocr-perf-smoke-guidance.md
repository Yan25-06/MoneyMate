# OCR Perf Smoke Guidance

Date: 2026-04-09
Scope: lightweight manual verification for OCR-T9 on low and mid-tier Android devices.

## Target devices
- Low-tier reference: Android 10-12, 3 GB RAM, slower eMMC/UFS storage.
- Mid-tier reference: Android 12-14, 4-6 GB RAM.

## Smoke checklist
- Cold start `Add/Edit Transaction` and launch scan entry.
- Pick a gallery image around 3-5 MB and verify the loading state clears without ANR.
- Capture one receipt photo and verify preview -> worker -> confirmation completes without visible freeze.
- Process one blank/poor receipt image and verify failure/fallback stays under normal UI responsiveness.
- Repeat 3 scans in a row and confirm:
  - no stuck loading dialog
  - no duplicate observer effects
  - confirmation list still scrolls smoothly
- On `TransactionConfirmationFragment`, test:
  - 1 suggested item
  - 5+ suggested items
  - low-confidence warning rendering
  - save-all after previous single-item edits

## What to watch
- Worker completion target: normal local receipt under about 3-5 seconds on mid-tier hardware.
- Peak image handling: no crash or obvious jank with internal images below the 20 MB guard.
- Confirmation screen: no frame drops when warnings are shown on several items.
- Save-all: should remain responsive while repository batch save runs in background.

## Log/privacy reminder
- Verify logs only include metadata such as attempt, dimensions, block count, line count, stage, and duration.
- Do not capture or attach raw OCR text when sharing smoke results.
