package com.filmroll.camera.capture

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.filmroll.camera.image.CubeLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws camera frames through the film look.
 *
 * The frames arrive as an external OES texture backed by a [SurfaceTexture] that
 * CameraX writes into, so nothing is ever copied through the CPU: a frame goes
 * sensor → buffer → GPU sampler → screen, with the LUT applied in the same pass
 * that composites it. That is the whole reason the viewfinder can hold 60 fps
 * while the editor's Skia path needs 25 ms for a 960 px still.
 *
 * ## Orientation
 *
 * A custom GL surface gets none of `PreviewView`'s rotation handling, so the
 * mapping is done here, entirely in texture coordinates. Rotating the *quad*
 * instead would seem simpler and is wrong: NDC is already stretched to the
 * viewport, so a 90° turn there is not a rigid rotation. Normalized texture space
 * has no such anisotropy — a quarter turn is exactly a quarter turn — which is
 * why the composed matrix is
 *
 * ```
 * T(+½) · R(rotationDegrees) · S(cropX, cropY) · Mirror · T(-½)
 * ```
 *
 * applied to the view-space coordinate, and then the [SurfaceTexture]'s own
 * transform on top of that.
 */
internal class ViewfinderRenderer(
    private val onFrameAvailable: () -> Unit,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    // --- GL objects, only ever touched on the GL thread ---
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var texMatrixHandle = 0
    private var cameraHandle = 0
    private var lutHandle = 0
    private var useLutHandle = 0
    private var lutSizeHandle = 0
    private var lutIntensityHandle = 0
    private var contrastHandle = 0
    private var saturationHandle = 0
    private var temperatureHandle = 0
    private var grainHandle = 0
    private var grainSeedHandle = 0

    private var cameraTextureId = 0
    private var lutTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var uploadedLutSize = 1

    private val vertices: FloatBuffer = floatBuffer(
        floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        ),
    )

    // Paired with the vertices so (0,0) sits at the bottom-left of the screen —
    // the convention the SurfaceTexture transform matrix is written against.
    private val texCoords: FloatBuffer = floatBuffer(
        floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        ),
    )

    private val stMatrix = FloatArray(16)
    private val orientMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)

    // --- cross-thread state: written from the main thread, read on the GL thread ---
    @Volatile private var look: LiveLook = LiveLook()
    @Volatile private var pendingCube: CubeLut? = null
    @Volatile private var lutDirty = true
    @Volatile private var rotationDegrees = 0
    @Volatile private var mirror = false
    @Volatile private var bufferWidth = 0
    @Volatile private var bufferHeight = 0

    private var viewWidth = 0
    private var viewHeight = 0

    fun setLook(next: LiveLook) {
        // Re-uploading the LUT texture is the only expensive part, so it is gated
        // on the cube identity rather than on the look as a whole — dragging the
        // strength slider must not re-upload 140 KB per frame.
        if (next.cube !== look.cube) {
            pendingCube = next.cube
            lutDirty = true
        }
        look = next
    }

    fun setBufferSize(width: Int, height: Int) {
        bufferWidth = width
        bufferHeight = height
    }

    fun setRotation(degrees: Int) {
        rotationDegrees = ((degrees % 360) + 360) % 360
    }

    fun setMirror(value: Boolean) {
        mirror = value
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(ViewfinderShader.VERTEX, ViewfinderShader.FRAGMENT)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        cameraHandle = GLES20.glGetUniformLocation(program, "uCamera")
        lutHandle = GLES20.glGetUniformLocation(program, "uLut")
        useLutHandle = GLES20.glGetUniformLocation(program, "uUseLut")
        lutSizeHandle = GLES20.glGetUniformLocation(program, "uLutSize")
        lutIntensityHandle = GLES20.glGetUniformLocation(program, "uLutIntensity")
        contrastHandle = GLES20.glGetUniformLocation(program, "uContrast")
        saturationHandle = GLES20.glGetUniformLocation(program, "uSaturation")
        temperatureHandle = GLES20.glGetUniformLocation(program, "uTemperature")
        grainHandle = GLES20.glGetUniformLocation(program, "uGrain")
        grainSeedHandle = GLES20.glGetUniformLocation(program, "uGrainSeed")

        cameraTextureId = createExternalTexture()
        lutTextureId = createLutTexture()
        // The EGL context can be recreated after a pause; everything above is
        // brand new, so whatever cube is loaded has to go up again.
        pendingCube = look.cube
        lutDirty = true

        val texture = SurfaceTexture(cameraTextureId)
        texture.setOnFrameAvailableListener { onFrameAvailable() }
        surfaceTexture = texture
        onSurfaceTextureReady(texture)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val texture = surfaceTexture ?: return

        texture.updateTexImage()
        texture.getTransformMatrix(stMatrix)

        if (lutDirty) uploadLut()

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (viewWidth == 0 || viewHeight == 0 || bufferWidth == 0 || bufferHeight == 0) return

        buildOrientationMatrix()
        Matrix.multiplyMM(finalMatrix, 0, stMatrix, 0, orientMatrix, 0)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(cameraHandle, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
        GLES20.glUniform1i(lutHandle, 1)

        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, finalMatrix, 0)

        val adjustments = look.adjustments
        val hasLut = look.cube != null && uploadedLutSize > 1
        GLES20.glUniform1f(useLutHandle, if (hasLut) 1f else 0f)
        GLES20.glUniform1f(lutSizeHandle, uploadedLutSize.toFloat())
        // The same normalizations SkiaImageProcessor applies, so a film looks the
        // same strength in the viewfinder as it does on the exported frame.
        GLES20.glUniform1f(
            lutIntensityHandle,
            (adjustments.lutIntensity / 100f).coerceIn(0f, 2f),
        )
        GLES20.glUniform1f(contrastHandle, (adjustments.contrast / 40f).coerceIn(-0.5f, 0.5f))
        GLES20.glUniform1f(saturationHandle, (adjustments.saturation / 20f).coerceIn(-1f, 1f))
        GLES20.glUniform1f(temperatureHandle, (adjustments.temperature / 20f).coerceIn(-1f, 1f))
        GLES20.glUniform1f(grainHandle, (adjustments.grain / 10f).coerceIn(0f, 1f))
        // A grain pattern frozen across frames reads as dirt on the lens; real
        // emulsion re-rolls its dice every exposure, so this advances with time.
        GLES20.glUniform1f(grainSeedHandle, (SystemClock.uptimeMillis() % 100_000L) / 97f)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    /** Releases the frame source. The GL objects die with the context. */
    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surfaceTexture?.release()
        surfaceTexture = null
    }

    // ------------------------------------------------------------------ private

    /**
     * Maps a view-space coordinate onto the camera buffer: undo the display
     * rotation, centre-crop to the view's aspect, and mirror the front lens.
     */
    private fun buildOrientationMatrix() {
        val rotated = rotationDegrees % 180 != 0
        val displayWidth = if (rotated) bufferHeight else bufferWidth
        val displayHeight = if (rotated) bufferWidth else bufferHeight

        val imageAspect = displayWidth.toFloat() / displayHeight.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

        // Centre-crop: sample a centred sub-rect whose aspect matches the view,
        // so the preview fills the surface without letterboxing or stretching.
        var cropX = 1f
        var cropY = 1f
        if (imageAspect > viewAspect) {
            cropX = viewAspect / imageAspect
        } else if (imageAspect < viewAspect) {
            cropY = imageAspect / viewAspect
        }

        Matrix.setIdentityM(orientMatrix, 0)
        Matrix.translateM(orientMatrix, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(orientMatrix, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(orientMatrix, 0, cropX, cropY, 1f)
        if (mirror) Matrix.scaleM(orientMatrix, 0, -1f, 1f, 1f)
        Matrix.translateM(orientMatrix, 0, -0.5f, -0.5f, 0f)
    }

    private fun uploadLut() {
        val cube = pendingCube
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
        if (cube == null) {
            // A 1×1 stand-in. `uUseLut` is 0 in this state so it is never read,
            // but GLES still wants something complete bound to the sampler.
            val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            pixel.put(byteArrayOf(0, 0, 0, 0xFF.toByte()))
            pixel.position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel,
            )
            uploadedLutSize = 1
        } else {
            val bytes = cube.toRgba8()
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                cube.size, cube.size * cube.size, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer,
            )
            uploadedLutSize = cube.size
        }
        lutDirty = false
    }

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        return ids[0]
    }

    /** NEAREST on purpose — the shader interpolates the cube itself. */
    private fun createLutTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vertexShader)
        GLES20.glAttachShader(id, fragmentShader)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            error("Viewfinder program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return id
    }

    private fun compileShader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source.trimIndent())
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            error("Viewfinder shader compile failed: $log")
        }
        return id
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
}
