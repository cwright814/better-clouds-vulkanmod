package com.qendolin.betterclouds.clouds.vulkan;

import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.util.MappedBuffer;
import java.util.Map;
import java.util.HashMap;

public class VulkanShader {
    private GraphicsPipeline pipeline;
    private final Map<String, MappedBuffer> uniformBuffers = new HashMap<>();
    
    // VK_SHADER_STAGE_VERTEX_BIT = 1, VK_SHADER_STAGE_FRAGMENT_BIT = 16 -> 17
    public static final int STAGE_ALL = 17;

    public void init(String vshSrc, String fshSrc) {
        Pipeline.Builder builder = new Pipeline.Builder(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, "betterclouds");
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vshSrc);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fshSrc);

        net.vulkanmod.vulkan.shader.layout.AlignedStruct.Builder structBuilder = new net.vulkanmod.vulkan.shader.layout.AlignedStruct.Builder();
        String[] types = {"mat4", "mat4", "mat4", "mat4", "mat4", "vec4", "vec4", "vec4", "vec3", "float", "vec4", "vec3", "float", "vec3", "float", "vec4", "vec3", "float", "vec3", "float", "vec2", "vec2"};
        String[] names = {"u_vp_matrix", "u_mv_matrix", "u_mc_p_matrix", "u_dh_p_matrix", "u_mvp_matrix", "u_bounding_box", "u_sun_direction", "u_color_grading", "u_origin_offset", "u_time", "u_miscellaneous", "u_sun_axis", "u_noise_factor", "u_opacity", "u_is_fancy", "u_moon_direction", "u_moon_axis", "u_density_edge", "u_tint", "u_density_center", "u_fog_range", "u_depth_range"};

        for (int i = 0; i < types.length; i++) {
            net.vulkanmod.vulkan.shader.layout.Uniform.Info info = net.vulkanmod.vulkan.shader.layout.Uniform.createUniformInfo(types[i], names[i]);
            MappedBuffer buffer = new MappedBuffer(info.size * 4); // MappedBuffer allocates bytes, info.size is in floats/dwords
            uniformBuffers.put(info.name, buffer);
            info.setBufferSupplier(() -> buffer);
            structBuilder.addUniform(info);
        }

        net.vulkanmod.vulkan.shader.descriptor.UBO ubo = structBuilder.buildUBO("CloudUBO", 0, STAGE_ALL);
        builder.addUBO(ubo);
        builder.addImageDescriptor(new net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor(1, "sampler2D", "u_noise_texture", 1, 1));
        builder.addImageDescriptor(new net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor(2, "sampler2D", "u_light_texture", 2, 1));

        pipeline = builder.createGraphicsPipeline();
    }

    public void setUniformMat4(String name, org.joml.Matrix4f mat) {
        MappedBuffer buf = uniformBuffers.get(name);
        if (buf != null) {
            mat.get(buf.buffer);
        }
    }

    public void setUniformVec4(String name, org.joml.Vector4f vec) {
        MappedBuffer buf = uniformBuffers.get(name);
        if (buf != null) {
            vec.get(buf.buffer);
        }
    }

    public void setUniformVec3(String name, org.joml.Vector3f vec) {
        MappedBuffer buf = uniformBuffers.get(name);
        if (buf != null) {
            vec.get(buf.buffer);
        }
    }

    public void setUniformVec2(String name, org.joml.Vector2f vec) {
        MappedBuffer buf = uniformBuffers.get(name);
        if (buf != null) {
            vec.get(buf.buffer);
        }
    }

    public void setUniformFloat(String name, float val) {
        MappedBuffer buf = uniformBuffers.get(name);
        if (buf != null) {
            buf.buffer.putFloat(0, val);
        }
    }

    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    public void cleanUp() {
        if (pipeline != null) {
            pipeline.cleanUp();
            pipeline = null;
        }
    }
}
