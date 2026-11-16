package com.famitube.app.ui.screens.player

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.FaceDetector
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat

/**
 * Lightweight, 100% on-device front-camera attention tracker using Camera2 + Android FaceDetector.
 * No frames or images are ever saved or transmitted; frames are analyzed in memory and discarded.
 */
class FrontCameraAttentionTracker(
    private val context: Context,
    private val onAttentionSample: (isViewing: Boolean) -> Unit
) {
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation: Int = 270
    private var isStarted: Boolean = false
    private var captureRunnable: Runnable? = null

    companion object {
        private const val SAMPLE_INTERVAL_MS = 2500L
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isStarted) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val frontCameraId = findFrontCameraId(manager) ?: return

        isStarted = true
        val thread = HandlerThread("FamiTubeAttentionThread").apply { start() }
        backgroundThread = thread
        val handler = Handler(thread.looper)
        backgroundHandler = handler

        try {
            val characteristics = manager.getCameraCharacteristics(frontCameraId)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
            val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = streamMap?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
            val targetSize = pickLowResSize(sizes)

            val reader = ImageReader.newInstance(
                targetSize.width,
                targetSize.height,
                ImageFormat.YUV_420_888,
                2
            )
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val viewing = detectFaceInYPlane(image, sensorOrientation)
                    onAttentionSample(viewing)
                } finally {
                    image.close()
                }
            }, handler)
            imageReader = reader

            manager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!isStarted) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    createCaptureSession(camera, reader, handler)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                }
            }, handler)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun createCaptureSession(
        camera: CameraDevice,
        reader: ImageReader,
        handler: Handler
    ) {
        try {
            val surface = reader.surface
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            }
            val captureRequest = requestBuilder.build()

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isStarted) {
                            session.close()
                            return
                        }
                        captureSession = session
                        val runnable = object : Runnable {
                            override fun run() {
                                if (!isStarted || captureSession !== session) return
                                try {
                                    session.capture(captureRequest, null, handler)
                                } catch (_: Exception) {
                                }
                                if (isStarted) {
                                    handler.postDelayed(this, SAMPLE_INTERVAL_MS)
                                }
                            }
                        }
                        captureRunnable = runnable
                        handler.post(runnable)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                handler
            )
        } catch (_: Exception) {
        }
    }

    fun stop() {
        isStarted = false
        captureRunnable?.let { backgroundHandler?.removeCallbacks(it) }
        captureRunnable = null
        backgroundHandler?.removeCallbacksAndMessages(null)
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            backgroundThread?.quitSafely()
        } catch (_: Exception) {
        }
        backgroundThread = null
        backgroundHandler = null
    }

    private fun findFrontCameraId(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val facing = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pickLowResSize(sizes: Array<Size>): Size {
        if (sizes.isEmpty()) return Size(320, 240)
        return sizes
            .filter { it.width >= 160 && it.height >= 120 }
            .minByOrNull { kotlin.math.abs(it.width * it.height - 320 * 240) }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(320, 240)
    }

    private fun detectFaceInYPlane(image: Image, rotationDegrees: Int): Boolean {
        val plane = image.planes.firstOrNull() ?: return false
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer = plane.buffer
        buffer.rewind()

        val pixels = IntArray(width * height)
        val rowBytes = ByteArray(rowStride)
        for (y in 0 until height) {
            val remaining = buffer.remaining()
            if (remaining <= 0) break
            val toRead = minOf(rowStride, remaining)
            buffer.get(rowBytes, 0, toRead)
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = x * pixelStride
                val luma = if (idx < toRead) (rowBytes[idx].toInt() and 0xFF) else 0
                pixels[rowOffset + x] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
            }
        }

        val rawBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565)
        val uprightBitmap = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, false)
            if (rotated !== rawBitmap) rawBitmap.recycle()
            rotated
        } else {
            rawBitmap
        }

        // FaceDetector requires even width
        val detectorBitmap = if (uprightBitmap.width % 2 != 0) {
            val even = Bitmap.createBitmap(
                uprightBitmap,
                0,
                0,
                uprightBitmap.width - 1,
                uprightBitmap.height
            )
            if (even !== uprightBitmap) uprightBitmap.recycle()
            even
        } else {
            uprightBitmap
        }

        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val count = FaceDetector(detectorBitmap.width, detectorBitmap.height, 1)
            .findFaces(detectorBitmap, faces)
        detectorBitmap.recycle()
        return count > 0
    }
}
