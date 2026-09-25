# BP Tracker

A personal Android blood-pressure tracker (Kotlin + Jetpack Compose, Material 3).

Readings and notes are stored locally in a Room database and can be mirrored as a plain,
human-readable CSV to a folder of your choice (e.g. a Google Drive folder) via the Storage
Access Framework — no account sign-in inside the app.

## Screenshots

| Add reading | History | Log | About | Console theme |
|:---:|:---:|:---:|:---:|:---:|
| <img src="screenshots/01-add-reading.png" width="170" alt="Add reading tab"> | <img src="screenshots/02-history.png" width="170" alt="History tab with trend chart"> | <img src="screenshots/03-log.png" width="170" alt="Log tab"> | <img src="screenshots/04-about.png" width="170" alt="About tab with update check"> | <img src="screenshots/05-console-theme.png" width="170" alt="Console theme"> |
| Last reading with AHA category and trend | Averages, trend chart with note markers | Readings and notes in one time-ordered list | Version, changelog and in-app update check | One of four themes |

*Screenshots use demo data, not real readings.*

## Features

- Capture readings (systolic/diastolic/heart rate/arm/date-time) with AHA category
  classification and a trend against the previous reading. The arm you last used is remembered,
  a back-dated date/time is kept until you save, and future times are rejected. A reading in the
  hypertensive-crisis range shows an advisory.
- Notes (medication changes, check-ups, samples) alongside readings, plus one-tap
  "Medication taken" notes that record the time the dose was taken.
- History with period filters (month, quarter, year, since last check-up, all time), averages,
  a trend chart, and note markers on the systolic line.
- Dense text log of readings and notes with arm filters and edit/delete.
- Daily reminders at one or more times, kept at the same local time across daylight-saving
  changes. The app warns you when notifications are blocked.
- Automatic CSV backup to a folder you choose, with daily snapshots (see below).
- CSV import/export and a shareable PDF "doctor's report".
- Home-screen widget showing the last reading.
- Four themes (System, Light, Dark, Console) and an in-app update check via GitHub Releases.

## Backup and moving phones

- **What is backed up.** After every change, and once a day, the app writes every reading and
  note to `readings.csv` in the folder you picked (History tab → **Set backup**). The file is
  plain CSV, so any spreadsheet can open it.
- **How syncing merges.** A sync adds readings the file has but the phone doesn't. The phone's
  own copies always win, and deletions are remembered so a deleted reading isn't brought back
  from the file.
- **What is never overwritten.**
  - A `readings.csv` written by some other app is left untouched; sync reports an error instead.
  - If some rows in the file can't be read, the original is first saved beside it as
    `readings-unreadable-….csv`.
- **Snapshots.** Before the first sync of each day, the previous file is copied to
  `snapshots/readings-YYYY-MM-DD.csv`. Snapshots are kept daily for two weeks, then one per month
  for a year. To go back to one, use **Import CSV** on the History tab. Import only adds readings
  and notes that are missing, so it never undoes a later edit.
- **Moving to a new phone.** Android's own backup restores the database and settings but not the
  app's permission to use the folder. The app notices this and asks you to **Re-link**: pick the
  same folder again and syncing carries on.
- **Warnings.** The Add reading tab shows a warning when there is no backup folder, when the
  folder is only on-device storage, when access was lost, or when backups have been failing for
  more than a day. **Change** (next to the status line on the History tab) moves the backup to
  another folder.

## Build

Requires JDK 21. The Paparazzi screenshot-test plugin declares a JVM 21 minimum, so an
older JDK fails during configuration, before anything compiles. Android Studio's bundled
JBR works if it is 21 or newer — check with `java -version` from
`<studio>/jbr/bin`. Compiled bytecode still targets Java 17.

```
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # Android lint (fails on errors)
./gradlew :app:assembleRelease      # release APK (needs signing config; see below)
```

Debug builds install alongside the released app (`applicationIdSuffix = ".debug"`) with their
own database. They back up to `readings-debug.csv`, so a debug build pointed at the real backup
folder can't mix test readings into the real history.

Release signing expects `keystore.properties` and a keystore at the project root (both
git-ignored). Create `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`,
and `keyPassword`. Without it, `assembleRelease` quietly produces `app-release-unsigned.apk`.

### Database schema

Room schemas are exported to `app/schemas/` and committed. A schema change needs a real
`Migration` written against them. Only the pre-v3 schemas may be dropped; any other missing
migration (or a downgrade) makes the app refuse to open rather than wipe the reading history.
Before Room migrates, the app copies the database file aside (`filesDir/db-backups`, newest three
kept).

## Screenshot tests

Compose screens are pinned by [Paparazzi](https://github.com/cashapp/paparazzi) snapshots, which
render on the JVM — no emulator or device needed.

```
./gradlew :app:verifyPaparazziDebug  # compare against committed baselines
./gradlew :app:recordPaparazziDebug  # re-record after an intentional UI change
```

Baselines live in `app/src/test/snapshots/images` and are committed. When `verify` fails it
writes expected/diff/actual images to `app/build/paparazzi/failures` and an HTML report to
`app/build/reports/paparazzi`. Review those before re-recording — a diff is a regression until
you have decided otherwise.

Current coverage is `EqualWidthSegmentedRow` across themes, selection positions, option counts,
narrow and landscape screens, and a 2x accessibility font scale. `ThemeMode.SYSTEM` is
deliberately excluded: it resolves to wallpaper-derived dynamic colour and is not reproducible.

Plain `testDebugUnitTest` renders the snapshot tests without comparing them; only
`verifyPaparazziDebug` gates them.

## Releases & updates

Each release is published on GitHub with a tag matching the version name (e.g. `v2.6.0`) and the
release-signed `BPTracker-vX.Y.Z.apk` attached as an asset. The app's About tab can check for and
install newer releases. Android only installs an update signed with the same key as the current
install, so only release-signed APKs are published.

Releases are cut by the **Release** GitHub Actions workflow, with `publish-release.ps1` as a
local fallback. Both refuse to publish when the signing certificate, tests, snapshots, changelog
or version don't check out. See [`.github/workflows/README-release.md`](.github/workflows/README-release.md).
