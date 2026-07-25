package com.filmroll.camera.capture

import com.filmroll.camera.image.CubeLut
import com.filmroll.camera.image.ImageAdjustments
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.CFBridgingRelease
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.dataWithBytes

/**
 * The Core Image half of the film look.
 *
 * Where Android hand-writes one fragment shader, iOS assembles the same pipeline
 * out of stock filters — and that is the better trade here, not a compromise:
 * `CIColorCubeWithColorSpace` is a first-party, Metal-backed 3D LUT that already
 * does exactly the trilinear interpolation the SkSL does by hand, so the film
 * itself costs nothing to get right.
 *
 * The colour space matters more than it looks. Core Image works in linear sRGB,
 * while a `.cube` file is authored against gamma-encoded sRGB values — feeding
 * one to the other without saying so produces a LUT that is technically applied
 * and visibly wrong, with crushed shadows and dirty midtones. Naming the space
 * makes Core Image encode before the lookup and decode after.
 *
 * Deliberate divergences from the Skia pipeline, all in the direction of "the
 * viewfinder is a preview, the export is the truth":
 *  - contrast and saturation run in Core Image's linear working space rather
 *    than pivoting on sRGB mid-grey, so extremes drift by a few percent;
 *  - grain is flat additive noise instead of the luminance-weighted, per-channel
 *    FBM the export uses. It answers "how much texture am I dialling in", which
 *    is the only question a viewfinder is being asked.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object ViewfinderFilters {

    /**
     * Bridged once and held: `CGColorSpaceRef` is a Core Foundation type and the
     * filter parameter dictionary is Objective-C, so it has to cross the toll-free
     * bridge before it can go in. Creating it per frame would be pure waste.
     */
    private val sRgbColorSpace: Any? by lazy {
        CGColorSpaceCreateWithName(kCGColorSpaceSRGB)?.let { CFBridgingRelease(it) }
    }

    /** The infinite-extent noise field, generated once and merely slid around after that. */
    private val noiseSource: CIImage? by lazy {
        CIFilter.filterWithName("CIRandomGenerator")?.outputImage
    }

    /**
     * Wraps [cube] in the `NSData` the filter wants. Cached by the caller against
     * the film, because this copies `size³ × 16` bytes — 570 KB for a 33³ cube.
     */
    fun cubeData(cube: CubeLut): NSData {
        val floats = cube.toRgbaFloats()
        return floats.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), (floats.size * 4).toULong())
        }
    }

    /**
     * Applies a cube that already has the strength baked into it (see
     * [CubeLut.mixedWithIdentity]) — so this stays a single lookup no matter where
     * the strength slider is.
     */
    fun applyLut(image: CIImage, size: Int, data: NSData): CIImage =
        CIFilter.filterWithName(
            "CIColorCubeWithColorSpace",
            mapOf(
                "inputImage" to image,
                "inputCubeDimension" to NSNumber(float = size.toFloat()),
                "inputCubeData" to data,
                "inputColorSpace" to sRgbColorSpace,
            ),
        )?.outputImage ?: image

    fun applyTone(image: CIImage, adjustments: ImageAdjustments): CIImage {
        var result = image

        val contrast = (adjustments.contrast / 40f).coerceIn(-0.5f, 0.5f)
        val saturation = (adjustments.saturation / 20f).coerceIn(-1f, 1f)
        if (contrast != 0f || saturation != 0f) {
            // Same curve the shader uses: positive scales up, negative flattens by
            // the reciprocal, so the two halves of the slider feel symmetrical.
            val contrastFactor = when {
                contrast > 0f -> 1f + contrast
                contrast < 0f -> 1f / (1f - contrast)
                else -> 1f
            }
            result = CIFilter.filterWithName(
                "CIColorControls",
                mapOf(
                    "inputImage" to result,
                    "inputContrast" to NSNumber(float = contrastFactor),
                    "inputSaturation" to NSNumber(float = 1f + saturation),
                    "inputBrightness" to NSNumber(float = 0f),
                ),
            )?.outputImage ?: result
        }

        val temperature = (adjustments.temperature / 20f).coerceIn(-1f, 1f)
        if (temperature != 0f) {
            // A per-channel bias rather than CITemperatureAndTint: these are the
            // exact numbers the shader adds, so warmth is the same gesture on both
            // platforms instead of two different interpretations of "warmer".
            result = CIFilter.filterWithName(
                "CIColorMatrix",
                mapOf(
                    "inputImage" to result,
                    "inputBiasVector" to CIVector.vectorWithX(
                        (temperature * 0.08f).toDouble(),
                        (temperature * 0.02f).toDouble(),
                        (-temperature * 0.08f).toDouble(),
                        0.0,
                    ),
                ),
            )?.outputImage ?: result
        }

        return result
    }

    /**
     * Additive noise over the frame.
     *
     * [seed] slides the noise field between frames — a grain pattern nailed to the
     * same pixels reads as a dirty sensor rather than as emulsion.
     */
    fun applyGrain(image: CIImage, extent: CValue<CGRect>, grain: Float, seed: Float): CIImage {
        val amount = (grain / 10f).coerceIn(0f, 1f)
        if (amount <= 0f) return image
        val noise = noiseSource ?: return image

        val shifted = noise.imageByApplyingTransform(
            CGAffineTransformMakeTranslation(
                (seed * 311.3f % 1024f).toDouble(),
                (seed * 137.7f % 1024f).toDouble(),
            ),
        )

        // Desaturate to luma and scale to the requested amplitude, biased so the
        // field is centred on zero and adds as much as it subtracts.
        val scaled = CIFilter.filterWithName(
            "CIColorMatrix",
            mapOf(
                "inputImage" to shifted,
                "inputRVector" to CIVector.vectorWithX(amount.toDouble(), 0.0, 0.0, 0.0),
                "inputGVector" to CIVector.vectorWithX(0.0, amount.toDouble(), 0.0, 0.0),
                "inputBVector" to CIVector.vectorWithX(0.0, 0.0, amount.toDouble(), 0.0),
                "inputAVector" to CIVector.vectorWithX(0.0, 0.0, 0.0, 0.0),
                "inputBiasVector" to CIVector.vectorWithX(
                    (-amount / 2f).toDouble(),
                    (-amount / 2f).toDouble(),
                    (-amount / 2f).toDouble(),
                    1.0,
                ),
            ),
        )?.outputImage?.imageByCroppingToRect(extent) ?: return image

        return CIFilter.filterWithName(
            "CIAdditionCompositing",
            mapOf(
                "inputImage" to scaled,
                "inputBackgroundImage" to image,
            ),
        )?.outputImage ?: image
    }
}
