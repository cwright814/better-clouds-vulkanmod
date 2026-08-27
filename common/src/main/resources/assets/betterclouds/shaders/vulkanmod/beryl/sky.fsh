#version 450

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec3 LightDir;
    vec3 LightColor;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    float NightFactor;
    float LightVisibility;
};

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec3 fragPos;

layout(location = 0) out vec4 fragColor;

layout(binding = 3) uniform sampler2D Sampler1;
layout(binding = 2) uniform CloudUBO {
    float BerylSkyDithering;
    float BerylSkyDitheringType;
    float BerylSkyDitheringTriangle;
    float BerylSkyDitheringJitter;
};


#include "lighting.glsl"

vec3 getSunColor(const float RdotL) {
    float f = 0.1 / (5000 * (0.9994 - RdotL) + 0.0001) - 0.005;
    f = clamp(f, 0.0, 1.0);
    float m = RdotL > 0.9994 ? 1.0 : 0.0;
    m += f;
    m *= LightVisibility;

    vec3 color = mix(vec3(0.0), LightColor * 5.0, m);
    return color;
}

vec3 getMoonColor(const float RdotL) {
    float f = 0.05 / (5000 * (0.9986 - RdotL) + 0.0001) - 0.005;
    f = clamp(f, 0.0, 1.0);
    float m = RdotL > 0.9986 ? 1.0 : 0.0;
    m += f;
    m *= LightVisibility;

    vec3 color = mix(vec3(0.0), LightColor * 3.0, m);
    return color;

    return color;
}

void main() {
    //TODO precompute and optimize
    vec3 fragDir = normalize(fragPos);
    float RoUp = dot(fragDir, UpVector);
    RoUp = max(RoUp, 0.0);

    float SoUp = max(dot(UpVector, LightDir), 0.0);

    float RdotL = max(dot(fragDir, LightDir), 0.0);

    fragColor = getSkyColor(RdotL, RoUp, SoUp);

    #ifndef TEXTURED_SUN
//        fragColor.rgb += NightFactor == 1.0  ? getMoonColor(RdotL) : getSunColor(RdotL);
        fragColor.rgb += NightFactor == 1.0  ? vec3(0.0) : getSunColor(RdotL);
    #endif

    if (BerylSkyDithering > 0.0) {
        float dither = 0.0;
        
        // Helper macros
        #define IGN(p) fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))))
        #define WHITE(p) fract(fract(dot(p, vec2(0.1031, 0.1030))) * 33.33) // simplified hash for inline use
        
        float rand1 = 0.0;
        float rand2 = 0.0;
        
        // Calculate jitter offset using golden ratio to avoid visible diagonal sliding
        vec2 jitterOffset = BerylSkyDitheringJitter > 0.0 ? vec2(mod(BerylSkyDitheringJitter * 0.61803398875, 1.0), mod(BerylSkyDitheringJitter * 0.38196601125, 1.0)) * 1000.0 : vec2(0.0);
        vec2 jitteredCoord = gl_FragCoord.xy + jitterOffset;
        vec3 jitteredCoord3 = vec3(jitteredCoord, gl_FragCoord.z);
        
        if (BerylSkyDitheringType < 0.5) { // 0 = WHITE
            vec3 p3 = fract(jitteredCoord3 * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            rand1 = fract((p3.x + p3.y) * p3.z);
            
            vec3 p3b = fract((jitteredCoord3 + vec3(17.13, 23.41, 19.55)) * 0.1031);
            p3b += dot(p3b, p3b.yzx + 33.33);
            rand2 = fract((p3b.x + p3b.y) * p3b.z);
        } else { // 1 = INTERLEAVED (IGN)
            rand1 = IGN(jitteredCoord);
            rand2 = IGN(jitteredCoord + vec2(17.13, 23.41));
        }
        
        if (BerylSkyDitheringTriangle > 0.5) {
            // Triangle distribution spanning [-1.0, 1.0] (2 quantization steps)
            dither = (rand1 + rand2) - 1.0;
        } else {
            // Uniform distribution spanning [-0.5, 0.5] (1 quantization step)
            dither = rand1 - 0.5;
        }
        
        // The framebuffer applies an sRGB gamma curve (approx ^(1/2.2)) automatically.
        // This causes linear additions in dark areas to be magnified by up to 10x!
        // We scale the dither magnitude by the sqrt of the luminance to ensure the perceived 
        // dither remains a constant 1-2 quantization steps across all brightness levels.
        float luma = max(dot(fragColor.rgb, vec3(0.299, 0.587, 0.114)), 0.0001);
        dither *= sqrt(luma);
        
        // Apply dither. Scale by 1.0/128.0 (2/256)
        fragColor.rgb += dither / 128.0;
    }
}
