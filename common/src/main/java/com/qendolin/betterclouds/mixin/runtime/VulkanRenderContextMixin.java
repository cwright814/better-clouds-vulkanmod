package com.qendolin.betterclouds.mixin.runtime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.braffolk.dhvulkan.core.VulkanRenderContext", remap = false)
public class VulkanRenderContextMixin {
    @Inject(method = "readShaderResource", at = @At("RETURN"), cancellable = true)
    private static void onReadShader(String path, CallbackInfoReturnable<String> cir) {
        if (path.equals("shaders/vulkan/dh_terrain.frag")) {
            String original = cir.getReturnValue();
            
            String injection = "\n" +
                "layout(binding = 1) uniform UBO2 {\n" + // Binding 1 might be free in DH! Or we use the global Uniforms? Wait, DH might have its own bindings. Let's check dh_terrain.frag for bindings!
                "    float CloudOriginX;\n" +
                "    float CloudOriginZ;\n" +
                "    float Cloudiness;\n" +
                "    float CloudScale;\n" +
                "    float CloudShadowsEnabled;\n" +
                "    float CloudTime;\n" +
                "};\n" +
                "\n" +
                "vec3 permute_c(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }\n" +
                "float snoise_c(vec2 v) {\n" +
                "  const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);\n" +
                "  vec2 i  = floor(v + dot(v, C.yy));\n" +
                "  vec2 x0 = v -   i + dot(i, C.xx);\n" +
                "  vec2 i1;\n" +
                "  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);\n" +
                "  vec4 x12 = x0.xyxy + C.xxzz;\n" +
                "  x12.xy -= i1;\n" +
                "  i = mod(i, 289.0);\n" +
                "  vec3 p = permute_c( permute_c( i.y + vec3(0.0, i1.y, 1.0 )) + i.x + vec3(0.0, i1.x, 1.0 ));\n" +
                "  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);\n" +
                "  m = m*m ; m = m*m ;\n" +
                "  vec3 x = 2.0 * fract(p * C.www) - 1.0;\n" +
                "  vec3 h = abs(x) - 0.5;\n" +
                "  vec3 ox = floor(x + 0.5);\n" +
                "  vec3 a0 = x - ox;\n" +
                "  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );\n" +
                "  vec3 g;\n" +
                "  g.x  = a0.x  * x0.x  + h.x  * x0.y;\n" +
                "  g.yz = a0.yz * x12.xz + h.yz * x12.yw;\n" +
                "  return 130.0 * dot(m, g);\n" +
                "}\n" +
                "float computeCloudShadow(vec2 worldPos) {\n" +
                "    if (CloudShadowsEnabled <= 0.0) return 0.0;\n" +
                "    vec2 relPos = worldPos - vec2(CloudOriginX, CloudOriginZ);\n" +
                "    vec2 nxz = (relPos / CloudScale) / 128.0 + vec2(0.5);\n" +
                "    float value = snoise_c(nxz) * 0.5 + snoise_c(nxz * 2.0) * 0.25;\n" +
                "    value = value / 2.0 + 0.5;\n" +
                "    value = (value - (1.0 - Cloudiness)) / Cloudiness;\n" +
                "    float coverage = snoise_c((relPos / 1024.0) + vec2(0.5));\n" +
                "    float edge0 = -0.6 * Cloudiness - 0.3;\n" +
                "    float edge1 = -0.6 * Cloudiness;\n" +
                "    float x = clamp((coverage - edge0) / (edge1 - edge0), 0.0, 1.0);\n" +
                "    float coverageSmooth = x * x * (3.0 - 2.0 * x);\n" +
                "    value *= coverageSmooth;\n" +
                "    return clamp(value, 0.0, 1.0);\n" +
                "}\n";
                
            original = original.replaceFirst("(#version 450)", "$1" + injection);
            
            original = original.replace(
                "if (uDitherDhRendering != 0)",
                "float shadow = computeCloudShadow(vertexWorldPos.xz);\n" +
                "if (shadow > 0.0) fragColor.rgb = mix(fragColor.rgb, fragColor.rgb * 0.5, shadow);\n" +
                "if (uDitherDhRendering != 0)"
            );
            
            cir.setReturnValue(original);
        }
    }
}
