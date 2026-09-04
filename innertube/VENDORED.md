# Vendored — do not edit

Source: https://github.com/z-huang/InnerTune — `innertube/` module (GPL-3.0)

Local edits will be lost on the next sync and will cause merge conflicts.
Anything Mero needs to change belongs in `:app`, wrapped around this module.

## Re-syncing when YouTube extraction breaks

    git clone https://github.com/z-huang/InnerTune.git /tmp/innertune
    cd /tmp/innertune && git subtree split --prefix=innertube -b innertube-only
    cd <mero repo>
    git fetch innertube-upstream innertube-only
    git subtree pull --prefix=innertube innertube-upstream innertube-only --squash

See `.claude/skills/resync-innertube/SKILL.md` for the full procedure,
including how to tell extraction breakage apart from an OEM task-killer.
