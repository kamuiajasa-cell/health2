# Health2 Android release signing

Health2 release APKs are signed with one persistent Android keystore. Keep the keystore and its passwords private and backed up. Never regenerate the keystore for an app already installed with this signing identity.

## Configure GitHub Actions

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and create:

| Secret name | Value |
|---|---|
| `HEALTH2_KEYSTORE_BASE64` | The complete one-line contents of the private `health2-release.base64` file |
| `HEALTH2_KEYSTORE_PASSWORD` | The keystore password supplied with the private key |
| `HEALTH2_KEY_ALIAS` | `health2-release` |
| `HEALTH2_KEY_PASSWORD` | The private-key password supplied with the private key |

After all four secrets are saved, open **Actions → Build Health2 signed APK → Run workflow**. Download the `Health2-signed-release-apk` artifact. Future builds use the same signing identity, so Android can install them as updates.

## Important migration note

Older APKs built by the former workflow used the runner's temporary debug key. That key was not persisted between builds. Android cannot update an installation signed by a different key. Before moving to the first persistent release-signed APK:

1. In the installed Health2 app, export a JSON backup from Profile.
2. Uninstall the old Health2 app once.
3. Install the first `Health2-signed-release-apk`.
4. Import the JSON backup from Profile.

After this one-time migration, do not uninstall the app for normal updates, and never lose or replace the release keystore. Losing it means future APKs cannot update the installed app.
