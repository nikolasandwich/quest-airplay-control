#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION=3.5.8
SHA256=a8f84a39918ec6415ce765d9b429d313ba97b8143169c172e734b9514464f5b2
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to the Linux Android NDK r27c directory}"
grep -Eq 'Pkg.Revision *= *27\.2\.12479018' "$ANDROID_NDK_ROOT/source.properties" || {
  echo 'Expected NDK r27c (27.2.12479018)' >&2; exit 1;
}
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$TOOLCHAIN/clang" || { echo 'Use Linux or WSL with the Linux NDK' >&2; exit 1; }
for tool in curl sha256sum tar perl make; do command -v "$tool" >/dev/null; done
DEST="$ROOT/third_party/openssl"
mkdir -p "$DEST"
ARCHIVE="$DEST/openssl-$VERSION.tar.gz"
if [ ! -f "$ARCHIVE" ]; then
  curl -fL --retry 3 "https://github.com/openssl/openssl/releases/download/openssl-$VERSION/openssl-$VERSION.tar.gz" -o "$ARCHIVE.part"
  mv "$ARCHIVE.part" "$ARCHIVE"
fi
echo "$SHA256  $ARCHIVE" | sha256sum -c -
# Compile on the Linux filesystem (also avoids slow WSL /mnt/c object writes).
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/quest-openssl.XXXXXXXX")"
trap 'rm -rf -- "$BUILD"' EXIT
tar -xzf "$ARCHIVE" -C "$BUILD"
cd "$BUILD/openssl-$VERSION"
export ANDROID_NDK_ROOT
export PATH="$TOOLCHAIN:$PATH"
perl Configure android-arm64 -D__ANDROID_API__=28 \
  --prefix="$BUILD/install" --libdir=lib \
  no-shared no-module no-tests no-apps -fPIC
make -j"${JOBS:-4}" build_libs
make install_dev
mkdir -p "$DEST/android-arm64"
cp -R "$BUILD/install/include" "$DEST/android-arm64/"
mkdir -p "$DEST/android-arm64/lib"
cp "$BUILD/install/lib/libcrypto.a" "$DEST/android-arm64/lib/"
cp LICENSE.txt "$DEST/android-arm64/"
printf 'OpenSSL %s\nsource-sha256=%s\nNDK=27.2.12479018\nABI=arm64-v8a\nAPI=28\n' "$VERSION" "$SHA256" > "$DEST/android-arm64/BUILD-INFO.txt"
echo "Built OpenSSL $VERSION for Android arm64-v8a in $DEST/android-arm64"
