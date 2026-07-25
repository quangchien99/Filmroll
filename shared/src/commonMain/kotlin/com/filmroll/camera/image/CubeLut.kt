package com.filmroll.camera.image

/**
 * A parsed Adobe `.cube` 3D LUT.
 *
 * [data] holds `size^3` RGB triples in the file's own order — red varies fastest,
 * then green, then blue — which is also the order every consumer here expects, so
 * nothing ever has to re-shuffle the array.
 *
 * This used to live inside [SkiaImageProcessor] as a private nested class. It came
 * out because the live viewfinder needs exactly the same numbers: the Skia export
 * path packs it into a raster image, the Android renderer uploads it as a GL
 * texture and iOS hands it to Core Image. One parser means a film stock cannot
 * quietly look different depending on which of the three drew it.
 */
class CubeLut(val size: Int, val data: FloatArray) {

    /** Number of entries, i.e. `size^3`. */
    val entryCount: Int get() = size * size * size

    /**
     * RGBA8 pixels laid out `size` wide × `size * size` tall — a stack of
     * blue slices, each slice a `size × size` red/green plane. Both the Skia
     * shader and the GLES fragment shader index it with
     * `x = r + 0.5`, `y = b * size + g + 0.5`.
     */
    fun toRgba8(): ByteArray {
        val pixels = ByteArray(entryCount * 4)
        for (i in 0 until entryCount) {
            val src = i * 3
            val dst = i * 4
            pixels[dst] = data[src].toByteChannel()
            pixels[dst + 1] = data[src + 1].toByteChannel()
            pixels[dst + 2] = data[src + 2].toByteChannel()
            pixels[dst + 3] = 0xFF.toByte()
        }
        return pixels
    }

    /**
     * RGBA float quadruples with alpha 1 — the layout Core Image's colour-cube
     * filters expect for `inputCubeData` (premultiplied, red fastest).
     */
    fun toRgbaFloats(): FloatArray {
        val out = FloatArray(entryCount * 4)
        for (i in 0 until entryCount) {
            val src = i * 3
            val dst = i * 4
            out[dst] = data[src].coerceIn(0f, 1f)
            out[dst + 1] = data[src + 1].coerceIn(0f, 1f)
            out[dst + 2] = data[src + 2].coerceIn(0f, 1f)
            out[dst + 3] = 1f
        }
        return out
    }

    /**
     * A copy of this cube pulled [mix] of the way from the identity transform
     * toward itself — 0 gives a pass-through cube, 1 gives this one back, and
     * values above 1 keep going, exaggerating the stock rather than clamping.
     *
     * It is the same `mix(src, lut(src), intensity)` the shaders do per pixel,
     * hoisted out to the cube because doing it there is exact and costs a few
     * thousand multiplies once instead of a blend on every frame. Core Image in
     * particular has no primitive that extrapolates past a full-strength LUT, so
     * without this the iOS viewfinder would silently ignore everything above 100%.
     */
    fun mixedWithIdentity(mix: Float): CubeLut {
        if (size < 2) return this
        val scale = (size - 1).toFloat()
        val out = FloatArray(data.size)
        var i = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    out[i] = r / scale + (data[i] - r / scale) * mix
                    out[i + 1] = g / scale + (data[i + 1] - g / scale) * mix
                    out[i + 2] = b / scale + (data[i + 2] - b / scale) * mix
                    i += 3
                }
            }
        }
        return CubeLut(size, out)
    }

    override fun equals(other: Any?): Boolean =
        other is CubeLut && other.size == size && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * size + data.contentHashCode()

    private fun Float.toByteChannel(): Byte =
        (coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
}

/** Largest LUT edge we accept. A 256³ cube is already 64 MB of floats. */
private const val MAX_LUT_SIZE = 256

/**
 * Parse `.cube` text. Returns null for anything that isn't a complete 3D LUT —
 * a truncated download, a 1D-only file, or a size we refuse to allocate.
 */
fun parseCubeLut(text: String): CubeLut? {
    var size = 0
    val data = ArrayList<Float>(0)
    val whitespace = Regex("\\s+")

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) continue

        if (line.startsWith("LUT_3D_SIZE", ignoreCase = true)) {
            size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: return null
            if (size <= 0 || size > MAX_LUT_SIZE) return null
            data.ensureCapacity(size * size * size * 3)
            continue
        }
        if (line.startsWith("TITLE", ignoreCase = true) ||
            line.startsWith("DOMAIN_", ignoreCase = true) ||
            line.startsWith("LUT_1D_", ignoreCase = true)
        ) continue

        if (size == 0) continue
        val parts = line.split(whitespace)
        if (parts.size < 3) continue
        val r = parts[0].toFloatOrNull() ?: continue
        val g = parts[1].toFloatOrNull() ?: continue
        val b = parts[2].toFloatOrNull() ?: continue
        data += r; data += g; data += b
    }

    if (size == 0) return null
    val expected = size * size * size * 3
    if (data.size < expected) return null
    return CubeLut(size, data.subList(0, expected).toFloatArray())
}

/** Convenience for the common case of raw file bytes straight off disk or the network. */
fun parseCubeLut(bytes: ByteArray): CubeLut? = parseCubeLut(bytes.decodeToString())
