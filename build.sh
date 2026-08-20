#!/bin/bash
set -e

# This script is hard-coded for my personal development environment. Providing it on GitHub solely as reference for how builds were generated. You will need to set up your own build workflow. An LLM can likely adapt this file for you if you are not sure where to begin.
# -Christopher

echo "Building fabric mod..."
JAVA_HOME=~/.jdks/graalvm-25.1.3+9.1 GRADLE_OPTS="--enable-native-access=ALL-UNNAMED" ./gradlew clean :fabric:build

echo "Copying to parent directory..."
MOD_VERSION=$(grep "^mod\.version=" gradle.properties | cut -d'=' -f2)

# Use find to locate the exact jar (ignoring -sources.jar) and copy it
find fabric/build/libs -name "better-clouds-*.jar" ! -name "*-sources.jar" -exec cp {} ../better-clouds-vulkanmod-${MOD_VERSION}+26.1.2-fabric.jar \;

echo "Build and copy complete!"
