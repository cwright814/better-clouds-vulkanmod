#version 450

//#include "fog.glsl"
#include "materials.glsl"

layout(binding = 5) uniform sampler2D Sampler0;
layout(binding = 6) uniform sampler2DShadow ShadowMap;

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ModelViewMat;
    mat4 LightSpaceMat;
    vec3 LightSpaceOffset;
};

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    vec3 LightDir;
    vec3 LightColor;
    vec3 ViewPos;
    float LightIntensity;
    float LightVisibility;
    float NightFactor;
    float AmbientLightFactor;
    float MinAmbientLight;
    float FogFactor;
    float ShadowTexelSize;
    float ShadowBias;
    float ShadowDistortion;
};

layout(binding = 2) uniform CloudUBO {
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

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec4 overlayColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec4 posLightSpace;
layout(location = 5) in vec3 fragPos;
layout(location = 6) in vec3 light;
layout(location = 7) in float vertexDistance;
layout(location = 8) in flat Material material;
layout(location = 14) in vec3 worldPos;

layout(location = 0) out vec4 fragColor;

#include "fog2.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "shadowSampling.glsl"

// --- CLOUD SHADOWS NOISE ---
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
    
    // Safety against NaN flashes during world load
    if (isnan(CameraX) || isnan(CameraZ) || isnan(WindDriftX) || isnan(WindDriftZ)) return 0.0;
    
    // Absolute world coordinate of the fragment
    vec2 absWpos = wpos + vec2(CameraX, CameraZ);
    // Offset by wind drift so shadows track cloud movement
    vec2 samplePos = absWpos - vec2(WindDriftX, WindDriftZ);
    
    // Apply user manual offsets
    samplePos -= vec2(ShadowOffsetX, ShadowOffsetZ);
    
    // Apply user manual flips
    if (ShadowFlipX > 0.5) samplePos.x = -samplePos.x;
    if (ShadowFlipZ > 0.5) samplePos.y = -samplePos.y;
    
    // Apply user manual rotation
    if (ShadowRotation != 0.0) {
        float r = ShadowRotation * 3.14159265359 / 180.0;
        float c = cos(r);
        float s = sin(r);
        samplePos = vec2(
            samplePos.x * c - samplePos.y * s,
            samplePos.x * s + samplePos.y * c
        );
    }
    
    // Match the exact scale of Sampler.java!
    float fScale = CloudScale * 128.0;
    vec2 nx = samplePos / (fScale * ShadowScale) + vec2(0.5);
    
    // 4 octaves to match the CPU shader exactly!
    float value = snoise_offset(nx * 0.25) * 0.4
                + snoise_offset(nx * 0.5)  * 0.3
                + snoise_offset(nx)        * 0.2
                + snoise_offset(nx * 2.0)  * 0.1;
    
    // Normalize from [-1,1] to [0,1]
    value = value * 0.5 + 0.5;
    
    // Apply cloudiness threshold: higher cloudiness = more shadow coverage
    // At cloudiness=0.5 (default clear), roughly half the area has shadows
    value = (value - (1.0 - Cloudiness)) / max(Cloudiness, 0.001);
    value = clamp(value, 0.0, 1.0);
    
    // Coverage mask: use low-frequency noise to create regional variation
    // (some areas cloudy, some clear) like the original Sampler does
    float coverage = snoise_offset(samplePos / 1024.0 + vec2(0.5));
    float covThresh = -0.6 * Cloudiness;
    float covMask = smoothstep(covThresh - 0.3, covThresh, coverage);
    value *= covMask;
    
    // Gentle contrast enhancement - avoid making it binary
    value = smoothstep(0.05, 0.6, value);
    
    return value;
}


void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) {
        discard;
    }
    texColor *= vertexColor * ColorModulator;
    texColor.rgb = mix(overlayColor.rgb, texColor.rgb, overlayColor.a);

    vec4 color;

    vec3 viewDir = normalize(-fragPos);
    vec3 N = normal;
    vec3 V = viewDir;

    float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;
    float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);

    vec3 F0 = material.F0;
    float roughness = material.roughness;
    float metallic = material.metallic;

    vec3 albedo = texColor.rgb;
    //    vec3 albedo = vec3(1.0);

    
    // Cloud Shadow Application
    float cShadow = computeCloudShadow(worldPos.xz);
    if (cShadow > 0.0) {
        float factor = mix(1.0, 1.0 - (CloudShadowIntensity * light.z), cShadow);
        albedo *= factor;
    }

    vec3 radiance = LightColor;

    vec3 color1 = LightingSphereGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);

    color.rgb = (color1 * shadow * light.z) + albedo * 0.3 * light.y + (texColor.rgb * light.x * vec3(1.0, 0.7, 0.5));
    color.a = texColor.a;

    vec3 fragDir = normalize(fragPos);
    float RoUp = dot(fragDir, UpVector);
    RoUp = max(RoUp, 0.0);

    float SoUp = max(dot(UpVector, LightDir), 0.0);
    float RdotL = max(dot(fragDir, LightDir), 0.0);
    vec4 fogColor = vec4(getSkyColor(RdotL, RoUp, SoUp).rgb, 1.0);

    atmospheric_fog(color, fogColor.rgb, fragDir, vertexDistance, FogFactor);

    // Render distance Fog
    if (vertexDistance > 0.8 * FogEnd) {
        color = fog(color, vertexDistance, FogEnd, fogColor);
    }

    //    color.rgb += volLighting(vec3(0.0), fragPos, normalize(fragPos));

    fragColor = color;

//    color *= lightMapColor;
//    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}