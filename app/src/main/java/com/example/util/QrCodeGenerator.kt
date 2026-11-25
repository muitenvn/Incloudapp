package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 640,
        darkColor: Int = Color.parseColor("#070B09"),
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    /**
     * Renders a crisp shareable QR card PNG into cacheDir/qr_exports/ and exposes it via FileProvider.
     */
    fun exportShareableQrUri(context: Context, url: String, title: String): Uri? {
        val qrBitmap = generateQrBitmap(url, sizePx = 760) ?: return null
        return runCatching {
            val cardWidth = 920
            val cardHeight = 1040
            val card = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(card)
            canvas.drawColor(Color.WHITE)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#27D884")
                style = Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRoundRect(
                RectF(24f, 24f, cardWidth - 24f, cardHeight - 24f),
                40f,
                40f,
                borderPaint
            )

            canvas.drawBitmap(qrBitmap, (cardWidth - qrBitmap.width) / 2f, 70f, null)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#070B09")
                textSize = 36f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val displayTitle = if (title.length > 34) title.take(31) + "…" else title
            canvas.drawText(displayTitle, cardWidth / 2f, 900f, titlePaint)

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475E52")
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            val displayUrl = if (url.length > 52) url.take(49) + "…" else url
            canvas.drawText(displayUrl, cardWidth / 2f, 954f, subtitlePaint)

            val exportDir = File(context.cacheDir, "qr_exports").apply { mkdirs() }
            val outFile = File(exportDir, "clouddrop_qr_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                card.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            qrBitmap.recycle()
            card.recycle()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }.getOrNull()
    }
}
