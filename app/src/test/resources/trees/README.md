# Screen tree fixtures

Real screens captured from an Android 16 phone during the feasibility spikes (`docs/spikes/2026-11-findings.md`), used to test the screen serializer against realistic structure.

**They are redacted.** Every text and description is replaced by `label N`, except a short allowlist of generic UI words ("Camera", "Calls", "More options" and similar). No names, messages, numbers, addresses or account details survive. Structure, flags and bounds are unchanged, because those are what the tests need.

## Format

One node per line, tab separated, in depth-first order. Lines starting with `#` are comments.

| Column | Meaning |
|---|---|
| depth | 0 for the root, +1 per level |
| role | `button`, `image`, `text`, `input`, `checkbox`, `list` or `group` |
| flags | letters `c` clickable, `e` editable, `s` scrollable, `p` password, or `-` for none |
| text | visible text, or empty |
| description | content description, or empty |
| bounds | `left,top,right,bottom` in pixels |

## Adding a fixture

Never commit a raw capture. Redact first, and check the result by eye for anything personal before committing.
