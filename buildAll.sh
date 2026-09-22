#!/usr/bin/env sh
# Build both distributable jars in one go. Works on Linux/macOS and Git Bash on Windows.
#   - desktop: Jar-in-Jar LWJGL + bundled natives, version without suffix
#   - android: no embedded LWJGL / no natives (FCL ships them), version with -android suffix
set -e

echo "==> Building desktop jar (Jar-in-Jar LWJGL + bundled natives)"
./gradlew build -Pvulkanmod_android=false

echo "==> Building Android/FCL jar (no embedded LWJGL, no natives)"
./gradlew build -Pvulkanmod_android=true

echo "==> Done. Distributable jars:"
ls -1 build/libs/VulkanMod_1.20.1-*.jar 2>/dev/null | grep -v -- '-slim.jar' | grep -v -- '-sources.jar'
