package com.qendolin.betterclouds.clouds.vulkan;

import net.minecraft.server.packs.resources.ResourceManager;
import com.qendolin.betterclouds.BetterCloudsStatic;
import net.vulkanmod.vulkan.memory.buffer.VertexBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import com.qendolin.betterclouds.clouds.Mesh;

public class VulkanResources {
    public VertexBuffer cubeVbo;

    public void reloadMeshPrimitives() {
        deleteMeshPrimitives();

        cubeVbo = new VertexBuffer(Mesh.CUBE_MESH.length * 4, MemoryTypes.GPU_MEM);
        ByteBuffer byteBuffer = MemoryUtil.memAlloc(Mesh.CUBE_MESH.length * 4);
        byteBuffer.asFloatBuffer().put(Mesh.CUBE_MESH);
        cubeVbo.copyBuffer(byteBuffer, byteBuffer.capacity());
        MemoryUtil.memFree(byteBuffer);
    }

    public void deleteMeshPrimitives() {
        if (cubeVbo != null) {
            cubeVbo.scheduleFree();
            cubeVbo = null;
        }
    }

    public VulkanShader shader;
    public com.qendolin.betterclouds.clouds.ChunkedGenerator generator = new com.qendolin.betterclouds.clouds.ChunkedGenerator(0L);

    public void reloadGenerator(long seed) {
        if (generator != null) generator.close();
        generator = new com.qendolin.betterclouds.clouds.ChunkedGenerator(seed);
    }

    public void reloadShaders(ResourceManager manager) {
        if (shader != null) {
            shader.cleanUp();
        }
        shader = new VulkanShader();

        String vshSrc = readShader(manager, "betterclouds:shaders/clouds_vulkan.vsh");
        String fshSrc = readShader(manager, "betterclouds:shaders/clouds_vulkan.fsh");

        com.qendolin.betterclouds.config.Config config = com.qendolin.betterclouds.config.ConfigManager.instance();
        boolean dhCompat = com.qendolin.betterclouds.compat.DistantHorizonsCompat.instance().isReady() && com.qendolin.betterclouds.compat.DistantHorizonsCompat.instance().isEnabled();

        String sizeXZ = Float.toString(config.sizeXZ);
        String sizeY = Float.toString(config.sizeY);
        String positionalColoring = com.qendolin.betterclouds.compat.GLCompat.glCompat.useStencilTextureFallback() ? "0" : "1";
        String distantHorizons = dhCompat ? "1" : "0";
        String worldCurvature = Integer.toString(config.shaderPreset().worldCurvatureSize);

        vshSrc = vshSrc.replace("_SIZE_XZ_", sizeXZ)
                       .replace("_SIZE_Y_", sizeY)
                       .replace("_POSITIONAL_COLORING_", positionalColoring)
                       .replace("_DISTANT_HORIZONS_", distantHorizons)
                       .replace("_WORLD_CURVATURE_", worldCurvature);
                       
        fshSrc = fshSrc.replace("_SIZE_XZ_", sizeXZ)
                       .replace("_SIZE_Y_", sizeY)
                       .replace("_POSITIONAL_COLORING_", positionalColoring)
                       .replace("_DISTANT_HORIZONS_", distantHorizons)
                       .replace("_WORLD_CURVATURE_", worldCurvature);

        shader.init(vshSrc, fshSrc, dhCompat);
        
        // Generator needs an empty reload trigger if any resources change?
    }

    private String readShader(ResourceManager manager, String path) {
        try {
            net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.tryParse(path);
            java.util.Optional<net.minecraft.server.packs.resources.Resource> res = manager.getResource(loc);
            if (res.isPresent()) {
                try (java.io.InputStream is = res.get().open()) {
                    return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            BetterCloudsStatic.getLogger().error("Failed to read shader {}", path, e);
        }
        return "";
    }

    public void cleanUp() {
        if (shader != null) shader.cleanUp();
        if (expandedVbo != null) {
            expandedVbo.scheduleFree();
            expandedVbo = null;
        }
        deleteMeshPrimitives();
    }

    public net.vulkanmod.vulkan.memory.buffer.VertexBuffer expandedVbo;
    private int lastSwapCount = -1;

    public void updateExpandedBuffer(java.nio.FloatBuffer instancePosBuffer, int numInstances, boolean fancy) {
        if (generator.buffer() == null) return;
        int swapCount = generator.buffer().swapCount();
        if (swapCount == lastSwapCount) return; // No change
        lastSwapCount = swapCount;
        
        int verticesPerInstance = fancy ? 36 : 6;
        
        int totalVertices = numInstances * verticesPerInstance;
        int floatsPerVertex = 4; // in_pos(3) + padding(1) to match POSITION_COLOR (16 bytes)
        int requiredFloats = totalVertices * floatsPerVertex;
        int requiredBytes = requiredFloats * 4;
        
        if (expandedVbo == null || expandedVbo.getBufferSize() < requiredBytes) {
            if (expandedVbo != null) expandedVbo.scheduleFree();
            if (requiredBytes > 0) expandedVbo = new net.vulkanmod.vulkan.memory.buffer.VertexBuffer(requiredBytes, net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM);
        }
        
        if (requiredBytes == 0) return;
        
        java.nio.ByteBuffer byteBuf = org.lwjgl.system.MemoryUtil.memAlloc(requiredBytes);
        java.nio.FloatBuffer floatBuf = byteBuf.asFloatBuffer();
        
        instancePosBuffer.position(0);
        for (int i = 0; i < numInstances; i++) {
            float cx = instancePosBuffer.get();
            float cy = instancePosBuffer.get();
            float cz = instancePosBuffer.get();
            
            for (int v = 0; v < verticesPerInstance; v++) {
                // in_pos is just the cloud block coordinate
                floatBuf.put(cx).put(cy).put(cz).put(0f);
            }
        }
        floatBuf.flip();
        expandedVbo.reset();
        
        int maxUploadBytes = 60 * 1024 * 1024; // 60 MB chunks to fit in 64MB staging buffer
        int offset = 0;
        int bytesRemaining = requiredBytes;
        
        while (bytesRemaining > 0) {
            int uploadSize = Math.min(bytesRemaining, maxUploadBytes);
            byteBuf.position(offset);
            byteBuf.limit(offset + uploadSize);
            java.nio.ByteBuffer slice = byteBuf.slice();
            
            expandedVbo.copyBuffer(slice, uploadSize, offset);
            
            offset += uploadSize;
            bytesRemaining -= uploadSize;
        }
        
        org.lwjgl.system.MemoryUtil.memFree(byteBuf);
    }
}
