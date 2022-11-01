/*
 * Copyright 2022 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.thibaultbee.streampack.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.SurfaceHolder
import androidx.core.app.ActivityCompat
import io.github.thibaultbee.streampack.R
import io.github.thibaultbee.streampack.logger.Logger
import io.github.thibaultbee.streampack.streamers.interfaces.ICameraStreamer
import io.github.thibaultbee.streampack.utils.getBackCameraList
import io.github.thibaultbee.streampack.utils.getCameraCharacteristics
import io.github.thibaultbee.streampack.utils.getFrontCameraList

/**
 * A [AutoFitSurfaceView] that manages [ICameraStreamer] preview.
 * In the case, you are using it, do not call [ICameraStreamer.startPreview] or
 * [ICameraStreamer.stopPreview] on application side.
 *
 * The [Manifest.permission.CAMERA] permission must be granted before using this class.s
 */
open class StreamerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AutoFitSurfaceView(context, attrs, defStyle) {
    private val cameraFacingDirection: FacingDirection
    private val defaultCameraId: String?

    var enableZoomOnPinch: Boolean

    var streamer: ICameraStreamer? = null
        /**
         * Set the [ICameraStreamer] to use.
         *
         * @param value the [ICameraStreamer] to use
         */
        set(value) {
            streamer?.stopPreview()
            field = value
            startPreviewIfReady()
        }

    /**
     * The [Listener] to listen to specific view events.
     */
    var listener: Listener? = null

    private val pinchGesture = ScaleGestureDetector(
        context,
        PinchToZoomOnScaleGestureListener()
    )

    init {
        holder.addCallback(StreamerHolderCallback())
        val a = context.obtainStyledAttributes(attrs, R.styleable.StreamerSurfaceView)

        try {
            cameraFacingDirection = FacingDirection.fromValue(
                a.getString(R.styleable.StreamerSurfaceView_cameraFacingDirection)
                    ?: DEFAULT_CAMERA_FACING.value
            )
            defaultCameraId = when (cameraFacingDirection) {
                FacingDirection.FRONT -> {
                    context.getFrontCameraList().firstOrNull()
                }
                FacingDirection.BACK -> {
                    context.getBackCameraList().firstOrNull()
                }
            }

            enableZoomOnPinch =
                a.getBoolean(R.styleable.StreamerSurfaceView_enableZoomOnPinch, true)
        } finally {
            a.recycle()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (streamer == null) {
            return super.onTouchEvent(event)
        }

        if (enableZoomOnPinch) {
            pinchGesture.onTouchEvent(event)
        }
        return true
    }

    private fun startPreviewIfReady(shouldFailSilently: Boolean = false) {
        if (display != null) {
            streamer?.let {
                try {
                    val camera = defaultCameraId ?: it.camera
                    Logger.i(TAG, "Starting on camera: $camera")

                    // Selects appropriate preview size
                    val previewSize = getPreviewOutputSize(
                        this.display,
                        context.getCameraCharacteristics(camera),
                        SurfaceHolder::class.java
                    )
                    Logger.d(
                        TAG,
                        "View finder size: $width x $height"
                    )
                    Logger.d(TAG, "Selected preview size: $previewSize")
                    setAspectRatio(previewSize.width, previewSize.height)

                    // To ensure that size is set, initialize camera in the view's thread
                    post {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            throw SecurityException("Camera permission is needed to run this application")
                        }
                        it.startPreview(this, camera)
                        listener?.onPreviewStarted()
                    }
                } catch (e: Exception) {
                    if (shouldFailSilently) {
                        Logger.w(TAG, e.toString(), e)
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = StreamerSurfaceView::class.java.simpleName

        private val DEFAULT_CAMERA_FACING = FacingDirection.BACK
    }

    private inner class StreamerHolderCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            startPreviewIfReady()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
            Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            streamer?.stopPreview()
            holder.addCallback(null)
        }
    }

    private inner class PinchToZoomOnScaleGestureListener :
        SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            streamer?.settings?.camera?.zoom?.let {
                it.onPinch(detector.scaleFactor)
                listener?.onZoomRationOnPinchChanged(it.zoomRatio)
            }
            return true
        }
    }


    interface Listener {
        fun onPreviewStarted() {}
        fun onZoomRationOnPinchChanged(zoomRatio: Float) {}
    }
}

enum class FacingDirection(val value: String) {
    FRONT("front"),
    BACK("back");

    companion object {
        fun fromValue(value: String): FacingDirection {
            return values().first { it.value == value }
        }
    }
}
