#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: ./bump-version.sh <new_version>"
    echo "Example: ./bump-version.sh 1.15.1"
    exit 1
fi

NEW_VERSION=$1

# Update gradle.properties
sed -i "s/^mod\.version=.*/mod.version=$NEW_VERSION/" gradle.properties

echo "Version bumped to $NEW_VERSION in gradle.properties"
