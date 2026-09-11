#!/bin/sh
# Pin tested dependencies and apply the tracked Quest lifecycle fixes.
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/third_party"

prepare_dependency() {
    dest="$1"; url="$2"; revision="$3"
    if [ ! -d "$dest" ]; then
        git init "$dest"
        git -C "$dest" remote add origin "$url"
        git -C "$dest" fetch --depth 1 origin "$revision"
        git -C "$dest" checkout --detach FETCH_HEAD
    fi
    actual="$(git -C "$dest" rev-parse HEAD)"
    if [ "$actual" != "$revision" ]; then
        echo "Dependency revision mismatch: $dest ($actual, expected $revision). Preserve local changes and resolve manually." >&2
        exit 1
    fi
}

prepare_dependency "$ROOT/third_party/RPiPlay" https://github.com/FD-/RPiPlay 64d0341ed3bef098c940c9ed0675948870a271f9
prepare_dependency "$ROOT/third_party/libplist" https://github.com/libimobiledevice/libplist 2117b8fdb6b4096455bd2041a63e59a028120136

patch="$ROOT/patches/RPiPlay-quest-lifecycle.patch"
if git -C "$ROOT/third_party/RPiPlay" apply --check "$patch" 2>/dev/null; then
    git -C "$ROOT/third_party/RPiPlay" apply "$patch"
elif git -C "$ROOT/third_party/RPiPlay" apply --reverse --check "$patch" 2>/dev/null; then
    echo "Quest lifecycle patch already applied"
else
    echo "Cannot apply Quest lifecycle patch; inspect existing dependency changes." >&2
    exit 1
fi
echo "Dependencies ready. Next: ./gradlew :app:assembleDebug"
