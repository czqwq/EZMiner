#version 330

layout (lines) in;
layout (triangle_strip, max_vertices = 4) out;

in float[] vDistance;
in vec4[] vViewPosition;

uniform mat4 projection;
uniform float minWidth = 0.01;
uniform float maxWidth = 5.0;

void main() {
    float w0 = clamp(0.1 / vDistance[0], minWidth, maxWidth);
    float w1 = clamp(0.1 / vDistance[1], minWidth, maxWidth);

    vec3 dir = normalize(vViewPosition[1].xyz - vViewPosition[0].xyz);
    vec3 perp = cross(dir, vec3(0, 0, 1));
    if (length(perp) < 0.0001) perp = vec3(1, 0, 0);
    else perp = normalize(perp);

    vec3 off0 = perp * w0 * 0.5;
    vec3 off1 = perp * w1 * 0.5;

    gl_Position = projection * (vViewPosition[0] - vec4(off0, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[1] - vec4(off1, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[0] + vec4(off0, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[1] + vec4(off1, 0.0)); EmitVertex();

    EndPrimitive();
}
