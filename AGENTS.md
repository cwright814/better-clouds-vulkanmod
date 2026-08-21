# Better Clouds Agent Instructions

## Build System
The mod uses a standard Gradle Fabric environment. You can build the project using `./build.sh` which dynamically reads the version from `gradle.properties` and outputs it directly to the sandbox directory.

## Versioning
To bump the version of the mod, use `./bump-version.sh <new_version>`. This will update the version string in `gradle.properties`. 
(Note: `fabric.mod.json` automatically expands `${version}` via Gradle).

### Important Findings
*   **VulkanMod ClassLoader Quirk:** VulkanMod uses `Class.getResource("/assets/vulkanmod")` to locate its shader root directory at runtime. If a resource pack or mod jar (like Better Clouds) contains an `/assets/vulkanmod` folder and is loaded earlier in the classpath, Java's `ClassLoader` returns the URL for that mod's jar instead. This blinds VulkanMod, causing it to exclusively look for all its internal `.json` configs and `.fsh` files inside the interfering mod's jar, which leads to `NullPointerException` crashes when it fails to find essential pipeline configs like `terrain.json`.
*   **Solution:** Never place an `assets/vulkanmod` directory in your mod jar if you intend to override VulkanMod shaders. Instead, place them in a custom directory (e.g., `assets/betterclouds/shaders/vulkanmod/`) and use a Mixin targeting `net.vulkanmod.render.shader.ShaderLoadUtil.getInputStream(String path)` to dynamically intercept the file request and return your custom shader's `InputStream`.

*   **Mixin Plugins:** Better Clouds uses `RuntimeMixinPlugin.java` to dynamically register mixins at runtime based on `ModLoader.isModLoaded(...)`. If you delete a mixin class (e.g., `VulkanRenderContextMixin`) and remove it from `betterclouds.runtime.mixins.json`, you **MUST ALSO** remove its hardcoded string from `RuntimeMixinPlugin.java`. If you forget, Fabric Loader will throw a "FAILED during PREPARE" crash because the plugin dynamically attempts to load the deleted class.
*   **VulkanMod/Beryl Custom Uniforms (UBOs):** Vulkan does not support loose GLSL uniforms like OpenGL (e.g., `uniform float MyVar;`). All uniforms must be grouped into `layout(binding = X) uniform UniformBlockName { ... };`. To pass new variables from Java to a VulkanMod shader, you must do two things:
    1. Mixin to the shader pipeline config (e.g., `BerylPipelineConfigsMixin.java` targeting `net.beryl.render.pipeline.PipelineConfigs`) and register the uniform structure using `builder.addUniform("float", "MyNewVar")`.
    2. Register a lambda supplier in Java that provides the value to `net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap` (or `vec2f_uniformMap`, etc.) before the frame renders.
*   **Synchronizing CPU & GPU Noise (Simplex):** Java's default `PerlinSimplexNoise` (which uses permutation lookup tables) generates visually and mathematically different terrain shapes than standard GLSL implementations like Ashima's webgl-noise `snoise`. If you need to perfectly synchronize CPU-generated block coordinates (like physical clouds) with a GPU fragment shader (like cloud shadows projected on terrain), you must port the exact GLSL `snoise_c` logic into Java (see `SNoise.java`). When porting GLSL swizzling to Java, be very careful with vectors (e.g., `C.xxzz` means `vec4(C.x, C.x, C.z, C.z)`).

### VulkanMod Descriptor Set Padding & Contiguous Bindings
When injecting a custom UBO (like `CloudUBO`) into a VulkanMod pipeline via JSON (like Beryl's `entity.json`), you **MUST NOT** use arbitrary high binding numbers (like `binding = 8`) and attempt to pad the gaps with "Dummy" UBOs.
VulkanMod's `Pipeline.java` calculates the capacity of the `VkDescriptorSetLayoutBinding` memory buffer strictly based on the *total number* of UBOs and Samplers defined (`buffers.size() + imageDescriptors.size()`). However, when writing to this layout buffer, it uses the explicit `binding` number as the memory index (`buffer.get(bindingId)`).
If you have gaps in your bindings (e.g., UBOs 0, 1, 8 and Samplers 2, 3, 4, 5), the capacity is 7. Accessing `buffer.get(8)` will throw an `IndexOutOfBoundsException` or cause silent memory corruption (which can result in shaders rendering entities completely invisibly).

**Solution:** Always shift your custom UBOs to use the very next available contiguous binding index, and shift the Samplers accordingly.
For example, if Vanilla uses UBOs `0, 1`:
1. Set your custom UBO to `binding: 2`.
2. VulkanMod parses Samplers automatically starting at `lastUboBinding + 1`. So, place your UBOs at the *end* of the JSON array: `0, 1, 2`. The parser will naturally assign your Samplers to bindings `3, 4, 5, 6`.
3. Update the `.vsh` and `.fsh` GLSL files to match the new contiguous bindings (e.g., change `Sampler1` to `binding = 3`, `CloudUBO` to `binding = 2`).

### VulkanMod Fragment Shader Location Limits
When adding new `layout(location = X) in/out` variables to shaders, be cautious not to choose arbitrarily high locations (e.g. `location = 14`). Complex structs (like Beryl's `Material`) implicitly consume multiple contiguous locations depending on their field count and types. Choosing a lower, tightly-packed location (e.g. `location = 12`) prevents silent background compilation failures or memory corruption on GPUs with lower `maxFragmentInputComponents` limits.
