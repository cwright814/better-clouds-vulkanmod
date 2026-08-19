# Better Clouds Agent Instructions

## Build System
The mod uses a standard Gradle Fabric environment. You can build the project using `./build.sh` which dynamically reads the version from `gradle.properties` and outputs it directly to the sandbox directory.

## Versioning
To bump the version of the mod, use `./bump-version.sh <new_version>`. This will update the version string in `gradle.properties`. 
(Note: `fabric.mod.json` automatically expands `${version}` via Gradle).
