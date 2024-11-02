/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.facedetection

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: FaceDetectorResult? = null
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var isOverlayEnabled: Boolean = true
    private var scaleFactor: Float = 1f
    private var numFacesCaptured = 0
    private var bounds = Rect()
    private var confidence: Float = 0.9f
    private var faceId: String = "18707611416"
    // Handler for delayed face capture

    private var isFaceDetectedFor2Seconds = false
    private var captureHandler: Handler? = null

    init {
        initPaints()
        // Initialize the Handler in the main looper
        captureHandler = Handler(Looper.getMainLooper())
    }

    fun clear() {
        results = null
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }



    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

//        boxPaint.color = ContextCompat.getColor(context!!, R.color.mp_primary)
        boxPaint.color = Color.TRANSPARENT
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    fun enableOverlay() {
        isOverlayEnabled = true
        invalidate()
    }

    fun disableOverlay() {
        isOverlayEnabled = false
        invalidate()
    }


    override fun draw(canvas: Canvas) {
        // Draw the original content first
        super.draw(canvas)

        if (!isOverlayEnabled) {
            return  // Skip drawing overlay if it's disabled
        }

        // Tambahkan kode ini untuk membuat overlay transparan
        val overlayPaint = Paint().apply {
            color = Color.parseColor("#99000000") // Warna hitam dengan transparansi 60%
            style = Paint.Style.FILL
        }

        val centerX = width / 2f
        val centerY = height / 4.5f
        val radiusX = min(width, height) / 4f // Ubah ini untuk mengubah ukuran lingkaran
        val radiusY = 350f

        // Buat path untuk overlay
        val overlayPath = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addOval(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY, Path.Direction.CCW)
        }

        // Draw the overlay
        canvas.drawPath(overlayPath, overlayPaint)

        // Draw the rest of the overlay only if it is enabled
        results?.let {
            for (detection in it.detections()) {
                val boundingBox = detection.boundingBox()

                val top = boundingBox.top * scaleFactor
                val bottom = boundingBox.bottom * scaleFactor
                val left = boundingBox.left * scaleFactor
                val right = boundingBox.right * scaleFactor

                // Cek apakah kotak pembatas berada di dalam lingkaran
                if (isInsideCircle(left, top, right, bottom, centerX, centerY, radiusX, radiusY)) {
                    // Draw bounding box around detected faces only if overlay is enabled
                    if (isOverlayEnabled) {
                        val drawableRect = RectF(left, top, right, bottom)
                        canvas.drawOval(drawableRect, boxPaint)

                        // Create text to display alongside detected faces
                        val drawableText =
                            detection.categories()[0].categoryName() +
                                    " " +
                                    String.format(
                                        "%.2f",
                                        detection.categories()[0].score()
                                    )

                        // Calculate text width and height
                        textBackgroundPaint.getTextBounds(
                            drawableText,
                            0,
                            drawableText.length,
                            bounds
                        )
                        val textWidth = bounds.width()
                        val textHeight = bounds.height()

                        // Draw rect behind display text only if overlay is enabled
                        canvas.drawRect(
                            left,
                            top,
                            left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
                            top + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
                            textBackgroundPaint
                        )

                        // Draw text for detected face only if overlay is enabled
                        canvas.drawText(
                            drawableText,
                            left,
                            top + bounds.height(),
                            textPaint
                        )
                        isFaceDetectedFor2Seconds = true
                        captureHandler?.postDelayed({
                            captureFace()
                        }, 2000)
                    }
                    if (isFaceDetectedFor2Seconds) {
                        captureHandler?.removeCallbacksAndMessages(null)  // Hapus tugas tertunda jika ada
                        capturePhoto()
                    }
                }
            }
        }
    }
    private fun isInsideCircle(left: Float, top: Float, right: Float, bottom: Float, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): Boolean {
        return isPointInsideEllipse(left, top, centerX, centerY, radiusX, radiusY) &&
                isPointInsideEllipse(right, top, centerX, centerY, radiusX, radiusY) &&
                isPointInsideEllipse(left, bottom, centerX, centerY, radiusX, radiusY) &&
                isPointInsideEllipse(right, bottom, centerX, centerY, radiusX, radiusY)
    }
    private fun capturePhoto() {
        // Beri tahu parent (activity atau fragment) untuk menangani logika membuka kamera dan mengambil foto
        (context as? PhotoCaptureListener)?.onCapturePhoto()
    }

    interface PhotoCaptureListener {
        fun onCapturePhoto()
    }
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // Menghitung jarak antara dua titik
        return Math.sqrt(Math.pow((x2 - x1).toDouble(), 2.0) + Math.pow((y2 - y1).toDouble(), 2.0)).toFloat()
    }
    private fun isPointInsideEllipse(x: Float, y: Float, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): Boolean {
        // Menghitung apakah titik berada di dalam elips
        return Math.pow((x - centerX).toDouble(), 2.0) / Math.pow(radiusX.toDouble(), 2.0) +
                Math.pow((y - centerY).toDouble(), 2.0) / Math.pow(radiusY.toDouble(), 2.0) <= 1
    }
    fun setResults(
        detectionResults: FaceDetectorResult,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // Images, videos and camera live streams are displayed in FIT_START mode. So we need to scale
        // up the bounding box to match with the size that the images/videos/live streams being
        // displayed.
        scaleFactor = min(width * 1f / imageWidth, height * 1f / imageHeight)

        invalidate()
    }
    private fun drawOriginalContent(canvas: Canvas) {
        super.draw(canvas)
    }

    // Modify the captureFace() method to use the new drawOriginalContent method
    private fun captureFace() {
        // Check if the view has been laid out
        if (width > 0 && height > 0 && results != null && numFacesCaptured < 2) {
            results?.let {
                for (detection in it.detections()) {
                    // Check if the detection confidence is above the threshold (0.90f)
                    if (detection.categories()[0].score() >= 0.90f) {
                        // Create a bitmap with the same dimensions as the view
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                        // Create a canvas using the bitmap
                        val canvas = Canvas(bitmap)

                        // Draw only the original content onto the canvas
                        drawOriginalContent(canvas)

                        // Save the bitmap to the device's external storage using MediaStore
                        saveBitmapToMediaStore(bitmap)

                        numFacesCaptured++  // Tambahkan jumlah wajah yang sudah di-capture
                        if (numFacesCaptured >= 2) {
                            return  // Keluar dari loop setelah dua wajah terdeteksi
                        }
                    }
                }
            }
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap) {
        // Use MediaStore to save the bitmap as an image
        val imageUri = MediaStore.Images.Media.insertImage(
            context.contentResolver,
            bitmap,
            "FaceCapture_${System.currentTimeMillis()}",
            "Face captured using OverlayView"
        )
        Log.d("Image", "Image saved to"  + imageUri);

        // Notify the system that a new image has been added
        imageUri?.let {
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(it)))
        }
    }


    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}