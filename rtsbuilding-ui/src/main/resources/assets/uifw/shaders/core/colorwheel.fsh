#version 150

uniform vec2 u_Size;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// HSV -> RGB（标准 GLSL 公式，色相 0=红）
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // texCoord0 为局部坐标（-half .. half），dist 即到圆心距离
    float radius = min(u_Size.x, u_Size.y) - 1.0;
    float dist = length(texCoord0);
    if (dist > radius) {
        fragColor = vec4(0.0);
        return;
    }
    float hue = atan(texCoord0.y, texCoord0.x) / 6.28318530718 + 0.5;
    float sat = clamp(dist / radius, 0.0, 1.0);
    vec3 rgb = hsv2rgb(vec3(hue, sat, 1.0));
    // 圆形边缘抗锯齿
    float alpha = 1.0 - smoothstep(radius - 1.0, radius, dist);
    fragColor = vec4(rgb, alpha);
}
