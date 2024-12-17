precision mediump float;

uniform sampler2D uGrid;
uniform vec2 uMouse;
uniform vec2 uDeltaMouse;
uniform float uMouseMove;
uniform float uGridSize;
uniform float uRelaxation;
uniform float uDistance;
uniform vec2 uResolution;
uniform bool uRestoreDisplacement;

void main()
{
    vec2 uv = gl_FragCoord.xy / uResolution.xy;
    vec4 color = texture2D(uGrid, uv);

    float dist = distance(uv, uMouse);
    dist = 1. - (smoothstep(0., uDistance / uGridSize, dist));

    vec2 delta = uDeltaMouse * dist;

    color.rg += delta;
    if (uRestoreDisplacement) {
        color.rg *= min(uRelaxation, uMouseMove);
    }

    gl_FragColor = color;
}