# Better Clouds Agent Instructions

## Build System
The mod uses a standard Gradle Fabric environment. You can build the project using `./build.sh` which dynamically reads the version from `gradle.properties` and outputs it directly to the sandbox directory.

## Versioning
To bump the version of the mod, use `./bump-version.sh <new_version>`. This will update the version string in `gradle.properties`. 
(Note: `fabric.mod.json` automatically expands `${version}` via Gradle).

### Important Findings
*   **VulkanMod ClassLoader Quirk:** VulkanMod uses `Class.getResource("/assets/vulkanmod")` to locate its shader root directory at runtime. If a resource pack or mod jar (like Better Clouds) contains an `/assets/vulkanmod` folder and is loaded earlier in the classpath, Java's `ClassLoader` returns the URL for that mod's jar instead. This blinds VulkanMod, causing it to exclusively look for all its internal `.json` configs and `.fsh` files inside the interfering mod's jar, which leads to `NullPointerException` crashes when it fails to find essential pipeline configs like `terrain.json`.
*   **Solution:** Never place an `assets/vulkanmod` directory in your mod jar if you intend to override VulkanMod shaders. Instead, place them in a custom directory (e.g., `assets/betterclouds/shaders/vulkanmod/`) and use a Mixin targeting `net.vulkanmod.render.shader.ShaderLoadUtil.getInputStream(String path)` to dynamically intercept the file request and return your custom shader's `InputStream`.
