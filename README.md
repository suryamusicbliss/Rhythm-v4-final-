# Rhythm 4.0 — Platinum Blue

Native Android project for phone-based GitHub Actions APK builds.

## Implemented in this source
- Premium dark Platinum Blue interface
- Jamendo catalogue search
- Track playback and foreground media notification
- Previous / play-pause / next controls
- Full-screen now-playing dialog and seek control
- Persistent favourites
- Create, rename, delete playlists
- Add searched tracks to playlists
- Provider-flag-gated downloads through Android Download Manager

## Important catalogue and release limits
This app uses Jamendo's independent catalogue, not a licensed mainstream commercial catalogue.
A track is offered for download only if the API response marks `audiodownload_allowed` true and supplies a download URL. This is a provider flag, not a substitute for checking the specific track licence and terms. Offline downloads are saved in Android's Downloads folder; the app does not yet provide an in-app local-file scanner/player.
Commercial mainstream streaming requires direct contracts, catalogue rights, and usually provider credentials/SDK approval. Those rights cannot be included in a source ZIP.
This project is a debug build template. Before public release, verify API terms, privacy disclosures, licences, branding, signing, and Play Store policy.

## Build
Push the complete project to a GitHub repository's `main` branch. Actions builds `:app:assembleDebug` and uploads `rhythm-v4-debug-apk`.
