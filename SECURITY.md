# Security and data handling

Experimental software; not a comprehensive security audit. Use a trusted LAN and
do not expose receiver or diagnostic ports to the Internet.

Debug builds only: read-only HTTP diagnostics run on port 8765, bound to all interfaces without
authentication. LAN peers can read status, language and session identifier. There
is no HTTP mouse-control endpoint. Release builds do not start this diagnostic server.

Frames and pointer analysis are processed locally. Preferences include language,
control settings and Bluetooth peer data. Logs may contain network/device
identifiers and pointer coordinates. Sanitize all shared logs and screenshots.

Loss of input context disarms mouse input; neutral releases may finish held buttons.
Physical headset removal may precede Android notifications: pause control first.

Report vulnerabilities privately to nikolasandwich@gmail.com with revision and
minimal reproduction, without credentials or private captures. No response-time
guarantee. Legacy OpenSSL and native parsers require further review before a
supported public binary release.
