# Releasing

Releases go to Maven Central through the Central Portal, from the `Release` workflow
(`.github/workflows/release.yml`), when a tag that starts with `v` is pushed. The workflow skips
publishing, with a warning, until the four secrets below exist.

JitPack needs no setup: it builds any tag on request, using `jitpack.yml`.

## One time setup

### 1. Central Portal account and namespace

1. Sign in at https://central.sonatype.com with the GitHub account `Hitice`.
2. Open **Namespaces**. Signing in with GitHub usually verifies `io.github.hitice` automatically.
   If it is not listed as verified, add it and follow the instructions (create a temporary public
   repository with the name the portal shows).
3. Open **Account** and choose **Generate User Token**. Keep the username and password it shows;
   they are not the account login.

### 2. GPG key

Central requires every file to be signed.

```bash
gpg --full-generate-key                 # RSA 4096, no expiry or a long one, a strong passphrase
gpg --list-secret-keys --keyid-format=long
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID> > private-key.asc
```

Confirm the e-mail on keys.openpgp.org if it asks. Delete `private-key.asc` after the next step.

### 3. Repository secrets

In GitHub, **Settings > Secrets and variables > Actions > New repository secret**:

| Secret | Value |
| --- | --- |
| `MAVEN_GPG_PRIVATE_KEY` | full content of `private-key.asc`, including the BEGIN and END lines |
| `MAVEN_GPG_PASSPHRASE` | passphrase of the GPG key |
| `CENTRAL_TOKEN_USERNAME` | user token username from step 1 |
| `CENTRAL_TOKEN_PASSWORD` | user token password from step 1 |

Signing uses the BouncyCastle signer of `maven-gpg-plugin`, so the runner does not need a gpg
keyring.

## Each release

1. Make sure `main` is green in CI.
2. Update the version in the README install snippets.
3. Tag and push:

   ```bash
   git tag -a v0.1.0 -m "v0.1.0"
   git push origin v0.1.0
   ```

4. The workflow sets the POM version from the tag (`0.1.0`), builds, tests, signs and uploads
   `pix-brcode-parent`, `pix-brcode` and `pix-brcode-qrcode`. The examples module is excluded.
5. The upload is validated but not published (`autoPublish` is `false`). Review the deployment at
   https://central.sonatype.com/publishing/deployments and press **Publish**. To publish without
   review, set `autoPublish` to `true` in the `release` profile of the root `pom.xml`.
6. Create a GitHub release for the tag with the notable changes.

Artifacts appear on Maven Central search some minutes after publishing.
