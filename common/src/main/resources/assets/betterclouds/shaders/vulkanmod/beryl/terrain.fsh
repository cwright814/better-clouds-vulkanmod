#version 450

#include "materials.glsl"

layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 5) uniform sampler2DShadow ShadowMap;

#ifdef COLORED_SHADOWS
layout(binding = 6) uniform sampler2DShadow ShadowMap1;
layout(binding = 7) uniform sampler2D ShadowMapColor;
#endif

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
    float FogEnvStart;
    float FogEnvEnd;
    vec3 LightDir;
    vec3 LightColor;
    vec3 AmbientLight;
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

layout(binding = 8) uniform CloudUBO {
    float WindDriftX;
    float WindDriftZ;
    float Cloudiness;
    float CloudScale;
    float CloudShadowsEnabled;
    float CloudTime;
    float CameraX;
    float CameraZ;
    float CloudShadowIntensity;
};

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec3 normal;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec4 posLightSpace;
layout(location = 5) in vec3 fragPos;
layout(location = 6) in vec3 light;
layout(location = 7) in flat Material material;
layout(location = 14) in vec3 worldPos;

layout(location = 0) out vec4 fragColor;

#include "fog2.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "shadowSampling.glsl"

// Returns a random number based on a vec3 and an int.
float random(vec3 seed, int i){
    vec4 seed4 = vec4(seed, i);
    float dot_product = dot(seed4, vec4(12.9898,78.233, 45.164, 94.673));
    return fract(sin(dot_product) * 43758.5453);
}

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
float computeCloudShadow(vec2 wpos) {
    if (CloudShadowsEnabled <= 0.0 || CloudScale <= 0.0 || Cloudiness <= 0.0) return 0.0;
    
    // Safety against NaN flashes during world load
    if (isnan(CameraX) || isnan(CameraZ) || isnan(WindDriftX) || isnan(WindDriftZ)) return 0.0;
    
    // Absolute world coordinate of the fragment
    vec2 absWpos = wpos + vec2(CameraX, CameraZ);
    // Offset by wind drift so shadows track cloud movement
    vec2 samplePos = absWpos - vec2(WindDriftX, WindDriftZ);
    
    // Better Clouds uses PerlinSimplexNoise with octaves [-1, 0, 1, 2] sampled at
    // x/scale/128. With scale=2, the base noise coord is x/256, giving ~256-block features.
    // That's too large to see within render distance. We approximate the multi-octave
    // character by using a higher base frequency and 4 octaves of our simplex noise.
    // Base frequency: one noise cell ≈ 64 blocks (visible cloud-shadow scale)
    vec2 nBase = samplePos / 64.0;
    
    // 4 octaves: large shapes + medium detail + small detail + fine detail
    float value = snoise_c(nBase * 0.25) * 0.4   // ~256 block features (cloud mass shapes)
                + snoise_c(nBase * 0.5)  * 0.3   // ~128 block features (individual clouds)
                + snoise_c(nBase)        * 0.2   // ~64 block features (cloud edges)
                + snoise_c(nBase * 2.0)  * 0.1;  // ~32 block features (fine detail)
    
    // Normalize from [-1,1] to [0,1]
    value = value * 0.5 + 0.5;
    
    // Apply cloudiness threshold: higher cloudiness = more shadow coverage
    // At cloudiness=0.5 (default clear), roughly half the area has shadows
    value = (value - (1.0 - Cloudiness)) / max(Cloudiness, 0.001);
    value = clamp(value, 0.0, 1.0);
    
    // Coverage mask: use low-frequency noise to create regional variation
    // (some areas cloudy, some clear) like the original Sampler does
    float coverage = snoise_c(samplePos / 1024.0 + vec2(0.5));
    float covThresh = -0.6 * Cloudiness;
    float covMask = smoothstep(covThresh - 0.3, covThresh, coverage);
    value *= covMask;
    
    // Gentle contrast enhancement - avoid making it binary
    value = smoothstep(0.05, 0.6, value);
    
    return value;
}
// ---------------------------

void main() {
    vec4 texColor = texture(Sampler0, texCoord0) * vertexColor;

    if (texColor.a < 0.5) {
        discard;
    }

    vec4 color;

    vec3 viewDir = normalize(-fragPos);
    vec3 N = normal;
    vec3 V = viewDir;

    float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;

    #ifdef COLORED_SHADOWS
        vec3 shadow = computeShadowColor(ShadowMap, ShadowMap1, ShadowMapColor, posLightSpace, ShadowTexelSize, bias);
    #else
        float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);
    #endif

    vec3 F0 = material.F0;
    float roughness = material.roughness;
    float metallic = material.metallic;

    vec3 albedo = texColor.rgb;
    
    // Cloud Shadow Application - apply cloud shadow to ambient and diffuse radiance
    float cShadow = computeCloudShadow(worldPos.xz);
    if (cShadow > 0.0) {
        float factor = mix(1.0, 1.0 - CloudShadowIntensity, cShadow);
        albedo *= factor;
    }

    vec3 radiance = LightColor * 0.6;

    // Gradually reduce lighting when its low on the horizon
    radiance *= saturate((dot(UpVector, LightDir) - 0.04) * 50); // 1 / 0.02

//    vec3 color1 = LightingGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);
//    vec3 color1 = LightingSphereGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);
    vec3 color1 = LightingSphereGGX2(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic, material.lightingType);

    color.rgb = (color1 * shadow * light.z) + albedo * (light.y * AmbientLight + light.x * vec3(1.0, 0.7, 0.5));
    float NdotU = max(dot(N, UpVector), 0.0);
    NdotU = material.lightingType == 0 ? NdotU : 0.8;
    color.rgb += radiance * 0.1 * NdotU * albedo * shadow * light.z;

    if (material.lightEmission > 0.0) {
        color.rgb += max((texColor.rgb - vec3(0.05)), vec3(0.0)) * material.lightEmission;
    }

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
}
