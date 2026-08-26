import subprocess
output = subprocess.check_output(["~/.jdks/graalvm-25.1.3+9.1/bin/javap", "-cp", "/home/cwright/.local/share/PandoraLauncher/instances/26.1.2/.minecraft/mods/VulkanMod-0.6.8+26.1.2.jar", "-c", "net.vulkanmod.vulkan.shader.SPIRVUtils"], text=True)
print([line for line in output.split('\n') if 'binding' in line.lower()])
