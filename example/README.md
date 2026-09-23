# An example plugin on VoidRP UI

The working minimum: `/uidemo` opens a page with tabs, tiles and a button that counts
presses. The folder can be copied anywhere and built on its own.

```bash
./gradlew build
# build/libs/voidrp-ui-example-1.0.0.jar → into plugins/ next to VoidRpUI.jar
```

Building it next to the engine's own sources — point it at a fresh jar instead of JitPack:

```bash
./gradlew build -PvoidrpUi=../build/libs/voidrp-ui-0.2.0.jar
```

## What to look at

| File | About |
|---|---|
| `UiDemoPlugin.kt` | getting the service (`VoidRpUi.get()`) and opening a page |
| `DemoPage.kt` | the page itself: state in fields, `refresh()` after a change, `onClick` |
| `build.gradle.kts` | one dependency, `compileOnly` — the plugin is already on the server |
| `plugin.yml` | `depend: [VoidRpUI]` |

Three things this example exists for:

- **`screen(...)`** — the root of a page, exactly the size of the player's screen.
- **`width = Size.Fill, maxWidth = 900`** — fill the screen, but do not stretch into a
  banner on an ultrawide. This is `max-width` with automatic centring.
- **`viewport.by(compact = 2, regular = 3)`** — fewer columns on a narrow screen. The
  canvas is always 1024 units tall, so a narrow screen means *narrower cards*, not *more
  rows*.

Next: [layout](../docs/layout.md), [pages](../docs/page.md),
[components](../docs/components.md), [responsive](../docs/responsive.md).
