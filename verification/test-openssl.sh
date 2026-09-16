#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to Linux NDK r27c}"
CRYPTO="$ROOT/third_party/openssl/android-arm64"
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/quest-crypto-test.XXXXXXXX")"
trap 'rm -rf -- "$BUILD"' EXIT
"$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang" \
  -static -fno-emulated-tls -DOPENSSL_API_COMPAT=0x10101000L \
  -I"$CRYPTO/include" -I"$ROOT/third_party/RPiPlay/lib" \
  "$ROOT/airplay/src/main/cpp/crypto_migration_test.c" \
  "$ROOT/third_party/RPiPlay/lib/crypto.c" "$CRYPTO/lib/libcrypto.a" \
  -ldl -o "$BUILD/crypto_migration_test"
# Tests Android arm64/Bionic code under CPU emulation, not host OpenSSL.
# This is not a replacement for an actual AirPlay session on a Quest.
qemu-aarch64 "$BUILD/crypto_migration_test"
