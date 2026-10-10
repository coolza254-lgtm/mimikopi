# Release signing

- One release key for the lifetime of the app: PKCS12, RSA 4096, alias `mimikopi`,
  subject `CN=Mimikopi, O=Id, C=TH`, valid 2026-10-10 → 2059-08-18.
- **Certificate SHA-256** (public, safe to share; used for developer-verification registration):
  `D7:CC:98:CA:21:A3:28:96:32:30:D8:9B:DF:F5:D6:35:D8:AD:79:5C:0C:51:17:07:70:6F:CC:2B:AA:C5:14:FE`
- The keystore and its password exist **only** in the user's offline backups and in this repo's
  GitHub Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`). Never in git.
- If the key is lost, installed copies can't be updated: the app would need a new package name and a
  fresh install (progress restored from a JSON backup).
- `RELEASES_TOKEN`: fine-grained personal access token, repository access **only**
  `coolza254-lgtm/mimikopi-releases`, permission **Contents: Read and write**. Renew it before it expires.
