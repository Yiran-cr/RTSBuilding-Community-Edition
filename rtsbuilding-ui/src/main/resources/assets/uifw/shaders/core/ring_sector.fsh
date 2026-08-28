#version 150

// 环形扇形 SDF 矢量着色器（中空饼状图扇区）。
// 思路与 rounded_rect.fsh / colorwheel.fsh 一致：
//   - 在 fragment 内对每个像素计算到「环 ∩ 扇形」边界的距离场；
//   - 用 dFdx/dFdy 梯度归一化做 1 像素过渡带抗锯齿，
//     外圆弧 / 内圆弧 / 两条径向边交界的过渡带宽度一致，无毛刺。
// SDF 约定：内部为负、边界为 0、外部为正。环形扇形 = 环 ∩ 扇形 = max(ringD, sectorD)。

uniform float u_OuterR;
uniform float u_InnerR;
uniform float u_StartAngle;   // 度
uniform float u_EndAngle;     // 度（> StartAngle；>= StartAngle + 360 视为完整环）

in vec2 texCoord0;            // 相对圆心的局部坐标，范围 ±u_OuterR
in vec4 vertexColor;

out vec4 fragColor;

const float PI  = 3.14159265358979;
const float TAU = 6.283185307179586;

// 把弧度归一化到 [0, TAU)
float wrapAngle(float a) {
    return mod(mod(a, TAU) + TAU, TAU);
}

void main() {
    vec2 p = texCoord0;
    float dist = length(p);

    // —— 圆环 SDF —— 到中线圆距离 - 半厚度。环内为负、环外为正。
    float midR       = (u_OuterR + u_InnerR) * 0.5;
    float halfThick  = (u_OuterR - u_InnerR) * 0.5;
    float ringD      = abs(dist - midR) - halfThick;

    // —— 扇形 SDF（角度裁剪）——
    // 跨度用未回绕的弧度差（如 End=360、Start=0 → span=TAU），
    // 避免 wrapAngle 把跨 360 的完整环回绕成 0 导致判定失败。
    float span = radians(u_EndAngle - u_StartAngle);

    float sectorD;
    if (span >= TAU - 0.0001) {
        // 完整环：无径向边界，处处在扇形内深处，不参与裁剪。
        sectorD = -1.0e9;
    } else {
        float ap  = wrapAngle(atan(p.y, p.x));
        float rel = wrapAngle(ap - radians(u_StartAngle)); // 相对起始边角度 [0, TAU)
        if (rel <= span) {
            // 扇形内：到最近径向边的弧长（外圈），取负。
            sectorD = -min(rel, span - rel) * u_OuterR;
        } else {
            // 扇形外：到最近径向边的弧长（外圈），取正。
            sectorD = min(rel - span, TAU - rel) * u_OuterR;
        }
    }

    // 环 ∩ 扇形：两者都为负时 max 才为负（在内部）。
    float sdf = max(ringD, sectorD);

    // 梯度归一化抗锯齿：|grad(sdf)| ≈ 1，1 像素过渡带，边界平滑。
    vec2 grad = vec2(dFdx(sdf), dFdy(sdf));
    float pixelDist = sdf / max(length(grad), 0.0001);
    float alpha = 1.0 - smoothstep(-1.0, 1.0, pixelDist);
    alpha = clamp(alpha, 0.0, 1.0);

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
