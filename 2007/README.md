# zebra-skills

AI coding-assistant **skills** for Zebra product SDKs — a **two-layer hybrid** with a router.

- **`reference/`** — *Layer 1*: one skill per **API/feature**, per product (*"learn/use DataWedge, AIDC, OEMInfo…"*).
- **`workflows/`** — *Layer 2*: one skill per **end-to-end task**, **composing** Layer-1 features (*"capture proof of delivery", "receive scans into an app"*).
- **`router/`** — the **entry advisor**: picks the product and routes *learn-an-API/feature* ↔ *do-a-task*.

> Architecture + rationale: **`SKILL-HYBRID-ARCHITECTURE.md`** (working notepad). This tree is a runnable illustration of that structure — the starter set for Developer Council review.

## Layout
```
zebra-skills/
├── README.md                 # this file
├── agentskills.json          # registry (name / path / layer / product / composes)
├── router/SKILL.md           # entry advisor
├── reference/                # Layer 1 — one API/feature each, grouped by product
│   ├── aidc/aidc-decode-barcodes/
│   ├── datawedge/datawedge-intent-output/
│   └── oeminfo/oeminfo-device-identifiers/
├── workflows/                # Layer 2 — end-to-end tasks (compose Layer 1)
│   ├── capture-proof-of-delivery/
│   ├── onboard-app-to-external-display/
│   └── receive-scans-into-app/
└── samples/                  # demo apps = ground-truth for evals (not included in this scaffold)
```

## Which one do I use?
- **Use ONE API/feature** → `reference/<product>/<feature>/`
- **Do an end-to-end task** → `workflows/<task>/` (it composes reference skills)
- **Not sure / spans several** → start at `router/`

## What's in a skill folder
| File | Role |
|---|---|
| `skill.yaml` | **the one hand-authored file** — the generation manifest (pointers to a sample + docs). Committed for reproducibility; **build-time only**, ignored at runtime. |
| `SKILL.md` | the skill itself (generated) — what the assistant loads. |
| `references/` | primitive-complete, verbatim-from-source content (**reference skills only**; workflows pull primitives from the skills they compose). |
| `evals/` | discovery + compile (+ run) cases, with `fixtures/`. |

## Adding a skill (see `SKILL-HYBRID-ARCHITECTURE.md` §5)
1. Write a `skill.yaml` manifest — `layer: reference|workflow` + pointers to a sample module (or demo-app task flow) + doc anchors; workflows also list `composes:`.
2. Run the skill generator against the manifest.
3. Register it in `agentskills.json`.
4. Wire it into `router/SKILL.md`.
5. Verify — reference: **compiles**; workflow: **compiles + runs** against the demo app.

## Install / distribution
Pull-based: point your AI assistant (Claude Code · Cursor · Copilot · Codex · Gemini) at this repo, or consume via the Zebra MCP server. One canonical `SKILL.md` per skill is projected to each assistant's native format.

## Status
🟡 Starter set (2–3 per layer) for **Developer Council** review. Grows over time. Open decisions tracked in `SKILL-HYBRID-ARCHITECTURE.md`.
