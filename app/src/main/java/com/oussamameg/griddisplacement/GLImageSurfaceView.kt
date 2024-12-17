package com.oussamameg.griddisplacement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay


class GLImageSurfaceView : GLSurfaceView {
    val imageRenderer: ImageRenderer

    constructor(context: Context) : this(context, 0, null)
    constructor(context: Context, drawableId: Int) : this(context, drawableId, null)

    @SuppressLint("ClickableViewAccessibility")
    constructor(context: Context, drawableId: Int, attrs: AttributeSet?) : super(
        context,
        attrs
    ) {
        setEGLContextClientVersion(3)
        setEGLContextFactory(ContextFactory())
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        imageRenderer = ImageRenderer(this, context, null)
        setRenderer(imageRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(true)
            //queueEvent {
            imageRenderer.onTouchEvent(event)
            //}
            // true
        }
        if (drawableId <= 0) return
        setImageResId(drawableId)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        getLocationOnScreen(imageRenderer.locationOnScreen)
        imageRenderer.updateViewLocation()
    }

    fun paused(isPaused: Boolean) {
        imageRenderer.paused(isPaused)
    }

    fun setImageResId(drawableId: Int) {
        val options = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeResource(resources, drawableId, options) ?: return
        setBitmapImage(bitmap)
    }

    fun setBitmapImage(bitmap: Bitmap) {
        if (imageRenderer.isSurfaceReady)
            queueEvent {
                imageRenderer.setBitmap(bitmap)
            }
        else
            Handler(Looper.getMainLooper()).postDelayed({
                queueEvent {
                    setBitmapImage(bitmap)
                }
            }, 50)
    }

    fun toggleRGBShift(enabled: Boolean) {
        queueEvent {
            imageRenderer.square?.toggleRGBShift(enabled)
        }
    }

    fun toggleRestoreDisplacement(enabled: Boolean) {
        queueEvent {
            imageRenderer.square?.toggleRestoreDisplacement(enabled)
        }
    }

    fun toggleGridMap(enabled: Boolean) {
        queueEvent {
            imageRenderer.square?.toggleGridMap(enabled)
        }
    }

    fun setRelaxation(relaxation: Float) {
        queueEvent {
            imageRenderer.square?.setRelaxation(relaxation)
        }
    }

    fun setDistance(distance: Float) {
        queueEvent {
            imageRenderer.square?.setDistance(distance)
        }
    }

    fun setStrength(strength: Float) {
        queueEvent {
            imageRenderer.square?.setStrength(strength)
        }
    }


    private class ContextFactory : EGLContextFactory {
        override fun createContext(
            egl: EGL10,
            display: EGLDisplay?,
            eglConfig: EGLConfig?
        ): EGLContext {
            val attribList = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
            return egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attribList)
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay?, context: EGLContext?) {
            egl.eglDestroyContext(display, context)
        }

        companion object {
            private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        }

    }

}