# Release checklist

- Preserve GPL-3.0-or-later, authorship and third-party notices.
- Verify the pinned OpenSSL 3 source archive and include its Apache-2.0 notice; see OPENSSL.md.
- Run the Android crypto smoke executable and real AirPlay pairing/video/audio checks on a device.
- Review all branches/tags and history intended for publication for private data.
- Build in a clean directory, run unit tests and lint, record exact evidence.
- Label Preview/Alpha; retain known limitations and do not claim instant headset-doff protection.
- Supply matching complete corresponding source, dependencies, patches and licenses with binaries.
- Keep signing keys external to Git and backed up securely.
- Sanitize screenshots/logs. Review the scope before changing repository visibility.
