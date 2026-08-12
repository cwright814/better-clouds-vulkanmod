#version 450 core

// Geometry attributes
#define SIZE vec3(_SIZE_XZ_, _SIZE_Y_, _SIZE_XZ_)
#define NEAR_VISIBILITY_START 10.0 + _SIZE_XZ_
#define NEAR_VISIBILITY_END 20.0 + _SIZE_XZ_

#define POSITIONAL_COLORING _POSITIONAL_COLORING_
#define WORLD_CURVATURE _WORLD_CURVATURE_
#define DISTANT_HORIZONS _DISTANT_HORIZONS_

layout (location = 0) in vec3 in_pos;

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
    
    vec3 u_miscellaneous;
    float u_noise_factor;
    
    vec3 u_sun_axis;
    float u_is_fancy;
    
    vec3 u_opacity;
    float padding2;
    
    vec3 u_tint;
    float padding3;
    
    vec2 u_fog_range;
    vec2 u_depth_range;
};

layout(binding = 1) uniform sampler2D u_noise_texture;

layout (location = 0) flat out float pass_opacity;
layout (location = 1) out vec3 pass_color;
layout (location = 2) out vec3 pass_dir;
#if DISTANT_HORIZONS
layout (location = 3) out float pass_dh_depth;
#endif

float linearFogFade(float distance, float fog_start, float fog_end) {
    float f = clamp(1.0 - (distance - fog_start) / (fog_end - fog_start), 0.0, 1.0);
    return f * f;
}

// 14 vertices of the original triangle strip
const vec3 F_POS[14] = vec3[14](
    vec3(-.5, -.5, -.5), vec3(+.5, -.5, -.5), vec3(-.5, -.5, +.5), vec3(+.5, -.5, +.5),
    vec3(+.5, +.5, +.5), vec3(+.5, -.5, -.5), vec3(+.5, +.5, -.5), vec3(-.5, -.5, -.5),
    vec3(-.5, +.5, -.5), vec3(-.5, -.5, +.5), vec3(-.5, +.5, +.5), vec3(+.5, +.5, +.5),
    vec3(-.5, +.5, -.5), vec3(+.5, +.5, -.5)
);

// We unroll the 12 triangles (36 vertices) from the 14-vertex strip
const int TRIS[36] = int[36](
    0,1,2, 1,3,2, 2,3,4, 3,5,4, 4,5,6, 5,7,6,
    6,7,8, 7,9,8, 8,9,10, 9,11,10, 10,11,12, 11,13,12
);

// Fast mesh
const vec3 FAST_POS[4] = vec3[4](
    vec3(-.5, 0.0, -.5), vec3(+.5, 0.0, -.5), vec3(-.5, 0.0, +.5), vec3(+.5, 0.0, +.5)
);
const int FAST_TRIS[6] = int[6](
    0,1,2, 1,3,2
);

const float chunk_size = 12.0;

vec4 bilinearTexture(sampler2D tex, vec2 uv) {
    vec2 size = vec2(textureSize(tex, 0));
    vec2 unnormalized = uv * size - 0.5;
    vec2 f = fract(unnormalized);
    vec2 i = floor(unnormalized);
    vec4 p00 = texture(tex, (i + vec2(0.5, 0.5)) / size);
    vec4 p10 = texture(tex, (i + vec2(1.5, 0.5)) / size);
    vec4 p01 = texture(tex, (i + vec2(0.5, 1.5)) / size);
    vec4 p11 = texture(tex, (i + vec2(1.5, 1.5)) / size);
    return mix(mix(p00, p10, f.x), mix(p01, p11, f.x), f.y);
}

void main() {
    vec3 in_vert = vec3(0.0);
    if (u_is_fancy > 0.5) {
        int vId = TRIS[gl_VertexIndex % 36];
        in_vert = F_POS[vId];
    } else {
        int vId = FAST_TRIS[gl_VertexIndex % 6];
        in_vert = FAST_POS[vId];
    }

    vec3 localWorldPosition = in_pos - u_origin_offset;
    float scaleFalloff = mix(1.0, u_miscellaneous.x, pow(length(localWorldPosition.xz), 2.0) / pow(u_bounding_box.z, 2.0));
    vec3 cloudPos = in_pos;
    cloudPos.y *= scaleFalloff;

    pass_opacity = smoothstep(NEAR_VISIBILITY_START, NEAR_VISIBILITY_END, length(localWorldPosition));

    float waveScale = bilinearTexture(u_noise_texture, (localWorldPosition.xz + u_bounding_box.xy) / 4000.0 + vec2(u_miscellaneous.z * u_time / 800.0)).r;
    float smallWaves = bilinearTexture(u_noise_texture, (localWorldPosition.zx + u_bounding_box.yx) / 1000.0 + vec2(u_miscellaneous.z * u_time / 200.0)).r * 1.8 - 0.9;
    waveScale = mix(mix(waveScale, 1.0, max(smallWaves, 0.0)), 0.0, max(-smallWaves, 0.0));
    float fDynScale = 1.0 - smoothstep(0.0, u_bounding_box.w / 4.0, in_pos.y + 0.5);
    float dynScale = mix(1.0, waveScale, fDynScale * u_miscellaneous.y);
    vec3 scale = SIZE * dynScale * scaleFalloff;

    vec3 vertexPos = scale * in_vert + cloudPos;
    vec3 localWorldVertexPos = vertexPos - u_origin_offset;

    pass_color.r = linearFogFade(length(localWorldVertexPos.xyz), u_fog_range.x, u_fog_range.y);

    #if POSITIONAL_COLORING
    pass_color.g = (scale.y * 0.625 * (in_vert.y + 0.375) + in_pos.y) / (u_bounding_box.w);
    #else
    pass_color.g = 1.0;
    #endif
    pass_color.b = bilinearTexture(u_noise_texture, localWorldPosition.xz / 1024.0).g;

    pass_dir = localWorldVertexPos;

    #if WORLD_CURVATURE != 0
    vertexPos.y -= dot(localWorldVertexPos, localWorldVertexPos) / WORLD_CURVATURE;
    #endif

    #if DISTANT_HORIZONS
    vec4 localPos = u_mv_matrix * vec4(vertexPos, 1.0);
    gl_Position = u_mc_p_matrix * localPos;
    vec4 dhPos = u_dh_p_matrix * localPos;
    pass_dh_depth = (dhPos.z / dhPos.w) * 0.5 + 0.5;
    #else
    gl_Position = u_mvp_matrix * vec4(vertexPos, 1.0);
    #endif
}


