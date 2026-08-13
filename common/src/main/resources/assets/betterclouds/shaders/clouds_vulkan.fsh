#version 450 core

#define DISTANT_HORIZONS _DISTANT_HORIZONS_

const float dither_matrix[16] = float[](
0.0, 0.5, 0.125, 0.625,
0.75, 0.25, 0.875, 0.375,
0.0625, 0.5625, 0.03125, 0.53125,
0.8125, 0.4375, 0.78125, 0.40625
);

layout (location = 0) flat in float pass_opacity;
layout (location = 1) in vec4 pass_color;
layout (location = 2) in vec3 pass_dir;
#if DISTANT_HORIZONS
layout (location = 3) in float pass_dh_depth;
#endif

layout (location = 0) out vec4 out_color;

layout(binding = 0) uniform CloudUBO {
    // Matrices
    mat4 u_vp_matrix;
    mat4 u_mv_matrix;
    mat4 u_mc_p_matrix;
    mat4 u_dh_p_matrix;
    mat4 u_mvp_matrix;
    
    // Vectors
    vec4 u_bounding_box;
    vec4 u_sun_direction;
    vec4 u_color_grading;
    
    vec3 u_origin_offset;
    float u_time;
    
    vec4 u_miscellaneous;
    
    vec3 u_sun_axis;
    float u_noise_factor;
    
    vec3 u_opacity;
    float u_is_fancy;
    
    vec4 u_moon_direction;
    
    vec3 u_moon_axis;
    float u_density_edge;
    
    vec3 u_tint;
    float u_density_center;
    
    vec2 u_fog_range;
    vec2 u_depth_range;
};

layout(binding = 2) uniform sampler2D u_light_texture;

const float pi = 3.14159265359;
const float sqrt2 = 1.41421356237;

vec4 bilinearTexture(sampler2D tex, vec2 uv) {
    uv = fract(uv);
    vec2 size = vec2(textureSize(tex, 0));
    vec2 unnormalized = uv * size - 0.5;
    vec2 f = fract(unnormalized);
    vec2 i = floor(unnormalized);
    vec4 p00 = texture(tex, fract((i + vec2(0.5, 0.5)) / size));
    vec4 p10 = texture(tex, fract((i + vec2(1.5, 0.5)) / size));
    vec4 p01 = texture(tex, fract((i + vec2(0.5, 1.5)) / size));
    vec4 p11 = texture(tex, fract((i + vec2(1.5, 1.5)) / size));
    return mix(mix(p00, p10, f.x), mix(p01, p11, f.x), f.y);
}

void main() {
    int x = int(gl_FragCoord.x) % 4;
    int y = int(gl_FragCoord.y) % 4;
    int index = x + y * 4;

    if (pass_opacity <= dither_matrix[index]) {
        discard;
    }

    vec3 cloudData = pass_color.rgb;
    float density_multiplier = pass_color.a;
    
    // Coverage and final alpha are computed at the end of the shader

    vec3 sun_dir = u_sun_direction.xyz;
    vec3 moon_dir = u_moon_direction.xyz;
    vec3 frag_dir = normalize(pass_dir);

    float sphere_sun = dot(sun_dir, frag_dir);
    float sphere_moon = dot(moon_dir, frag_dir);
    
    bool is_sun = sphere_sun > sphere_moon;
    vec3 active_dir = is_sun ? sun_dir : moon_dir;
    vec3 active_axis = is_sun ? u_sun_axis : u_moon_axis;
    
    vec3 xz_proj = frag_dir - active_dir * dot(frag_dir, active_dir);
    float proj_angle = 0.0;
    if (length(xz_proj) > 0.0001) {
        proj_angle = acos(clamp(dot(normalize(xz_proj), active_axis), -1.0, 1.0));
    }

    float sphere = dot(active_dir, frag_dir);
    if (!is_sun) sphere = -sphere; // Negate so the shader uses the moon side of the texture
    
    float superellipse_falloff = dot(active_dir, frag_dir);
    if (!is_sun) superellipse_falloff = -superellipse_falloff;
    const float superellipse_size = 3.0;
    float superellipse = (
    (1.0 + (1.0 / 3.0) * (pow(sin(2.0 * proj_angle + pi / 2.0), 2.0)))
    * (superellipse_size - abs(superellipse_falloff) * superellipse_size) - 1.0
    ) * sign(-superellipse_falloff);
    float light_uv_x = mix(sphere, superellipse, smoothstep(0.75, 1.0, abs(sphere)));
    
    // (1, 0) to (0.5, 1)
    if (light_uv_x > 0.5) light_uv_x = (-2.0 * light_uv_x + 2.0) * 0.375;
    // (0.5, 0) to (-0.5, 1)
    else if (light_uv_x > -0.5) light_uv_x = 0.375 + (-1.0 * light_uv_x + 0.5) * 0.25;
    // (-0.5, 0) to (-1, 1)
    else light_uv_x = 0.625 + (-2.0 * light_uv_x - 1.0) * 0.375;

    vec2 light_uv = vec2(light_uv_x, u_sun_direction.w);

    out_color.rgb = bilinearTexture(u_light_texture, light_uv).rgb;

    float color_lumi = dot(out_color.rgb, vec3(0.2126, 0.7152, 0.072)) + 0.001;
    vec3 color_chroma = out_color.rgb / color_lumi;

    float color_variance = length(vec2(1.0 - pow(1.0 - cloudData.g, 3.) * 0.75, cloudData.b * 0.75 + 0.25)) / sqrt2;
    color_lumi = mix(color_lumi, color_variance * 0.35 * (0.3 + 0.7 * color_lumi) + 0.75 * color_lumi, u_noise_factor);

    color_chroma = mix(vec3(1.0), color_chroma, u_color_grading.w);
    color_lumi *= u_color_grading.x;
    color_lumi = pow(color_lumi, u_color_grading.y);

    out_color.rgb = color_chroma * color_lumi;
    out_color.rgb *= u_tint;
    
    // Instead of computing coverage from 3D ray marching like the original shader,
    // we use a baseline coverage of 1.0 which results exactly in the user's base u_opacity.x
    float coverage = 1.0;
    out_color.a = pow(coverage, u_opacity.z) / (1.0 / (u_opacity.x) + pow(coverage, u_opacity.z) - 1.0);
    out_color.a *= u_opacity.y;
    out_color.a *= cloudData.r;
    out_color.a *= pass_opacity; // fade
    
    // Apply density multiplier for edges/filling experiment
    out_color.a = clamp(out_color.a * density_multiplier, 0.0, 1.0);
}
