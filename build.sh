#!/bin/bash
set -e

echo "Building fabric mod..."
JAVA_HOME=~/.jdks/graalvm-25.1.3+9.1 ./gradlew clean :fabric:build

echo "Copying to sandbox directory..."
# Use find to locate the exact jar (ignoring -sources.jar) and copy it
find fabric/build/libs -name "better-clouds-*.jar" ! -name "*-sources.jar" -exec cp {} /home/cwright/Projects/sandbox/better-clouds-vulkanmod-1.15.0+26.1.2-fabric.jar \;

echo "Build and copy complete!"
