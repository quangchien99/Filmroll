package com.filmroll.camera.capture

/**
 * The GLES 2.0 half of the film look.
 *
 * This is a deliberate, narrow port of [com.filmroll.camera.image.shaders.ImageProcessingShader]
 * — the same trilinear LUT sampling against the same packed texture layout, the
 * same linear-light contrast and saturation, the same sRGB warmth tilt and the
 * same cheap grain hash, with identical constants. It is not shared source
 * because SkSL and GLSL ES disagree on every keyword that matters here
 * (`half3` vs `vec3`, shader children vs samplers, `.eval` vs `texture2D`), and a
 * textual transpiler between them would be a worse liability than two files that
 * are read side by side.
 *
 * What is *not* ported is as important as what is. Exposure is missing because
 * the viewfinder biases the sensor instead; shadows, highlights and fringing are
 * missing because they belong to the editor. Grain uses only the cheap path — the
 * film-emulation path costs 30-40× more and exists for the export.
 */
internal object ViewfinderShader {

    const val VERTEX: String = """
        uniform mat4 uTexMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """

    /**
     * Note the precision block: the LUT is addressed in normalized coordinates
     * over a texture up to `size * size` tall (1089 rows for a 33³ cube), and
     * mediump cannot resolve neighbouring rows there — the film would band.
     */
    const val FRAGMENT: String = """
        #extension GL_OES_EGL_image_external : require
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif

        varying vec2 vTexCoord;

        uniform samplerExternalOES uCamera;
        uniform sampler2D uLut;
        uniform float uUseLut;        // 0 = bypass, 1 = apply
        uniform float uLutSize;       // e.g. 33
        uniform float uLutIntensity;  // 0..2; >1 extrapolates past the LUT
        uniform float uContrast;      // ~[-0.5, 0.5]
        uniform float uSaturation;    // ~[-1, 1]
        uniform float uTemperature;   // ~[-1, 1], warm positive
        uniform float uGrain;         // 0..1 amplitude
        uniform float uGrainSeed;     // advances per frame so grain shimmers like film

        vec3 srgbToLinear(vec3 c) {
            return vec3(
                c.r <= 0.04045 ? c.r / 12.92 : pow((c.r + 0.055) / 1.055, 2.4),
                c.g <= 0.04045 ? c.g / 12.92 : pow((c.g + 0.055) / 1.055, 2.4),
                c.b <= 0.04045 ? c.b / 12.92 : pow((c.b + 0.055) / 1.055, 2.4)
            );
        }

        vec3 linearToSrgb(vec3 c) {
            c = clamp(c, 0.0, 1.0);
            return vec3(
                c.r <= 0.0031308 ? c.r * 12.92 : 1.055 * pow(c.r, 1.0 / 2.4) - 0.055,
                c.g <= 0.0031308 ? c.g * 12.92 : 1.055 * pow(c.g, 1.0 / 2.4) - 0.055,
                c.b <= 0.0031308 ? c.b * 12.92 : 1.055 * pow(c.b, 1.0 / 2.4) - 0.055
            );
        }

        // Same addressing as the Skia path: the cube is packed `size` wide by
        // `size * size` tall, blue selecting the slice.
        vec3 sampleLut(float r, float g, float b) {
            float x = (r + 0.5) / uLutSize;
            float y = (b * uLutSize + g + 0.5) / (uLutSize * uLutSize);
            return texture2D(uLut, vec2(x, y)).rgb;
        }

        // Trilinear by hand against a NEAREST-filtered texture. Letting the
        // sampler interpolate would blend across slice boundaries in blue, which
        // shows up as banding in skies.
        vec3 applyLut(vec3 src) {
            float scale = uLutSize - 1.0;
            vec3 s = clamp(src, 0.0, 1.0) * scale;
            vec3 f0 = floor(s);
            vec3 f1 = min(f0 + 1.0, scale);
            vec3 d = s - f0;

            vec3 c000 = sampleLut(f0.r, f0.g, f0.b);
            vec3 c100 = sampleLut(f1.r, f0.g, f0.b);
            vec3 c010 = sampleLut(f0.r, f1.g, f0.b);
            vec3 c110 = sampleLut(f1.r, f1.g, f0.b);
            vec3 c001 = sampleLut(f0.r, f0.g, f1.b);
            vec3 c101 = sampleLut(f1.r, f0.g, f1.b);
            vec3 c011 = sampleLut(f0.r, f1.g, f1.b);
            vec3 c111 = sampleLut(f1.r, f1.g, f1.b);

            vec3 c00 = mix(c000, c100, d.r);
            vec3 c10 = mix(c010, c110, d.r);
            vec3 c01 = mix(c001, c101, d.r);
            vec3 c11 = mix(c011, c111, d.r);

            vec3 c0 = mix(c00, c10, d.g);
            vec3 c1 = mix(c01, c11, d.g);
            return mix(c0, c1, d.b);
        }

        float hash12(vec2 p) {
            vec3 p3 = fract(vec3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        void main() {
            vec3 color = texture2D(uCamera, vTexCoord).rgb;

            if (uUseLut > 0.5) {
                color = mix(color, applyLut(color), uLutIntensity);
            }

            vec3 lin = srgbToLinear(clamp(color, 0.0, 1.0));

            if (uContrast != 0.0) {
                float factor = uContrast > 0.0
                    ? (1.0 + uContrast)
                    : (1.0 / (1.0 - uContrast));
                lin = (lin - 0.2140) * factor + 0.2140;
            }

            if (uSaturation != 0.0) {
                float luma = dot(lin, vec3(0.2126, 0.7152, 0.0722));
                lin = mix(vec3(luma), lin, 1.0 + uSaturation);
            }

            vec3 srgb = linearToSrgb(lin);

            if (uTemperature != 0.0) {
                srgb.r = clamp(srgb.r + uTemperature * 0.08, 0.0, 1.0);
                srgb.g = clamp(srgb.g + uTemperature * 0.02, 0.0, 1.0);
                srgb.b = clamp(srgb.b - uTemperature * 0.08, 0.0, 1.0);
            }

            if (uGrain > 0.0) {
                vec2 gp = gl_FragCoord.xy + vec2(uGrainSeed * 137.7, uGrainSeed * 311.3);
                float nR = hash12(gp + vec2(  17.31,   91.07));
                float nG = hash12(gp + vec2(1313.71,  717.13));
                float nB = hash12(gp + vec2(2731.37, 4297.53));
                vec3 noise = vec3(nR, nG, nB) - 0.5;

                float luma2 = dot(srgb, vec3(0.2126, 0.7152, 0.0722));
                float midtone = max(1.0 - abs(luma2 - 0.5) * 1.4, 0.35);

                srgb = clamp(srgb + noise * uGrain * midtone, 0.0, 1.0);
            }

            gl_FragColor = vec4(srgb, 1.0);
        }
    """
}
