#version 450

#include "materials.glsl"

layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 5) uniform sampler2DShadow ShadowMap;

#ifdef SSR
layout(binding = 6) uniform sampler2D Framebuffer;
layout(binding = 7) uniform sampler2D Depthbuffer;
#endif

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ProjMat;
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
    float GameTime;
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
    float NoiseOffsetX;
    float NoiseOffsetZ;
    float ShadowOffsetX;
    float ShadowOffsetZ;
    float ShadowRotation;
    float ShadowFlipX;
    float ShadowFlipZ;
    float ShadowScale;
};

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec3 normal;
layout(location = 3) in vec4 texCoord0;
layout(location = 4) in vec4 posLightSpace;
layout(location = 5) in vec3 fragPos;
layout(location = 6) in vec3 light;
layout(location = 7) in flat Material material;

layout(location = 0) out vec4 fragColor;

#include "fog2.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "shadowSampling.glsl"

#ifdef SSR
    #include "ssr.glsl"
#endif

vec3 hash(vec3 a);

float fresnelSchlick(float cosTheta, float F0)
{
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

vec3 waterNormal(sampler2D NoiseTex, vec3 worldPos, float gameTime) {
    worldPos *= 0.1;

    //    return texture(Sampler4, worldPos.xz).ggg - vec3(0.5);
    //    float noiseA = texture(Sampler4, worldPos.xz)

    float animation = gameTime * 40;
    //    vec2 animation = vec2(0.0);

    //    return texture(Sampler4, worldPos.xz + animation).ggg;
    //    return texture(Sampler4, worldPos.xz + animation).rrr;
    //    return texture(Sampler4, worldPos.xz).ggg;

    //    float n1 = texture(Sampler4, worldPos.xz + animation).r;
    //    float n2 = texture(Sampler4, (worldPos.xz + animation) * 5).r * 0.125;
    //    float n1 = (texture(Sampler4, worldPos.xz + animation).r - 0.5) * 2;
    //    float n2 = (texture(Sampler4, (worldPos.xz + animation) * 5).r - 0.5) * 0.125;
    //
    //    return vec3(n1 + n2);

    worldPos.xz += animation;
    float normalOffset = 0.25;
    //    float normalOffset = 0.005;
    //
    ////    float fresnel = pow(clamp(1.0 + dot(normalize(normal), normalize(viewPos)), 0.0, 1.0), 8.0);
    ////    float normalStrength = 0.35 * (1.0 - fresnel);
    float normalStrength = 0.1;

    float h1 = texture(NoiseTex, vec2(worldPos.x + normalOffset, worldPos.z)).g;
    float h2 = texture(NoiseTex, vec2(worldPos.x - normalOffset, worldPos.z)).g;
    float h3 = texture(NoiseTex, vec2(worldPos.x, worldPos.z + normalOffset)).g;
    float h4 = texture(NoiseTex, vec2(worldPos.x, worldPos.z - normalOffset)).g;

    float xDelta = (h2 - h1) / normalOffset;
    float yDelta = (h4 - h3) / normalOffset;

    //    vec3 normalMap = vec3(xDelta, yDelta, 1.0 - (xDelta * xDelta + yDelta * yDelta));
    //    return normalMap * normalStrength + vec3(0.0, 0.0, 1.0 - normalStrength);

    //    vec3 normalMap = vec3(xDelta, yDelta, 0.0);
    //    return normalMap * normalStrength;

    //    return vec3(xDelta, 0.0, yDelta);
    return normalize(vec3(normalStrength * xDelta, 1.0, normalStrength * yDelta));
}

vec3 getWaterNormal(vec3 pos, vec3 normal) {
    vec3 N;

    // Sine wave
    float m = cos(pos.x);
    float mN = 1 / (m + 0.001);
    N.y = mN;
    N.x = 1;
    N.z = 0;

    N = normalize(N);

    return N;
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
    vec4 texColor = texture(Sampler0, texCoord0.xy) * vertexColor;

    if (texColor.a < 0.1) {
        discard;
    }

    vec3 Normal = normal.xyz;

    //    //Add random noise to simulate waves
    //    Normal += hash(fragPosWS.xyz) * 0.002;
    //    Normal = normalize(Normal);

    //    Normal = getWaterNormal(fragPosWS, normal);

    //    Normal = waterNormal(fragPosWS);
    //    Normal = (ModelViewMat * vec4(Normal, 0.0)).xyz;

    vec4 color;

    // Water
    if (material.id == 1) {
        #include "materials/water.glsl"
    }
    else if (material.id == 2) {
        #include "materials/glass.glsl"
    }
    else {
        vec3 viewDir = normalize(-fragPos);
        vec3 N = normal;
        vec3 V = viewDir;

        float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;
        float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);

        vec3 F0 = material.F0;
        float roughness = material.roughness;
        float metallic = material.metallic;

        vec3 albedo = texColor.rgb;
vec3 radiance = LightColor * 0.6;

        // Gradually reduce lighting when its low on the horizon
        radiance *= saturate((dot(UpVector, LightDir) - 0.04) * 50); // 1 / 0.02

        vec3 color1 = LightingSphereGGX2(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic, material.lightingType);

        color.rgb = (color1 * shadow * light.z) + albedo * (0.3 * light.y + light.x * vec3(1.0, 0.7, 0.5));
        float NdotU = max(dot(N, UpVector), 0.0);
        NdotU = material.lightingType == 0 ? NdotU : 0.8;
        color.rgb += radiance * 0.05 * NdotU * albedo * shadow * light.z;

        if (material.lightEmission > 0.0) {
            color.rgb += max((texColor.rgb - vec3(0.05)), vec3(0.0)) * material.lightEmission;
        }

        color.a = texColor.a;
    }


    // Global Cloud Shadow Application
    float cShadow = computeCloudShadow(texCoord0.zw);
    if (cShadow > 0.0) {
        float factor = mix(1.0, 1.0 - (CloudShadowIntensity * light.z), cShadow);
        color.rgb *= factor;
    }

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

    fragColor = color;
}

vec3 hash(vec3 a)
{
    a = fract(a * 0.8);
    a += dot(a, a.yxz + 1.0);
    //    return fract((a.xxy + a.yxx)*a.zyx);
    return fract(a.xyz * a.xyz + a.zxy);
}