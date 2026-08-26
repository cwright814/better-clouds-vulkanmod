import zipfile
import os

with zipfile.ZipFile(os.path.expanduser("~/.local/share/PandoraLauncher/instances/26.1.2/.minecraft/mods/VulkanMod-0.6.8+26.1.2.jar"), "r") as z:
    for name in z.namelist():
        if "Pipeline.class" in name:
            print("Found Pipeline.class:", name)
