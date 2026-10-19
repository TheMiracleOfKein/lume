# Security policy

## Supported versions

Only the [latest release](https://github.com/TheMiracleOfKein/lume/releases/latest) gets fixes.

## Reporting a vulnerability

Please don't open a public issue for security problems. Report them privately through GitHub: **Security → Report a vulnerability** on this repository ([direct link](https://github.com/TheMiracleOfKein/lume/security/advisories/new)).

Include what is affected, how to reproduce it and the Lume version. You should get an answer within a week.

## Verifying releases

Release APKs are signed with the certificate `CN=Lume App`, SHA-256 fingerprint:

```
b1594cec70301bfd0f66d73d82e9bdcea6355b8df8d2abe50e1fc022629bd565
```

Check a download with `apksigner verify --print-certs <apk>`. Android also refuses to install an update signed with a different key over an existing Lume install.

## Scope

In scope: the Lume app and this repository (for example token leaks, unsafe handling of shared links or backups, WebView issues).

Out of scope: the third-party sites and trackers Lume connects to; report those to their owners.
