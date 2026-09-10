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
   the release notes from it and fails if it is missing or empty.
3. Merge to `main`.
4. Run the **Release** workflow (`workflow_dispatch`), or push a `v<versionName>` tag.

Tick **dry_run** to build, test and check the signature without publishing.

## What the workflow refuses to do

- Publish when a release for that tag already exists.
- Publish when a pushed tag disagrees with `versionName`.
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
