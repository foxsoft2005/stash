# Security policy

Stash keeps people's belongings, photos and "who borrowed what" on their phone,
and its backups are encrypted with a user password. Security reports are very
welcome.

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately instead:

1. Go to the repository's **Security** tab › **Report a vulnerability**
   (GitHub private vulnerability reporting).
2. Describe the problem, how to reproduce it, and what an attacker could do.

You'll get a reply within a week. Please give us a reasonable time to fix it
before disclosing it publicly; you'll be credited unless you'd rather not be.

## What's in scope

Especially interesting:

- **Backup files** (`.stash`, see `app/src/main/java/io/github/foxsoft2005/stash/data/backup/`
  and the format notes in the README): recovering contents without the password,
  forging or modifying a backup that restores without error, crafted files that
  write outside the app's storage, crash or exhaust the phone on restore.
- **Data leaving the device**: any path by which items, photos or the database
  reach the network or other apps. Stash has no `INTERNET` permission (CI checks
  every built APK; libraries asking for it are overridden) and excludes its data
  from Android cloud backup; a way around either counts. Photos are handed to
  the system's AICore service for Gemini Nano on supported phones; issues in
  AICore itself belong with Google.
- **The capture shortcut and other exported entry points** being usable by
  other apps.

Out of scope: attacks that need an unlocked phone with root or developer
access, and the security of Android, AICore/Gemini Nano or ML Kit themselves
(please report those to Google).

### App lock

App lock (optional) keeps the screens away from someone who picks up your
unlocked phone. It uses Android's own prompt (fingerprint, face or the phone's
screen lock) and isn't encryption: the data is protected at rest by Android's
app sandbox and the phone's storage encryption, exactly as without the lock.
So bypassing App lock with code running on the phone (root, a debugger, a
hooking framework) is out of scope: that access can read the data directly.
Getting past the lock screen *without* such access (e.g. a screen or flow that
shows items while locked) is in scope.

## Supported versions

Only the latest code on `main` is supported.
