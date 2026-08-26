package com.qendolin.betterclouds.mixin.runtime;

import com.qendolin.betterclouds.BetterCloudsStatic;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;

import java.util.List;
import java.util.Map;

@Mixin(targets = "com.braffolk.dhvulkan.core.VulkanRenderContext")
public class DhVulkanContextMixin {

    private static final String CLOUD_SHADOW_CODE = """
// --- CLOUD SHADOWS NOISE ---
layout(binding = 3) uniform CloudUBO {
    float WindDriftX;
    float WindDriftZ;
    float Cloudiness;
    float CloudScale;
    float CloudShadowsEnabled;
    float CloudTime;
    float CameraX;
    float CameraZ;
    float CloudShadowIntensity;
    float NoiseOffsetX;
    float NoiseOffsetZ;
    float ShadowOffsetX;
    float ShadowOffsetZ;
    float ShadowRotation;
    float ShadowFlipX;
    float ShadowFlipZ;
    float ShadowScale;
};
vec3 permute_c(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }
float snoise_c(vec2 v) {
  const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
  vec2 i  = floor(v + dot(v, C.yy));
  vec2 x0 = v -   i + dot(i, C.xx);
  vec2 i1;
  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute_c( permute_c( i.y + vec3(0.0, i1.y, 1.0 )) + i.x + vec3(0.0, i1.x, 1.0 ));
  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
  m = m*m ; m = m*m ;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
  vec3 g;
  g.x  = a0.x  * x0.x  + h.x  * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}
float snoise_offset(vec2 v) {
  return snoise_c(v + vec2(NoiseOffsetX, NoiseOffsetZ));
}
float computeCloudShadow(vec2 wpos) {
    if (CloudShadowsEnabled <= 0.0 || CloudScale <= 0.0 || Cloudiness <= 0.0) return 0.0;
    if (isnan(CameraX) || isnan(CameraZ) || isnan(WindDriftX) || isnan(WindDriftZ)) return 0.0;
    
    vec2 absWpos = wpos + vec2(CameraX, CameraZ);
    vec2 samplePos = absWpos - vec2(WindDriftX, WindDriftZ);
    samplePos -= vec2(ShadowOffsetX, ShadowOffsetZ);
    
    if (ShadowFlipX > 0.5) samplePos.x = -samplePos.x;
    if (ShadowFlipZ > 0.5) samplePos.y = -samplePos.y;
    
    if (ShadowRotation != 0.0) {
        float r = ShadowRotation * 3.14159265359 / 180.0;
        float c = cos(r);
        float s = sin(r);
        samplePos = vec2(
            samplePos.x * c - samplePos.y * s,
            samplePos.x * s + samplePos.y * c
        );
    }
    
    float fScale = CloudScale * 128.0;
    vec2 nx = samplePos / (fScale * ShadowScale) + vec2(0.5);
    
    float value = snoise_offset(nx * 0.25) * 0.4
                + snoise_offset(nx * 0.5)  * 0.3
                + snoise_offset(nx)        * 0.2
                + snoise_offset(nx * 2.0)  * 0.1;
                
    value = value * 0.5 + 0.5;
    value = (value - (1.0 - Cloudiness)) / max(Cloudiness, 0.001);
    value = clamp(value, 0.0, 1.0);
    
    float coverage = snoise_offset(samplePos / 1024.0 + vec2(0.5));
    float covThresh = -0.6 * Cloudiness;
    float covMask = smoothstep(covThresh - 0.3, covThresh, coverage);
    value *= covMask;
    
    value = smoothstep(0.05, 0.6, value);
    return value;
}
""";

    @Redirect(
            method = "createTerrainPipeline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/vulkan/shader/Pipeline$Builder;createGraphicsPipeline()Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;",
                    remap = false
            ),
            remap = false
    )
    private GraphicsPipeline onBuildDhPipeline(Pipeline.Builder builder) {
        BetterCloudsStatic.getLogger().info("[BetterClouds] Injecting Cloud Shadows into Distant Horizons Terrain Pipeline");

        // 1. Add CloudUBO to binding 3 (DH uses 0, 1, and 2 is push constants or SectionOffsets)
        List<UBO> ubos = builder.getUBOs();
        
        AlignedStruct.Builder cloudStruct = new AlignedStruct.Builder();
        cloudStruct.addUniform("float", "WindDriftX", 1);
        cloudStruct.addUniform("float", "WindDriftZ", 1);
        cloudStruct.addUniform("float", "Cloudiness", 1);
        cloudStruct.addUniform("float", "CloudScale", 1);
        cloudStruct.addUniform("float", "CloudShadowsEnabled", 1);
        cloudStruct.addUniform("float", "CloudTime", 1);
        cloudStruct.addUniform("float", "CameraX", 1);
        cloudStruct.addUniform("float", "CameraZ", 1);
        cloudStruct.addUniform("float", "CloudShadowIntensity", 1);
        cloudStruct.addUniform("float", "NoiseOffsetX", 1);
        cloudStruct.addUniform("float", "NoiseOffsetZ", 1);
        cloudStruct.addUniform("float", "ShadowOffsetX", 1);
        cloudStruct.addUniform("float", "ShadowOffsetZ", 1);
        cloudStruct.addUniform("float", "ShadowRotation", 1);
        cloudStruct.addUniform("float", "ShadowFlipX", 1);
        cloudStruct.addUniform("float", "ShadowFlipZ", 1);
        cloudStruct.addUniform("float", "ShadowScale", 1);
        
        ubos.add(cloudStruct.buildUBO("CloudUBO", 3, 16));


        // 2. Modify the fragment shader source
        Map<SPIRVUtils.ShaderKind, String> shadersSrc = builder.getShadersSrc();
        if (shadersSrc != null && shadersSrc.containsKey(SPIRVUtils.ShaderKind.FRAGMENT_SHADER)) {
            String fragSrc = shadersSrc.get(SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
            
            // Inject the functions and UBO definition right after the version string or defines
            int injectIdx = fragSrc.indexOf("layout(location = 0) out vec4 fragColor;");
            if (injectIdx != -1) {
                fragSrc = fragSrc.substring(0, injectIdx) + CLOUD_SHADOW_CODE + fragSrc.substring(injectIdx);
            }
            
            // Apply the shadow to fragColor right before the end of main
            String shadowApplication = """
                    // Apply cloud shadow
                    float cShadow = computeCloudShadow(vertexWorldPos.xz);
                    if (cShadow > 0.0) {
                        float shadowFactor = mix(1.0, 1.0 - CloudShadowIntensity, cShadow);
                        fragColor.rgb *= shadowFactor;
                    }
                }""";
            
            // Replace the final closing brace of main
            int lastBraceIdx = fragSrc.lastIndexOf('}');
            if (lastBraceIdx != -1) {
                fragSrc = fragSrc.substring(0, lastBraceIdx) + shadowApplication + fragSrc.substring(lastBraceIdx + 1);
            }

            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragSrc);
        } else {
            BetterCloudsStatic.getLogger().error("[BetterClouds] Could not find fragment shader source for DH terrain pipeline!");
        }

        return builder.createGraphicsPipeline();
    }
}
