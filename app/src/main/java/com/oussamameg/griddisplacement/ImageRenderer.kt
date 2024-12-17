package com.oussamameg.griddisplacement

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.animation.AnticipateOvershootInterpolator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

class ImageRenderer(
    private val surfaceView: GLImageSurfaceView,
    private val context: Context,
    private val bitmap: Bitmap?
) :
    GLSurfaceView.Renderer {
    private var isPaused: Boolean = false
    var square: Square? = null
    private var isTouching = false
    private var width = 0
    private var height = 0
    var isSurfaceReady = false
    val locationOnScreen = IntArray(2)
    private var onSurfaceCreatedAndReady: (() -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val bgColorRGBAFloat = convertColorIntToFloatArray(Color(0xFF000000).toArgb())
        GLES30.glClearColor(
            bgColorRGBAFloat[0],
            bgColorRGBAFloat[1],
            bgColorRGBAFloat[2],
            bgColorRGBAFloat[3]
        )
        isSurfaceReady = true
    }

    private fun convertColorIntToFloatArray(colorInt: Int): FloatArray {
        val red = ((colorInt shr 16) and 0xFF) / 255.0f
        val green = ((colorInt shr 8) and 0xFF) / 255.0f
        val blue = (colorInt and 0xFF) / 255.0f
        val alpha = ((colorInt shr 24) and 0xFF) / 255.0f

        return floatArrayOf(red, green, blue, alpha)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
        if (bitmap == null) return
        setBitmap(bitmap)
    }

    fun setOnSurfaceCreatedAndReadyCallback(onSurfaceCreatedAndReady: () -> Unit) {
        this.onSurfaceCreatedAndReady = onSurfaceCreatedAndReady
    }

    fun setBitmap(bitmap: Bitmap) {
        isPaused = true
        if (square != null)
            square?.destroy()
        square = Square(surfaceView, context, bitmap, width.toFloat(), height.toFloat())
        square?.setResolution(width.toFloat(), height.toFloat())
        updateViewLocation()
        onSurfaceCreatedAndReady?.invoke()
        isPaused = false
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (square == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
            }

            MotionEvent.ACTION_MOVE -> {

                    square?.onTouch(event.x, event.y, isTouching)


            }

            MotionEvent.ACTION_UP -> {
                isTouching = false

                    square?.onTouch(event.x, event.y, isTouching)

            }
        }
        //square?.onTouch(event.x, event.y, isTouching)
        return true
    }


    override fun onDrawFrame(gl: GL10?) {
        if (isPaused || square == null) return
        square!!.draw()
    }

    fun paused(isPaused: Boolean) {
        this.isPaused = isPaused
    }

    fun updateViewLocation() {
        if (square == null) {
            Log.e("updateViewLocation", "Square is not initialized yet")
            return
        }
        square?.updateOffset(locationOnScreen)
    }

    fun toggleDisplacement(enabled: Boolean) {
        square?.toggleGridMap(enabled)
    }


    companion object {
        @JvmStatic
        fun loadFromAsset(context: Context, fileName: String): String {
            val assetManager = context.assets
            try {
                val inputStream = assetManager.open(fileName)
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                do {
                    line = bufferedReader.readLine()
                    if (line != null) {
                        stringBuilder.append(line)
                        stringBuilder.append("\n")
                    }
                } while (line != null)
                bufferedReader.close()
                inputStream.close()
                return stringBuilder.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }
    }
}


class Square(
    private val surfaceView: GLImageSurfaceView,
    private val context: Context,
    private val bitmap: Bitmap,
    private var width: Float,
    private var height: Float
) {
    private var scaleX = 0f
    private var scaleY = 0f
    private var mainProgram = 0
    private var gridProgram = 0


    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val gridProjectionMatrix = FloatArray(16)
    private val gridViewMatrix = FloatArray(16)
    private val gridModelMatrix = FloatArray(16)
    private val gridMvpMatrix = FloatArray(16)


    private val squareVertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f, // bottom left
        1.0f, -1.0f, 0.0f,  // bottom right
        -1.0f, 1.0f, 0.0f,  // top left
        1.0f, 1.0f, 0.0f    // top right
    )

    private val textureCoordinates = floatArrayOf(
        0.0f, 1.0f, // bottom left
        1.0f, 1.0f, // bottom right
        0.0f, 0.0f, // top left
        1.0f, 0.0f  // top right
    )

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private var isTouching = false

    private var bitmapWidth = 0f
    private var bitmapHeight = 0f
    private var startRangeX = 0f
    private var endRangeX = width
    private var startRangeY = 0f
    private var endRangeY = 0.2f * height
    private val maxTouchSize = 250f
    private var touchSize = maxTouchSize / 2 //250f
    private val minRangeSkippingValue = 0.75f
    private val maxRangeSkippingValue = 0.9f
    private var rangeSkippingValue = maxRangeSkippingValue
    private var touchX = 0f
    private var touchY = 0f
    private var previousTouchX = 0f
    private var previousTouchY = 0f


    private var textureHandle = 0

    private var gridTextureHandle = 0
    private var gridTextureFBO = 0
    private var gridSize = 700f
    private var gridTextureWidth = ceil(sqrt(gridSize.toDouble())).toInt()
    private var gridTextureHeight = ceil(sqrt(gridSize.toDouble())).toInt()
    private var screenOffsetX = 0f
    private var screenOffsetY = 0f
    private var renderingHeight = 0f
    private var renderingWidth = 0f
    private var meshPositionX = 0f
    private var meshPositionY = 0f
    private var aspectRatio = 1f
    private var imageAspectRatio = 1f

    private var activeProgram = -1
    private var useScaling = false


    private var uRelaxation = 0.965f
    private var uStrength = 0.8f
    private var uDistance = 0.6f
    private var fullDisplacement = false
    private var minDistanceMultiplier = 100
    private var maxDistanceMultiplier = 1000
    private var rgbShift = true
    private var gridMap = false
    private var restoreDisplacement = true

    private var isRestoringDisplacement = false
    private var handler = Handler(Looper.getMainLooper())
    private val enableRestoreDisplacementRunnable = Runnable {
        isRestoringDisplacement = true
        surfaceView.queueEvent {
            toggleRestoreDisplacement(true)
            handler.postDelayed(disableRestoreDisplacementRunnable, 2000)
        }
    }
    private val disableRestoreDisplacementRunnable = Runnable {
        surfaceView.queueEvent {
            toggleRestoreDisplacement(false)
        }
        isRestoringDisplacement = false
    }

    init {
        bitmapWidth = bitmap.width.toFloat()
        bitmapHeight = bitmap.height.toFloat()
        aspectRatio = width / height
        imageAspectRatio = bitmapWidth / bitmapHeight

        /*if (bitmapWidth > bitmapHeight)
            gridTextureWidth *= 2
        else if (bitmapHeight > bitmapWidth)
            gridTextureHeight *= 2*/

        val pixelRatio = min(2f, context.resources.displayMetrics.density)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, pixelRatio.toInt())
        createMainProgram()
        createGridTextureProgram()
        if (useScaling) {
            val byteBuffer = ByteBuffer.allocateDirect(squareVertices.size * 4)
            byteBuffer.order(ByteOrder.nativeOrder())
            vertexBuffer = byteBuffer.asFloatBuffer()
            val textureCordsByteBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
            textureCordsByteBuffer.order(ByteOrder.nativeOrder())
            textureBuffer = textureCordsByteBuffer.asFloatBuffer()
            textureBuffer.put(textureCoordinates)
            textureBuffer.position(0)
            setSizes()
            setMeshPosition()
            createTextures()
            genGridFrameBuffer()
            setCameraForScaledMeshes()
            setGridCamera()
        } else {
            val byteBuffer = ByteBuffer.allocateDirect(squareVertices.size * 4)
            byteBuffer.order(ByteOrder.nativeOrder())
            vertexBuffer = byteBuffer.asFloatBuffer()
            vertexBuffer.put(squareVertices)
            vertexBuffer.position(0)
            val textureCordsByteBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
            textureCordsByteBuffer.order(ByteOrder.nativeOrder())
            textureBuffer = textureCordsByteBuffer.asFloatBuffer()
            textureBuffer.put(textureCoordinates)
            textureBuffer.position(0)
            createTextures()
            genGridFrameBuffer()
            setMainCamera()
            setGridCamera()
        }
    }

    private fun setSizes() {
        val cameraZ = 10f
        val fov = 75f * (Math.PI / 180)
        renderingHeight = (cameraZ * tan(fov / 2) * 2).toFloat()
        renderingWidth = renderingHeight * (aspectRatio)
    }


    private fun setMeshPosition() {
        val (meshWidth, meshHeight) = if (imageAspectRatio > aspectRatio) {
            // Image is wider than the view
            val adjustedHeight = (renderingWidth / imageAspectRatio)
            Pair(renderingWidth, adjustedHeight)
        } else {
            // Image is taller than the view
            val adjustedWidth = (renderingHeight * imageAspectRatio)
            Pair(adjustedWidth, renderingHeight)
        }

        meshPositionX = (renderingWidth - meshWidth) / 2
        meshPositionY = (renderingHeight - meshHeight) / 2

        scaleX = meshWidth
        scaleY = meshHeight

        updateSquareVerticesAndBuffers(meshWidth, meshHeight, meshPositionX, meshPositionY)
    }

    private fun updateSquareVerticesAndBuffers(
        scaleX: Float,
        scaleY: Float,
        meshPositionX: Float,
        meshPositionY: Float,
    ) {
        // Scale and position the bottom left vertex
        squareVertices[0] = -scaleX / 2f + meshPositionX
        squareVertices[1] = -scaleY / 2f + meshPositionY

        // Scale and position the bottom right vertex
        squareVertices[3] = scaleX / 2f + meshPositionX
        squareVertices[4] = -scaleY / 2f + meshPositionY

        // Scale and position the top left vertex
        squareVertices[6] = -scaleX / 2f + meshPositionX
        squareVertices[7] = scaleY / 2f + meshPositionY

        // Scale and position the top right vertex
        squareVertices[9] = scaleX / 2f + meshPositionX
        squareVertices[10] = scaleY / 2f + meshPositionY

        vertexBuffer.clear()
        vertexBuffer.put(squareVertices)
        vertexBuffer.position(0)

    }


    private fun createMainProgram() {
        val vertexShader = loadShader(
            GLES30.GL_VERTEX_SHADER,
            ImageRenderer.loadFromAsset(context, "shaders/vertex_shader.glsl")
        )
        val fragmentShader = loadShader(
            GLES30.GL_FRAGMENT_SHADER,
            ImageRenderer.loadFromAsset(context, "shaders/fragment_shader.glsl")
        )
        mainProgram = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }
        switchProgramTo(mainProgram)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(mainProgram, "uDisplacement"),
            if (gridMap) 1f else 0f
        )
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(mainProgram, "uRGBShift"),
            if (rgbShift) 1 else 0
        )
    }

    private fun createGridTextureProgram() {
        val gridFragmentShader = loadShader(
            GLES30.GL_FRAGMENT_SHADER,
            ImageRenderer.loadFromAsset(context, "shaders/grid_fragment_shader.glsl")
        )

        val gridVertexShader = loadShader(
            GLES30.GL_VERTEX_SHADER,
            ImageRenderer.loadFromAsset(context, "shaders/grid_vertex.glsl")
        )

        gridProgram = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, gridFragmentShader)
            GLES30.glAttachShader(it, gridVertexShader)
            GLES30.glLinkProgram(it)
        }
        switchProgramTo(gridProgram)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(gridProgram, "uMouseMove"), 0f)
        val relaxationLocation = GLES30.glGetUniformLocation(gridProgram, "uRelaxation")
        GLES30.glUniform1f(relaxationLocation, uRelaxation)
        val uGridSizeLocation = GLES30.glGetUniformLocation(gridProgram, "uGridSize")
        GLES30.glUniform1f(uGridSizeLocation, gridSize)
        val uDistanceLocation = GLES30.glGetUniformLocation(gridProgram, "uDistance")
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(gridProgram, "uRestoreDisplacement"),
            if (restoreDisplacement) 1 else 0
        )
        GLES30.glUniform1f(
            uDistanceLocation,
            if (fullDisplacement) uDistance * maxDistanceMultiplier else uDistance * minDistanceMultiplier
        )
    }

    private fun createTextures() {
        textureHandle = loadTexture(bitmap)
        gridTextureHandle = createGridTexture()
    }

    private fun genGridFrameBuffer() {
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        gridTextureFBO = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, gridTextureFBO)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            gridTextureHandle,
            0
        )

        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer  is not complete")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun updateOffset(locationOnScreen: IntArray) {
        screenOffsetX = (locationOnScreen[0] / width) * 2f - 1f
        screenOffsetY = -(locationOnScreen[1] / height) * 2f + 1
    }

    private fun checkGLError(tag: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(tag, "glError: $error")
        }
    }

    private fun createGridTexture(): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_NEAREST
        )

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA16F,
            gridTextureWidth,
            gridTextureHeight,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_FLOAT,
            null
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return textureId
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) {
            throw RuntimeException("Error loading texture for bitmap")
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_NEAREST
        )

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmapWidth = bitmap.width.toFloat()
        bitmapHeight = bitmap.height.toFloat()
        //bitmap.recycle()

        // Unbind the texture to avoid accidental modification
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return textureIds[0]
    }

    fun setResolution(resolutionX: Float, resolutionY: Float) {
        width = resolutionX
        height = resolutionY
        aspectRatio = width / height
        startRangeX = -touchSize
        endRangeX = touchSize
        startRangeY = 0f // 15% of the height for start range in Y-axis
        endRangeY = touchSize * 2 // 0.15f * height // 30% of the height for end range in Y-axis
        if (useScaling) {
            setSizes()
            setMeshPosition()
            setCameraForScaledMeshes()
        } else
            setMainCamera()
        setGridCamera()
    }


    fun onTouch(x: Float, y: Float, isTouching: Boolean) {
        this.isTouching = isTouching
        touchX = x + screenOffsetX
        touchY = y + screenOffsetY
        if(!isRestoringDisplacement) {
            handler.removeCallbacks(enableRestoreDisplacementRunnable)
            handler.removeCallbacks(disableRestoreDisplacementRunnable)
        }
        if(!isTouching && !restoreDisplacement){
            handler.postDelayed(enableRestoreDisplacementRunnable, 2000)
        }
    }

    fun onAccelerometerInput(xForce: Float, yForce: Float){
        isTouching = false
        val scaleFactor = 10
        touchX = (xForce / scaleFactor) * width / 2 + width / 2
        touchY = (yForce / scaleFactor) * height / 2 + height / 2
    }

    private fun normalizeMove() {
        var normalizedX = (touchX / width) * 2f - 1f
        var normalizedY = -(touchY / height) * 2f + 1f

        val inverseMVPMatrix = FloatArray(16)
        Matrix.invertM(inverseMVPMatrix, 0, mvpMatrix, 0)
        if (width > height)  // Landscape
            normalizedY /= aspectRatio
        else
            normalizedX *= aspectRatio

        val ndcPoint = floatArrayOf(normalizedX, normalizedY, 0f, 1f)
        val worldPoint = FloatArray(4)
        Matrix.multiplyMV(worldPoint, 0, inverseMVPMatrix, 0, ndcPoint, 0)

        val worldX = worldPoint[0] / worldPoint[3]
        val worldY = worldPoint[1] / worldPoint[3]

        if (worldX >= -1.0f && worldX <= 1.0f && worldY >= -1.0f && worldY <= 1.0f) {
            val u = (worldX + 1.0f) / 2.0f
            val v = 1.0f - (worldY + 1.0f) / 2.0f
            updateGridMove(u, v)
        }
        previousTouchX = touchX
        previousTouchY = touchY
    }

    private fun normalizeMoveForScaledMeshes() {
        val normalizedX = (touchX / width) * 2f - 1f
        val normalizedY = -(touchY / height) * 2f + 1f
        Log.e("TOUCH", " $normalizedX $normalizedY")
        // Set up the ray from the camera through the normalized coordinates
        val rayStart = floatArrayOf(normalizedX, normalizedY, -1f, 1f)
        val rayEnd = floatArrayOf(normalizedX, normalizedY, 1f, 1f)

        // Invert the MVP matrix to transform the ray into world space
        val invertedMvpMatrix = FloatArray(16)
        Matrix.invertM(invertedMvpMatrix, 0, mvpMatrix, 0)
        Matrix.multiplyMV(rayStart, 0, invertedMvpMatrix, 0, rayStart, 0)
        Matrix.multiplyMV(rayEnd, 0, invertedMvpMatrix, 0, rayEnd, 0)

        // Calculate the ray direction
        val rayDirection = floatArrayOf(
            rayEnd[0] - rayStart[0],
            rayEnd[1] - rayStart[1],
            rayEnd[2] - rayStart[2]
        )
        val length = sqrt(
            rayDirection[0] * rayDirection[0] +
                    rayDirection[1] * rayDirection[1] +
                    rayDirection[2] * rayDirection[2]
        )
        rayDirection[0] /= length
        rayDirection[1] /= length
        rayDirection[2] /= length


        val t = -rayStart[2] / rayDirection[2]
        val intersectX = rayStart[0] + t * rayDirection[0]
        val intersectY = rayStart[1] + t * rayDirection[1]

        // Check if the intersection is within the bounds of the square (-1 to 1)
        if (intersectX >= -1f && intersectX <= 1f && intersectY >= -1f && intersectY <= 1f) {
            // Convert the intersection to UV coordinates
            val u = (intersectX + 1f) / 2f
            val v = (1f - intersectY) / 2f
            updateGridMove(u, v)
        }
        previousTouchX = touchX
        previousTouchY = touchY
    }

    private fun updateGridMove(u: Float, v: Float) {
        GLES30.glUniform1f(GLES30.glGetUniformLocation(gridProgram, "uMouseMove"), 1f)
        val touchHandle = GLES30.glGetUniformLocation(gridProgram, "uMouse")
        val deltaTouchHandle = GLES30.glGetUniformLocation(gridProgram, "uDeltaMouse")
        val current = getUniformVec2(gridProgram, "uMouse")
        current[0] = (u - current[0]) * (uStrength * 100)
        current[1] = (v - current[1]) * (uStrength * 100)
        current[0] = if (gridTextureWidth < 5)  current[0] * 1.5f else current[0]
        current[1] = if (gridTextureHeight < 5)  current[1] * 1.5f else current[1]
        GLES30.glUniform2f(deltaTouchHandle, current[0], current[1])
        GLES30.glUniform2f(touchHandle, u, v)
    }

    private fun getUniformVec2(programId: Int, uniformName: String): FloatArray {
        GLES30.glUseProgram(programId)
        val uniformLocation = GLES30.glGetUniformLocation(programId, uniformName)
        if (uniformLocation == -1) {
            // throw RuntimeException("Uniform variable '$uniformName' not found in the shader program.")
        }
        val uniformValues = FloatArray(2)
        GLES30.glGetUniformfv(programId, uniformLocation, uniformValues, 0)
        return uniformValues
    }


    private fun setMainCamera() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)

        val aspectRatio = width / height
        val imageAspectRatio = bitmapWidth / bitmapHeight

        if (aspectRatio > imageAspectRatio) {
            Matrix.orthoM(
                projectionMatrix, 0,
                -aspectRatio / imageAspectRatio, aspectRatio / imageAspectRatio,
                -1f, 1f,
                -1f, 1f
            )
        } else {
            Matrix.orthoM(
                projectionMatrix, 0,
                -1f, 1f,
                -1f * imageAspectRatio / aspectRatio, 1f * imageAspectRatio / aspectRatio,
                -1f, 1f
            )
        }

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        switchProgramTo(mainProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "modelMatrix"),
            1,
            false,
            modelMatrix,
            0
        )
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "viewMatrix"),
            1,
            false,
            viewMatrix,
            0
        )
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "projectionMatrix"),
            1,
            false,
            projectionMatrix,
            0
        )
    }

    private fun setCameraForScaledMeshes(
        fov: Float = 75f,
        near: Float = 0.1f,
        far: Float = 100f,
        zoom: Float = 1.0f,
        filmOffset: Float = 0.0f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        val deg2rad = Math.PI / 180.0
        val top = near * tan(deg2rad * 0.5 * fov) / zoom
        val height = 2.0 * top
        val width = aspectRatio * height
        var left = -0.5 * width

        if (filmOffset != 0.0f) {
            left += near * filmOffset / (aspectRatio * 0.036f) // Assuming 35mm film width
        }

        val right = (left + width).toFloat()
        val bottom = top.toFloat() - height.toFloat()

        // Update the provided projectionMatrix using frustumM
        Matrix.frustumM(
            projectionMatrix,
            0,
            left.toFloat(),
            right,
            bottom,
            top.toFloat(),
            near,
            far
        )
        val cameraZ = 10f/*if (bitmapWidth > bitmapHeight) { // Landscape
            (10f * (bitmapWidth / bitmapHeight) / (width / height)).toFloat() - (meshPositionX + meshPositionY) * 2
        } else { // Portrait
            (10f / (width / height)).toFloat() - (meshPositionX + meshPositionY) / 2
        }*/
        // Set the camera position in the view matrix (translate the camera along the z-axis)
        Matrix.translateM(viewMatrix, 0, -meshPositionX, -meshPositionY, -cameraZ)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        switchProgramTo(mainProgram)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "modelMatrix"),
            1,
            false,
            modelMatrix,
            0
        )
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "viewMatrix"),
            1,
            false,
            viewMatrix,
            0
        )
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(mainProgram, "projectionMatrix"),
            1,
            false,
            projectionMatrix,
            0
        )
    }

    private fun setGridCamera() {
        val left = -1.0f
        val right = 1.0f
        val bottom = -1.0f
        val top = 1.0f
        val near = 0.1f
        val far = 10.0f
        Matrix.orthoM(gridProjectionMatrix, 0, left, right, bottom, top, near, far)
        Matrix.setLookAtM(gridViewMatrix, 0, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        Matrix.setIdentityM(gridModelMatrix, 0)
        Matrix.multiplyMM(gridMvpMatrix, 0, gridViewMatrix, 0, gridModelMatrix, 0)
        Matrix.multiplyMM(gridMvpMatrix, 0, gridProjectionMatrix, 0, gridMvpMatrix, 0)
        switchProgramTo(gridProgram)
        val gridMvpMatrixHandle = GLES30.glGetUniformLocation(gridProgram, "uMVPMatrix")
        GLES30.glUniformMatrix4fv(gridMvpMatrixHandle, 1, false, gridMvpMatrix, 0)
    }

    private fun drawWithGridProgram() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, gridTextureFBO)
        GLES30.glViewport(0, 0, gridTextureWidth, gridTextureHeight)
        switchProgramTo(gridProgram)

        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, textureBuffer)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, gridTextureHandle)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(gridProgram, "uGrid"), 0)

        val resolutionHandle = GLES30.glGetUniformLocation(gridProgram, "uResolution")
        GLES30.glUniform2f(
            resolutionHandle,
            gridTextureWidth.toFloat(),
            gridTextureHeight.toFloat()
        )
        if (previousTouchX != touchX || previousTouchY != touchY) {
            if (useScaling)
                normalizeMoveForScaledMeshes()
            else
                normalizeMove()
        }


        val uMouseMoveCurrentValue = FloatArray(1)
        val uMouseMoveLocation = GLES30.glGetUniformLocation(gridProgram, "uMouseMove")
        GLES30.glGetUniformfv(gridProgram, uMouseMoveLocation, uMouseMoveCurrentValue, 0)
        //GLES30.glUniform1f(uMouseMoveLocation, uMouseMoveCurrentValue[0] * 0.99f)
        GLES30.glUniform1f(uMouseMoveLocation, uMouseMoveCurrentValue[0] - 0.0000001f) // slow displacement restore speed
        //GLES30.glUniform1f(uMouseMoveLocation, uMouseMoveCurrentValue[0] - 0.001f)

        val uRelaxationCurrentValue = FloatArray(1)
        val uRelaxationLocation = GLES30.glGetUniformLocation(gridProgram, "uRelaxation")
        GLES30.glGetUniformfv(gridProgram, uRelaxationLocation, uRelaxationCurrentValue, 0)

        val uDeltaTouchLocation = GLES30.glGetUniformLocation(gridProgram, "uDeltaMouse")
        val uDeltaMouseCurrentValue = getUniformVec2(gridProgram, "uDeltaMouse")

        GLES30.glUniform2f(
            uDeltaTouchLocation,
            uDeltaMouseCurrentValue[0] * uRelaxationCurrentValue[0],
            uDeltaMouseCurrentValue[1] * uRelaxationCurrentValue[0]
        )

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        checkGLError("GridProgram")
    }

    private fun drawWithMainProgram() {
        if (bitmapWidth == 0f || bitmapHeight == 0f) return
        switchProgramTo(mainProgram)
        GLES30.glViewport(0, 0, width.toInt(), height.toInt())
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, textureBuffer)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mainProgram, "uTexture"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, gridTextureHandle)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(mainProgram, "uGrid"), 1)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(mainProgram, "uImageResolution"),
            width,
            height
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(mainProgram, "uContainerResolution"),
            width,
            height
        )

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        checkGLError("MainProgram")
    }

    fun draw() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawWithGridProgram()
        drawWithMainProgram()
    }

    private fun switchProgramTo(program: Int) {
        GLES30.glUseProgram(program)
        activeProgram = program
    }


    private fun readFramebufferPixels(width: Int, height: Int) {
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, gridTextureFBO)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Log.e(
            "FramebufferCheck",
            "First pixel: " + buffer.get(0) + ", " + buffer.get(1) + ", " + buffer.get(2) + ", " + buffer.get(
                3
            )
        )
    }


    private fun animateBubble(toX: Float, toY: Float) {
        val animatorSet = AnimatorSet()
        val translateXAnimator = ValueAnimator.ofFloat(endRangeX - touchSize, toX)
        translateXAnimator.addUpdateListener {
            startRangeX = it.animatedValue as Float - touchSize
            endRangeX = it.animatedValue as Float + touchSize
        }
        val translateYAnimator = ValueAnimator.ofFloat(endRangeY, toY)
        translateYAnimator.addUpdateListener {
            startRangeY = (it.animatedValue as Float) - touchSize * 2
            endRangeY = it.animatedValue as Float
        }
        val touchSizeAnimator = ValueAnimator.ofFloat(touchSize, maxTouchSize)
        touchSizeAnimator.addUpdateListener {
            touchSize = it.animatedValue as Float
        }

        val rangeSkippingValueAnimator =
            ValueAnimator.ofFloat(rangeSkippingValue, minRangeSkippingValue)
        rangeSkippingValueAnimator.addUpdateListener {
            rangeSkippingValue = it.animatedValue as Float
        }
        val animators = mutableListOf<Animator>()
        animators.add(translateXAnimator)
        animators.add(translateYAnimator)
        animators.add(touchSizeAnimator)
        animators.add(rangeSkippingValueAnimator)
        animatorSet.apply {
            interpolator = AnticipateOvershootInterpolator()
            playTogether(animators)
            duration = 600
            start()
        }
    }

    private fun narrowBubble(x: Float, y: Float) {
        val animatorSet = AnimatorSet()
        val touchAreaAnimator = ValueAnimator.ofFloat(touchSize, maxTouchSize / 2)
        touchAreaAnimator.addUpdateListener {
            touchSize = it.animatedValue as Float
            startRangeX = x - touchSize
            endRangeX = x + touchSize
            startRangeY = y - touchSize * 2
            endRangeY = y
        }
        val rangeSkippingValueAnimator =
            ValueAnimator.ofFloat(rangeSkippingValue, maxRangeSkippingValue)
        rangeSkippingValueAnimator.addUpdateListener {
            rangeSkippingValue = it.animatedValue as Float
        }
        val animators = mutableListOf<Animator>()
        animators.add(touchAreaAnimator)
        animators.add(rangeSkippingValueAnimator)
        animatorSet.apply {
            interpolator = AnticipateOvershootInterpolator()
            playTogether(animators)
            duration = 600
            start()
        }
    }

    private fun loadShader(type: Int, shaderSrc: String): Int {
        val compiled = IntArray(1)
        val shader: Int = GLES30.glCreateShader(type)
        if (shader == 0) {
            return 0
        }
        GLES30.glShaderSource(shader, shaderSrc)
        GLES30.glCompileShader(shader)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            throw RuntimeException(
                "Could not compile program: "
                        + GLES30.glGetShaderInfoLog(shader) + " " + shaderSrc
            )
        }
        return shader
    }

    fun destroy() {
        bitmap.recycle()
        GLES30.glDeleteProgram(mainProgram)
        GLES30.glDeleteProgram(gridProgram)
    }

    fun toggleGridMap(enabled: Boolean) {
        gridMap = enabled
        if (activeProgram == mainProgram) {
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(mainProgram, "uDisplacement"),
                if (enabled) 1f else 0f
            )
        } else {
            switchProgramTo(mainProgram)
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(mainProgram, "uDisplacement"),
                if (enabled) 1f else 0f
            )
            switchProgramTo(gridProgram)
        }

    }

    fun toggleRGBShift(enabled: Boolean) {
        rgbShift = enabled
        if (activeProgram == mainProgram) {
            GLES30.glUniform1i(
                GLES30.glGetUniformLocation(mainProgram, "uRGBShift"),
                if (enabled) 1 else 0
            )
        } else {
            switchProgramTo(mainProgram)
            GLES30.glUniform1i(
                GLES30.glGetUniformLocation(mainProgram, "uRGBShift"),
                if (enabled) 1 else 0
            )
            switchProgramTo(gridProgram)
        }
    }

    fun toggleRestoreDisplacement(enabled: Boolean) {
        restoreDisplacement = enabled
        if (activeProgram == gridProgram) {
            GLES30.glUniform1i(
                GLES30.glGetUniformLocation(gridProgram, "uRestoreDisplacement"),
                if (enabled) 1 else 0
            )
        } else {
            switchProgramTo(gridProgram)
            GLES30.glUniform1i(
                GLES30.glGetUniformLocation(gridProgram, "uRestoreDisplacement"),
                if (enabled) 1 else 0
            )
            switchProgramTo(mainProgram)
        }
    }

    fun setRelaxation(uRelaxation: Float) {
        this.uRelaxation = uRelaxation
        if (activeProgram == gridProgram) {
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(gridProgram, "uDisplacement"),
                uRelaxation
            )
        } else {
            switchProgramTo(gridProgram)
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(gridProgram, "uDisplacement"),
                uRelaxation
            )
            switchProgramTo(mainProgram)
        }

    }

    fun setDistance(uDistance: Float) {
        this.uDistance = uDistance
        if (activeProgram == gridProgram) {
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(gridProgram, "uDistance"),
                if (fullDisplacement) uDistance * maxDistanceMultiplier else uDistance * minDistanceMultiplier
            )
        } else {
            switchProgramTo(gridProgram)
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(gridProgram, "uDistance"),
                if (fullDisplacement) uDistance * maxDistanceMultiplier else uDistance * minDistanceMultiplier
            )
            switchProgramTo(mainProgram)
        }

    }

    fun setStrength(strength: Float) {
        this.uStrength = strength
    }

    private fun getFloatUniformValue(program: Int, uniformName: String): Float {
        val uniformLocation = GLES30.glGetUniformLocation(program, uniformName)
        if (uniformLocation == -1) {
            Log.e("Shader", "Uniform $uniformName not found in program $program")
            return 0.0f // Return a default value or handle the error appropriately
        }
        val uniformValue = FloatArray(1)
        GLES30.glGetUniformfv(program, uniformLocation, uniformValue, 0)
        return uniformValue[0]
    }


}
