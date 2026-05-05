#version 330

layout (lines) in;
layout (triangle_strip, max_vertices = 4) out;

in  float[] vDistance;
in  vec4[]  vViewPosition;

uniform mat4  projection;
uniform float minWidth;
uniform float maxWidth;

void main() {
    float w0 = clamp(0.1 / max(vDistance[0], 0.0001), minWidth, maxWidth);
    float w1 = clamp(0.1 / max(vDistance[1], 0.0001), minWidth, maxWidth);

    vec3 dir = vViewPosition[1].xyz - vViewPosition[0].xyz;
    float len = length(dir);
    if (len < 0.0001) return;   // degenerate segment – skip rather than emit NaN
    dir /= len;

    vec3 perp = cross(dir, vec3(0.0, 0.0, 1.0));
    if (length(perp) < 0.0001) perp = vec3(1.0, 0.0, 0.0);
    else perp = normalize(perp);

    vec3 off0 = perp * (w0 * 0.5);
    vec3 off1 = perp * (w1 * 0.5);

    gl_Position = projection * (vViewPosition[0] - vec4(off0, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[1] - vec4(off1, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[0] + vec4(off0, 0.0)); EmitVertex();
    gl_Position = projection * (vViewPosition[1] + vec4(off1, 0.0)); EmitVertex();
    EndPrimitive();
}
