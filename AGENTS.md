# Working on BP Tracker

Guidance for anyone (or any coding agent, whatever model it runs on) working in this repository.
It is deliberately tool- and model-neutral. Tool-specific notes belong in local, git-ignored files
(see `.gitignore`), not here.

## What this is

A personal Android blood-pressure tracker: Kotlin, Jetpack Compose (Material 3), Room, WorkManager,
Vico charts. Readings are stored on the device and mirrored to a user-chosen folder as CSV. The
app is public, but it is used by one person, and the data it holds is real health data.

Start with [`README.md`](README.md) for the user-facing feature set and
[`.github/workflows/README-release.md`](.github/workflows/README-release.md) for releasing.

## Ground rules

- **Never commit real data.** `*.db`, `*.csv` exports, `keystore.properties`, `*.keystore`,
  `*.jks` and `*.apk` are git-ignored for a reason. Screenshots and test fixtures use invented
  demo values only.
- **Keep personal details out of tracked files.** No email addresses, device serials, account
  names, or health details in code, docs, commit messages, or test data.
- **Never print or move secrets.** Signing material lives in encrypted repository secrets and a
  local keystore. Do not paste key material, passwords or secret values into a chat, log or file.
- **Don't change release identity.** The release signing certificate is pinned by
  `EXPECTED_SIGNER` in `release.yml`. Android refuses an update signed by a different key, so a
  change there strands every existing install.
- **Merging and publishing need explicit approval.** Opening a pull request and running a dry run
  are fine. Merging to `main`, pushing a `v*` tag, or running a real release are outward-facing and
  wait for the maintainer's go-ahead.

## Build, test, lint

Requires **JDK 21** (the Paparazzi screenshot-test plugin won't configure on an older JDK). The
bytecode still targets Java 17.

```
./gradlew :app:assembleDebug          # debug APK (installs as com.robcloud.bloodpressure.debug)
./gradlew :app:testDebugUnitTest      # unit tests (JUnit 4)
./gradlew :app:verifyPaparazziDebug   # compare Compose snapshots with committed baselines
./gradlew :app:lintDebug              # Android lint; CI fails on errors
```

- After an intentional UI change, re-record with `./gradlew :app:recordPaparazziDebug`, but first
  look at the diff images in `app/build/reports/paparazzi`. A diff is a regression until you have
  decided otherwise.
- The release build is unsigned unless `keystore.properties` exists at the project root.

## Conventions

- **Versions.** Bump `versionCode` (must increase every release) and `versionName` in
  `app/build.gradle.kts`. Add a `CHANGELOG` entry for the new `versionName` in `AboutScreen.kt`;
  the release workflow reads the notes from there and fails without one. Use literal characters
  (`—`, `·`) in that file, not Kotlin `\u` escapes.
- **Database.** Room schemas are exported to `app/schemas/` and committed. Any schema change needs
  a real `Migration` committed with the new schema JSON. The app refuses to open a database it has
  no migration for, and it must never wipe the reading history.
- **Backups.** The CSV format is user data. Changes to `backup/` must keep older files readable and
  must never overwrite a file the app doesn't own.
- **Tests.** Add unit tests next to the logic they cover. Pure logic (categories, stats, CSV,
  merge, scheduling math) is tested without Android; UI is pinned by Paparazzi snapshots.
- **Style.** Match the surrounding code: naming, comment density and idiom. Comments say *why*,
  not what.
- **Docs.** When behaviour or screens change, update the README feature list and, where the UI
  changed, the screenshots (see below).

## Screenshots

`screenshots/` is shown in the README. To regenerate:

1. Use a debug build, which is safe to seed: its database is separate from the release app's.
2. Fill it with demo readings and notes, not real ones. Run the app on an emulator or a test device.
3. Capture the five screens named in the README table with `adb exec-out screencap -p`, then
   check each one for real data, device names or account names before committing.
4. The About screen shows the debug version string (`2.x.y-debug`). Say so in the README if that is
   the build used.

## Releasing

Follow [`.github/workflows/README-release.md`](.github/workflows/README-release.md). In short: bump
the version, add the changelog entry, merge to `main`, then run the **Release** workflow (a dry run
first, from a branch if you like). The workflow refuses to publish when tests, snapshots, the
changelog, the version order or the signing certificate don't check out.

## Repository layout

```
app/src/main/java/com/robcloud/bloodpressure/
  backup/       CSV sync to the user's folder, merge plan, snapshots, SAF access
  data/         Room entities, DAOs, converters, category logic, DB upgrade backup
  reminders/    daily reminder scheduling, notifications, time-change handling
  report/       PDF doctor's report
  ui/           Compose screens: capture, history, log, notes, about, theme
  update/       in-app update check and install via GitHub Releases
  widget/       home-screen widget showing the last reading
app/src/test/   unit tests and Paparazzi snapshot tests (baselines in snapshots/images)
app/schemas/    exported Room schemas (committed)
.github/workflows/  release pipeline
```
