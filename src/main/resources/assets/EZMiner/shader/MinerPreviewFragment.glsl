#version 330

out vec4 FragColor;

uniform vec3  fogColor;
uniform float fogNear;
uniform float fogFar;
uniform vec4  lineColor;

void main() {
    float depth = gl_FragCoord.z / gl_FragCoord.w;
    float fogIntensity = clamp((depth - fogNear) / (fogFar - fogNear), 0.0, 1.0);
    vec3 finalColor = mix(lineColor.rgb, fogColor, fogIntensity);
    float alpha = lineColor.a * (1.0 - fogIntensity * 0.6);
    FragColor = vec4(finalColor, alpha);
}
