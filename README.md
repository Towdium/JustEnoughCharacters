# JECH config branch

Data-only branch holding every piece of transformer configuration and mapping
data for **Just Enough Characters**. It is consumed by the `unified` branch as a
git submodule mounted at `./config`.

No Java, Gradle or build logic lives here — only YAML. Everything that *reads*
this data lives in the `unified` branch (`buildSrc`, `tools/`).

## Why a separate branch

The target lists change far more often than the code (a new mod adds a search
bar, a mod update renames a lambda) and they are edited by people who are not
touching the build. Keeping them on their own branch means:

* config-only commits do not trigger code review on `unified`;
* the superproject pins an exact config revision via the gitlink, so a build is
  reproducible even while the config branch moves;
* the same data can be checked out standalone for tooling.

## Layout

```
.
├── generate.yaml                  # shared across every version that opts in
├── mapping.yaml                   # shared class-name rewrites (Fabric/intermediary)
├── 1.12.2/
│   ├── legacy-targets.yaml        # authoritative 1.12.2 data, NATIVE legacy format
│   ├── generate.yaml              # same data projected into the unified format
│   └── unresolvable.yaml          # legacy entries with no known descriptor
├── 1.16.5/{generate.yaml,forge/platform_generate.yaml,fabric/platform_generate.yaml}
├── 1.18.2/…  1.19.2/…  1.20.1/…   # same shape
├── 1.21.1/{generate.yaml,mapping.yaml}
└── 1.21.9+/{generate.yaml,mapping.yaml}
```

`<version>/generate.yaml` is version-specific. `generate.yaml` at the root is
the cross-version list, and **only the 1.16.5–1.20.1 modules inherit it** —
1.21.1 and 1.21.9+ carry a complete standalone list, which is how those branches
already behaved. The inheritance flag is declared in the superproject's
`settings.gradle`, not here, so this branch stays pure data.

See `SCHEMA.md` for the entry formats.

## Editing

```bash
# from the superproject
cd config
$EDITOR 1.21.1/generate.yaml
git commit -am "1.21.1: add Foo mod search bar"
git push origin HEAD:config

cd ..
git add config          # bump the pinned revision
git commit -m "config: bump for Foo mod search bar"
```

Forgetting the second step is the common mistake: the config branch moves but
`unified` still builds against the old gitlink.
