# Singleplayer Toolkit — Fabric 1.21.11

A client-entrypoint Fabric mod for Minecraft Java Edition 1.21.11 that exposes a vanilla-style cheat/utility menu for **integrated singleplayer worlds only**.

## Exact build versions

These are the versions supplied with the requested Fabric template and are intentionally unchanged:

- Minecraft: `1.21.11`
- Yarn mappings: `1.21.11+build.6`
- Fabric Loader: `0.19.5`
- Fabric API: `0.141.6+1.21.11`
- Fabric Loom: `1.18-SNAPSHOT`
- Loom plugin: `net.fabricmc.fabric-loom-remap`
- Java compile target: `21`
- GitHub Actions JDK: `25`
- Gradle in Actions: `9.2`
- Mod ID: `spktoolkit`
- Package: `com.example.spktoolkit`
- Client entrypoint: `com.example.spktoolkit.ToolkitMod`

Nothing in the project changes those requested versions.

## Important limitation about the supplied ZIP

The original `spktoolkit-project.zip` attachment was not available in the file workspace used to assemble this deliverable. I therefore reconstructed the project from the exact settings in the request instead of claiming that I had merged into the missing ZIP. The old Fabric template example is deliberately absent.

The GitHub Actions workflow includes a hard guard against the previous mistake: it checks that the built jar contains `com/example/spktoolkit/ToolkitMod.class`, checks the mod id, and fails the build if the old `Hello Fabric world!` string is present.

## Features

### Working

- Player invulnerability.
- Infinite health: restores health to current max health every tick.
- Auto-regeneration.
- Infinite hunger and saturation.
- Flight with adjustable flight speed multiplier.
- Movement speed and jump-strength multipliers using temporary attribute modifiers when available in the 1.21.11 runtime.
- Free camera as a **spectator-mode equivalent**. This is not a detached renderer camera.
- Item browser built from the real item registry; search, quantity, max-stack, and give-to-player.
- Time presets: dawn, day, noon, sunset, night, midnight.
- Clear, rain, thunder.
- Freeze time, always day, keep inventory.
- Coordinate teleport with numeric/finite validation.
- Saved named waypoints: save/update, rename, delete, and teleport within the same dimension.
- World-spawn teleport.
- HUD showing FPS, coordinates, biome, and dimension.
- Clear nearby hostile mobs with a confirmation screen.
- Spawnable mob list from the real entity registry, with search.
- Keybinds: Right Shift, F6, F7, F9, F10; all rebindable in Controls.
- JSON config at `.minecraft/config/spktoolkit.json` with safe defaults and corrupt-file backup handling.
- `Turn everything OFF (restore normal)` action with confirmation.

### Approximation

- `Fullbright` is implemented as a maximum vanilla gamma/brightness boost. It is **not** a renderer lightmap override, so it is not presented as a true shader/lightmap fullbright.
- Fire/lava, drowning, fall, and suffocation immunity are covered by the invulnerability toggle rather than separate damage-source mixins.

### Not included on purpose

- True X-ray and configurable X-ray visible-block rendering.
- Renderer-level entity highlighting.
- A true detached free camera that leaves the player's game mode untouched.
- Pause-world utility.
- Instant crop growth.

Those omitted renderer/world-editing features are intentionally left unsupported rather than simulated with fragile mixins.

## Singleplayer-only safety boundary

All gameplay actions are gated on a live integrated server using the client singleplayer/integrated-server state. There is no dedicated server entrypoint, no packet manipulation, no anti-cheat bypass, no exploit logic, and no multiplayer command path.

When the client leaves the integrated singleplayer state after the toolkit was active, runtime toggles are cleared and client-side brightness is restored where possible. No toolkit action is run while connected to a multiplayer server.

## Exact GitHub web upload steps

1. Go to GitHub and open the repository where you want this project.
2. Make sure the repository is empty or that you are replacing the old template files. Do not leave the old example Java source in place.
3. Click **Add file → Upload files**.
4. Unzip this project on Windows first.
5. In File Explorer, open the unzipped `spktoolkit-project` folder.
6. Drag the visible project files and folders into the GitHub upload box. The `.github` folder may be skipped by the browser uploader; that is expected.
7. Before committing, confirm these visible paths are present in the upload list: `build.gradle`, `gradle.properties`, `settings.gradle`, `README.md`, `src/main/java/com/example/spktoolkit/ToolkitConfig.java`, `src/main/java/com/example/spktoolkit/ToolkitManager.java`, `src/main/java/com/example/spktoolkit/ToolkitMod.java`, `src/main/java/com/example/spktoolkit/ToolkitScreen.java`, `src/main/resources/fabric.mod.json`, and `src/main/resources/assets/spktoolkit/lang/en_us.json`.
8. Click **Commit changes**.
9. Now create the hidden workflow manually. Click **Add file → Create new file**.
10. In the file-name box, enter exactly `.github/workflows/build.yml`. GitHub will create the folders automatically.
11. Paste the complete workflow from the section below.
12. Click **Commit new file**.

## Exact `.github/workflows/build.yml`

```yaml
name: Build Singleplayer Toolkit

on:
  workflow_dispatch:
  push:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '25'

      - name: Set up Gradle 9.2
        uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '9.2'
          cache-provider: basic

      - name: Build
        run: gradle clean build --no-daemon

      - name: Verify the correct toolkit jar
        shell: bash
        run: |
          set -euo pipefail
          shopt -s nullglob
          jars=(build/libs/*.jar)
          main=""
          for jar in "${jars[@]}"; do
            case "$jar" in
              *-sources.jar) ;;
              *) main="$jar"; break ;;
            esac
          done
          test -n "$main"
          test -f "$main"
          echo "Verified jar: $main"
          jar tf "$main" | grep -Fx 'com/example/spktoolkit/ToolkitMod.class'
          unzip -p "$main" fabric.mod.json | grep -q '"id": "spktoolkit"'
          if unzip -p "$main" | strings | grep -Fq 'Hello Fabric world!'; then
            echo 'ERROR: old Fabric template example string detected in jar.'
            exit 1
          fi

      - name: Prepare the installable jar
        shell: bash
        run: |
          set -euo pipefail
          shopt -s nullglob
          main=()
          for jar in build/libs/*.jar; do
            case "$jar" in
              *-sources.jar) ;;
              *) main+=("$jar") ;;
            esac
          done
          test "${#main[@]}" -eq 1
          cp "${main[0]}" build/spktoolkit-install.jar

      - name: Upload only the installable jar
        uses: actions/upload-artifact@v4
        with:
          name: spktoolkit-jar
          path: build/spktoolkit-install.jar
          if-no-files-found: error
          compression-level: 9
          include-hidden-files: false
```

## Run the GitHub Actions build

1. Open the repository on GitHub.
2. Click the **Actions** tab.
3. Click **Build Singleplayer Toolkit** in the left sidebar.
4. Click **Run workflow**.
5. Leave the branch on the branch you uploaded the files to.
6. Click the green **Run workflow** button.
7. Wait for the run to finish.
8. Open the completed run.
9. Scroll to the **Artifacts** section.
10. Download **`spktoolkit-jar`**.
11. Open the downloaded artifact ZIP on Windows. It contains exactly **`spktoolkit-install.jar`**. This is the installable jar; there is no `-sources.jar` inside the artifact.

The workflow also runs on normal pushes, so pushing changes can trigger a build automatically.

## Put the jar into the Modrinth App instance

1. Open **Modrinth App**.
2. Open your Fabric 1.21.11 instance.
3. Open the instance's **Options/Settings** and choose **Open instance folder**. The exact button wording can vary slightly by Modrinth App release.
4. Open the `mods` folder. Create it if it does not exist.
5. Copy `spktoolkit-install.jar` into that `mods` folder.
6. Do not replace the Minecraft installation directory or any world folder.
7. Launch the instance.

## Verify that the correct jar is installed

Do all of these checks in a singleplayer world:

1. Open **Options → Controls → Key Binds**.
2. Find the **Singleplayer Toolkit** category. It should contain the five toolkit keybinds.
3. Press **Right Shift**. The **Singleplayer Toolkit** screen should open.
4. Open the Minecraft log (`latest.log`) and search for `Hello Fabric world!`. That string must **not** be present.
5. Search the log for `spktoolkit` / `Singleplayer Toolkit` if needed.

If the keybind category is missing, you are almost certainly loading the wrong jar, a stale duplicate jar, or a jar built from the plain Fabric template. Remove old copies of the mod from the instance `mods` folder and use only the `spktoolkit-install.jar` produced by the workflow.

## Keybinds

Defaults:

- Right Shift — open menu
- F6 — flight
- F7 — gamma/fullbright approximation
- F9 — spectator-equivalent free camera
- F10 — invulnerability

All five are in their own **Singleplayer Toolkit** category and are registered with Fabric's keybinding system, so they can be rebound under **Options → Controls → Key Binds**.

These defaults are chosen from keys that are normally unassigned by vanilla Minecraft. If your existing mods use one, change it in Controls.

## Config

The file is in the **Config folder of the Modrinth instance you launch**:

`<your Modrinth instance folder>\config\spktoolkit.json`

For a normal `.minecraft` launcher profile this may appear as `%APPDATA%\\.minecraft\\config\\spktoolkit.json`; Modrinth App instances normally have their own instance folder, so use that instance folder rather than assuming a global `.minecraft`.

The config stores toggles, multipliers, selected item/entity, searches, waypoints, X-ray filter text, HUD preference, and runtime restore baselines.

Bad JSON or invalid numeric values are handled safely. Invalid/corrupt files are moved aside as `.broken-<timestamp>.json` where possible, and a clean default config is created in memory.

## Turning everything off / undo

Inside the menu, open **Settings → Turn everything OFF (restore normal)** and confirm.

That resets toolkit toggles and restores saved runtime baselines for player abilities, game mode, brightness, and the game rules controlled by the toolkit. It also removes the toolkit's temporary speed/jump modifiers.

Point-in-time actions such as giving an item, spawning a mob, teleporting, or setting the clock/weather are only performed after you press the corresponding button. They are not silently performed at startup.

To remove the mod completely:

1. Close Minecraft.
2. Delete `spktoolkit-install.jar` from the instance `mods` folder.
3. Optionally delete `<your Modrinth instance folder>\config\spktoolkit.json` to remove the saved toolkit config and waypoints.
4. Leave your Minecraft worlds untouched.

## Troubleshooting

### GitHub Actions build fails

Open **Actions → Build Singleplayer Toolkit → failed run → Build** and look for lines containing `error:`. Send those lines exactly. Also send the first few lines around the error if they name a missing class or method.

The most useful distinction is whether the failure is a Gradle dependency/plugin error or a Java compiler `error:` line.

### Build succeeds but the mod does not load

First check the downloaded artifact: it must be `spktoolkit-install.jar`, not a sources jar and not an old template jar.

Then confirm that the instance `mods` folder contains Fabric API plus `spktoolkit-install.jar`, and that there is no old duplicate `spktoolkit` jar.

Open `latest.log`. `Hello Fabric world!` should not appear. If the mod fails during startup, send the `error:` lines and the nearby `Caused by:` lines.

### Keybinds do not work

Make sure you are actually inside a local singleplayer world. The toolkit deliberately ignores its action keys in multiplayer.

Then open **Options → Controls → Key Binds**. The **Singleplayer Toolkit** category must be visible. If it is visible but a key is marked as conflicting, bind the toolkit action to another key.

For menu issues, try rebinding the menu to a spare key and test again.

### Menu opens but a feature shows unavailable

Unsupported features are intentionally labelled rather than faked. Renderer-dependent X-ray/entity-highlight and the true detached camera are not enabled in this build. The free-camera control uses spectator mode instead.

## Changelog

### 1.0.0

- Replaced the plain Fabric template example with the Singleplayer Toolkit client entrypoint.
- Added vanilla-style categorized toolkit screen with scrolling-by-page item/entity lists.
- Added singleplayer-only runtime guards.
- Added persistent JSON settings and named waypoints.
- Added player, movement, world, item, teleport, visual, utility, and settings actions.
- Added explicit confirmation for hostile-mob clearing, waypoint deletion, reset, and restore-normal actions.
- Added GitHub Actions jar-content verification to prevent the previous `Hello Fabric world!` mistake.
- Deliberately left fragile renderer-only and high-risk world-edit features unsupported.
