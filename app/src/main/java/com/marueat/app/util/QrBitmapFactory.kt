package com.marueat.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrBitmapFactory {
    private var cachedPlaceholder: Bitmap? = null

    fun bitmapFromText(text: String, size: Int = 512): Bitmap {
        if (text.isBlank()) return placeholderBitmap(size)

        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun placeholderBitmap(size: Int = 512): Bitmap {
        cachedPlaceholder?.let { if (it.width == size) return it }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.parseColor("#F5F5F5"))
        cachedPlaceholder = bitmap
        return bitmap
    }
}

