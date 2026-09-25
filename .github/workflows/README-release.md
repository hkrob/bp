# Releasing

`release.yml` builds, signs and publishes a release entirely on a GitHub runner. The signing
key never leaves encrypted repository secrets except inside the job that uses it.

## One-time setup

Four secrets are needed. Set them from the machine that holds `release.keystore` — never paste
key material into a chat, an issue, or a commit.

```powershell
cd C:\path\to\bp                     # the folder holding release.keystore
gh auth status                        # must be authenticated

gh secret set RELEASE_KEYSTORE_B64 --repo hkrob/bp `
  --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\release.keystore))))

gh secret set RELEASE_STORE_PASSWORD --repo hkrob/bp
gh secret set RELEASE_KEY_ALIAS      --repo hkrob/bp
gh secret set RELEASE_KEY_PASSWORD   --repo hkrob/bp
```

`Resolve-Path` matters: .NET methods such as `ReadAllBytes` resolve a relative path against the
process working directory, which is not necessarily the directory PowerShell has `cd`-ed to.
Passing a bare `"release.keystore"` can silently read from the wrong place or throw.

The last three commands prompt for the value, so it never reaches your shell history. They must
match the `storePassword`, `keyAlias` and `keyPassword` in your local `keystore.properties`.

Check the result with `gh secret list --repo hkrob/bp` — it shows names and update times only,
never values.

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a `CHANGELOG` entry for the new `versionName` in `AboutScreen.kt`. The workflow reads
   the release notes from it and fails if it is missing or empty. Write literal characters (`—`,
   `·`), not Kotlin `\u` escapes: only `\"` and `\\` are unescaped.
3. If the Room schema changed, commit the new `app/schemas/.../<version>.json` together with its
   `Migration`. The app refuses to open a database it has no migration for.
4. Merge to `main`.
5. Run the **Release** workflow (`workflow_dispatch`), or push a `v<versionName>` tag.

Tick **dry_run** to build, test and check the signature without publishing. Dry runs work from
any branch, which makes them a good check on a pull request before merging. A real release must
be dispatched on a commit that is on `main`.

### Local fallback: `publish-release.ps1`

If Actions is unavailable, `pwsh ./publish-release.ps1` does the same from the machine that holds
`release.keystore` (`-DryRun` stops short of publishing). It applies the same checks, plus:

- It must run on `main` with a clean tree, and not be behind `origin/main`. It pushes any local
  commits, then tags exactly the commit it built.
- `apksigner` must be found under the Android SDK. If it can't verify the signature, it doesn't
  publish.
- `-SkipTests` is only accepted together with `-DryRun`.

Creating the release this way pushes a tag, which starts the workflow. The workflow sees the
release already exists and finishes as a no-op.

## What the workflow refuses to do

- Publish when a release for that tag already exists. (A tag push for a release that already
  exists — which is what `publish-release.ps1` triggers when it creates the release — is skipped
  as a no-op rather than failing.)
- Publish when a pushed tag disagrees with `versionName`, or an existing tag points at a
  different commit than the one being built.
- Publish when `versionCode` is not higher than the latest published release's (read from that
  release's tag). Android won't install an update whose versionCode didn't go up. Dry runs check
  this too.
- Publish a commit that is not on `main`.
- Publish when there is no changelog entry for the version.
- Publish when tests or the Paparazzi snapshots fail (diffs are uploaded as an artifact).
- Publish an APK whose signing certificate is not `EXPECTED_SIGNER`. Android refuses to install
  an update signed with a different key, so shipping one would strand every existing install.

## Security notes

- Secrets are not exposed to workflow runs from forked pull requests, so the public repo is not
  a leak vector by itself.
- Anyone with **write** access can add a workflow that prints a secret. Keep the collaborator
  list tight; that is the real perimeter here.
- For a second gate, put the four secrets in a GitHub **Environment** with a required reviewer
  and add `environment:` to the job — the run then pauses for approval before it can read them.
- Rotating this key is painful and mostly one-way: `minSdk` is 26, and signing-certificate
  rotation only works on Android 9 (API 28) and above. On older devices a new key means an
  uninstall and reinstall, which destroys the local reading database. Treat the key as
  unrecoverable and back it up offline.
