package com.example.lean.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.lean.R
import com.example.lean.data.CornerDirection
import com.example.lean.data.CornerEventEntity
import com.example.lean.data.LeanSafetyRating
import com.example.lean.data.RideEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object RideShareImageGenerator {

    private const val TAG = "RideShareGenerator"
    private const val IMAGE_WIDTH = 1080
    private const val IMAGE_HEIGHT = 1350

    fun shareRide(context: Context, ride: RideEntity, corners: List<CornerEventEntity> = emptyList()) {
        try {
            val bitmap = generateRideImage(ride, corners, context)
            val uri = saveBitmapToCache(context, bitmap, ride.rideId)

            if (uri != null) {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val dateStr = dateFormat.format(Date(ride.startTimeMs))
                val maxLeanStr = "${ride.maxAbsoluteLean.roundToInt()}°"
                val distanceStr = if (ride.distanceKm > 0) String.format(Locale.US, "%.1f km", ride.distanceKm) else null

                val summaryText = buildString {
                    append("🏍️ LEAN — Take your corner ($dateStr)\n")
                    append("• Max Lean Angle: $maxLeanStr\n")
                    append("• Ride Time: ${formatDurationText(ride.durationMs)}\n")
                    if (distanceStr != null) {
                        append("• Distance: $distanceStr\n")
                    }
                    if (corners.isNotEmpty()) {
                        val lefts = corners.count { it.direction == CornerDirection.LEFT }
                        val rights = corners.count { it.direction == CornerDirection.RIGHT }
                        append("• Corners Recorded: ${corners.size} ($lefts Left, $rights Right)\n")
                    }
                    if (ride.maxSpeedKmh > 0) {
                        append("• Max Speed: ${ride.maxSpeedKmh.roundToInt()} km/h\n")
                    }
                    append("\nLEAN — Take your corner")
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, summaryText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Ride Summary").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating or sharing ride summary image", e)
        }
    }

    fun generateRideImage(
        ride: RideEntity,
        corners: List<CornerEventEntity> = emptyList(),
        context: Context? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                Color.parseColor("#0B0E14"), Color.parseColor("#141923"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), bgPaint)

        // Decorative background accent circle
        val accentCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F2FE")
            alpha = 15
        }
        canvas.drawCircle(IMAGE_WIDTH / 2f, 320f, 400f, accentCirclePaint)

        // Outer Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#2A3447")
        }
        val outerRect = RectF(36f, 36f, IMAGE_WIDTH - 36f, IMAGE_HEIGHT - 36f)
        canvas.drawRoundRect(outerRect, 32f, 32f, borderPaint)

        // Cyan accent top border line
        val accentTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F2FE")
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        val topPath = Path().apply {
            addArc(RectF(36f, 36f, IMAGE_WIDTH - 36f, 100f), 180f, 180f)
        }
        canvas.drawPath(topPath, accentTopPaint)

        // 1. BRANDING HEADER WITH APP LOGO AND OFFICIAL NAME / TAGLINE
        var headerContentTop = 60f

        if (context != null) {
            try {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
                if (logoBitmap != null) {
                    val logoWidth = 84f
                    val logoHeight = logoWidth * (logoBitmap.height.toFloat() / logoBitmap.width.toFloat())
                    val logoLeft = (IMAGE_WIDTH - logoWidth) / 2f
                    val logoTop = 50f
                    val dstRect = RectF(logoLeft, logoTop, logoLeft + logoWidth, logoTop + logoHeight)
                    val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(logoBitmap, null, dstRect, logoPaint)
                    headerContentTop = logoTop + logoHeight + 32f
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing app logo in ride image", e)
            }
        }

        // App Name
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F2FE")
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
        }
        canvas.drawText("LEAN", IMAGE_WIDTH / 2f, headerContentTop, brandPaint)

        // Tagline: Take your corner
        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A3E635") // Primary Lime accent
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("Take your corner", IMAGE_WIDTH / 2f, headerContentTop + 32f, subBrandPaint)

        // Date & Subtitle: RIDE SUMMARY
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.US)
        val dateStr = dateFormat.format(Date(ride.startTimeMs)).uppercase()
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText("RIDE SUMMARY • $dateStr", IMAGE_WIDTH / 2f, headerContentTop + 64f, datePaint)

        // Divider Line
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3447")
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 235f, IMAGE_WIDTH - 80f, 235f, divPaint)

        // 2. HERO METRICS (Duration, Distance, Max Speed)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161D2A")
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3447")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Hero Card Box (Y: 260 to 400)
        val heroRect = RectF(60f, 260f, IMAGE_WIDTH - 60f, 400f)
        canvas.drawRoundRect(heroRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(heroRect, 24f, 24f, cardBorderPaint)

        // 3 Hero Columns: Duration, Distance, Max Speed
        val hasGps = ride.isGpsEnabled || ride.distanceKm > 0
        val colWidth = (IMAGE_WIDTH - 120f) / 3f

        // Hero 1: Duration
        drawMetricCol(
            canvas = canvas,
            label = "DURATION",
            valText = formatDurationText(ride.durationMs),
            colorHex = "#00F2FE",
            centerX = 60f + colWidth * 0.5f,
            centerY = 330f
        )

        // Divider 1
        canvas.drawLine(60f + colWidth, 275f, 60f + colWidth, 385f, divPaint)

        // Hero 2: Distance
        val distValStr = if (hasGps) String.format(Locale.US, "%.1f km", ride.distanceKm) else "N/A"
        drawMetricCol(
            canvas = canvas,
            label = "DISTANCE",
            valText = distValStr,
            colorHex = "#A3E635",
            centerX = 60f + colWidth * 1.5f,
            centerY = 330f
        )

        // Divider 2
        canvas.drawLine(60f + colWidth * 2f, 275f, 60f + colWidth * 2f, 385f, divPaint)

        // Hero 3: Max Speed
        val speedValStr = if (hasGps && ride.maxSpeedKmh > 0) String.format(Locale.US, "%.0f km/h", ride.maxSpeedKmh) else "N/A"
        drawMetricCol(
            canvas = canvas,
            label = "MAX SPEED",
            valText = speedValStr,
            colorHex = "#FF8C00",
            centerX = 60f + colWidth * 2.5f,
            centerY = 330f
        )

        // 3. LEAN ANGLE HIGHLIGHT CARD (Y: 425 to 700)
        val leanRect = RectF(60f, 425f, IMAGE_WIDTH - 60f, 700f)
        canvas.drawRoundRect(leanRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(leanRect, 24f, 24f, cardBorderPaint)

        val leanTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("MAXIMUM LEAN ANGLE PEAKS", IMAGE_WIDTH / 2f, 460f, leanTitlePaint)

        // Big Main Peak Angle
        val mainPeakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A3E635")
            textSize = 90f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val maxAbsDeg = ride.maxAbsoluteLean.roundToInt()
        canvas.drawText("$maxAbsDeg°", IMAGE_WIDTH / 2f, 550f, mainPeakPaint)

        val peakSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.1f
        }
        val maxSideLabel = if (ride.maxRightLean >= ride.maxLeftLean) "MAX RIGHT LEAN" else "MAX LEFT LEAN"
        canvas.drawText(maxSideLabel, IMAGE_WIDTH / 2f, 585f, peakSubPaint)

        // Left vs Right Sub-Cards inside Lean Card (Y: 610 to 685)
        canvas.drawLine(120f, 610f, IMAGE_WIDTH - 120f, 610f, divPaint)

        val sideColWidth = (IMAGE_WIDTH - 120f) / 2f

        val leftValStr = "${ride.maxLeftLean.roundToInt()}° LEFT"
        drawMetricCol(
            canvas = canvas,
            label = "LEFT PEAK",
            valText = leftValStr,
            colorHex = "#FF8C00",
            centerX = 60f + sideColWidth * 0.5f,
            centerY = 655f,
            textSizeSp = 28f
        )

        canvas.drawLine(IMAGE_WIDTH / 2f, 620f, IMAGE_WIDTH / 2f, 690f, divPaint)

        val rightValStr = "${ride.maxRightLean.roundToInt()}° RIGHT"
        drawMetricCol(
            canvas = canvas,
            label = "RIGHT PEAK",
            valText = rightValStr,
            colorHex = "#00F2FE",
            centerX = 60f + sideColWidth * 1.5f,
            centerY = 655f,
            textSizeSp = 28f
        )

        // 4. CORNERS SUMMARY CARD (Y: 725 to 900)
        val cornerRect = RectF(60f, 725f, IMAGE_WIDTH - 60f, 900f)
        canvas.drawRoundRect(cornerRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(cornerRect, 24f, 24f, cardBorderPaint)

        val cornerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("CORNER-BY-CORNER SUMMARY", IMAGE_WIDTH / 2f, 760f, cornerTitlePaint)

        val cornerColWidth = (IMAGE_WIDTH - 120f) / 3f
        val leftCount = corners.count { it.direction == CornerDirection.LEFT }
        val rightCount = corners.count { it.direction == CornerDirection.RIGHT }
        val fastestCorner = corners.maxByOrNull { it.speedAtMaxLeanKmh }

        drawMetricCol(
            canvas = canvas,
            label = "TOTAL CORNERS",
            valText = "${corners.size}",
            colorHex = "#00F2FE",
            centerX = 60f + cornerColWidth * 0.5f,
            centerY = 830f,
            textSizeSp = 30f
        )
        canvas.drawLine(60f + cornerColWidth, 780f, 60f + cornerColWidth, 880f, divPaint)

        drawMetricCol(
            canvas = canvas,
            label = "LEFT / RIGHT",
            valText = "$leftCount L • $rightCount R",
            colorHex = "#A3E635",
            centerX = 60f + cornerColWidth * 1.5f,
            centerY = 830f,
            textSizeSp = 26f
        )
        canvas.drawLine(60f + cornerColWidth * 2f, 780f, 60f + cornerColWidth * 2f, 880f, divPaint)

        val fastestStr = if (fastestCorner != null && fastestCorner.speedAtMaxLeanKmh > 0) {
            "${fastestCorner.speedAtMaxLeanKmh.roundToInt()} km/h"
        } else "N/A"
        drawMetricCol(
            canvas = canvas,
            label = "FASTEST APEX",
            valText = fastestStr,
            colorHex = "#FF8C00",
            centerX = 60f + cornerColWidth * 2.5f,
            centerY = 830f,
            textSizeSp = 26f
        )

        // 5. RIDE DISTRIBUTION CARD (Y: 925 to 1100)
        val distRect = RectF(60f, 925f, IMAGE_WIDTH - 60f, 1100f)
        canvas.drawRoundRect(distRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(distRect, 24f, 24f, cardBorderPaint)

        val distTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("RIDE LEAN DISTRIBUTION", IMAGE_WIDTH / 2f, 960f, distTitlePaint)

        // Distribution Progress Bar
        val barRect = RectF(100f, 980f, IMAGE_WIDTH - 100f, 1010f)
        val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0E14")
        }
        canvas.drawRoundRect(barRect, 14f, 14f, barBgPaint)

        val barWidth = barRect.width()
        var curX = barRect.left

        val straightPct = ride.straightPercentage.coerceIn(0f, 100f)
        val leftPct = ride.leftLeanPercentage.coerceIn(0f, 100f)
        val rightPct = ride.rightLeanPercentage.coerceIn(0f, 100f)

        if (straightPct > 0) {
            val w = barWidth * (straightPct / 100f)
            val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A3E635") }
            canvas.drawRect(curX, barRect.top, curX + w, barRect.bottom, sPaint)
            curX += w
        }
        if (leftPct > 0) {
            val w = barWidth * (leftPct / 100f)
            val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8C00") }
            canvas.drawRect(curX, barRect.top, curX + w, barRect.bottom, lPaint)
            curX += w
        }
        if (rightPct > 0) {
            val w = barWidth * (rightPct / 100f)
            val rPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00F2FE") }
            canvas.drawRect(curX, barRect.top, curX + w, barRect.bottom, rPaint)
        }

        // Distribution Legend Numbers
        val legendColWidth = (IMAGE_WIDTH - 120f) / 3f
        drawMetricCol(
            canvas = canvas,
            label = "STRAIGHT",
            valText = String.format(Locale.US, "%.1f%%", straightPct),
            colorHex = "#A3E635",
            centerX = 60f + legendColWidth * 0.5f,
            centerY = 1060f,
            textSizeSp = 26f
        )
        drawMetricCol(
            canvas = canvas,
            label = "LEFT LEAN",
            valText = String.format(Locale.US, "%.1f%%", leftPct),
            colorHex = "#FF8C00",
            centerX = 60f + legendColWidth * 1.5f,
            centerY = 1060f,
            textSizeSp = 26f
        )
        drawMetricCol(
            canvas = canvas,
            label = "RIGHT LEAN",
            valText = String.format(Locale.US, "%.1f%%", rightPct),
            colorHex = "#00F2FE",
            centerX = 60f + legendColWidth * 2.5f,
            centerY = 1060f,
            textSizeSp = 26f
        )

        // 6. SAFETY RATING (Y: 1125 to 1220)
        val safetyRect = RectF(60f, 1125f, IMAGE_WIDTH - 60f, 1220f)
        canvas.drawRoundRect(safetyRect, 20f, 20f, cardBgPaint)
        canvas.drawRoundRect(safetyRect, 20f, 20f, cardBorderPaint)

        val rating = LeanSafetyRating.evaluate(ride.safetyWarningPercentage, ride.safetyCriticalPercentage)
        val ratingColorHex = when (rating) {
            LeanSafetyRating.GOOD -> "#A3E635"
            LeanSafetyRating.CAUTION -> "#FFC107"
            LeanSafetyRating.HIGH -> "#FF3B30"
        }

        val safetyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(ratingColorHex)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("RIDE SAFETY RATING: ${rating.displayName}", IMAGE_WIDTH / 2f, 1175f, safetyTitlePaint)

        // 7. FOOTER (Y: 1250 to 1310)
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F2FE")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("LEAN — Take your corner", IMAGE_WIDTH / 2f, 1265f, footerPaint)

        val footerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 16f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        canvas.drawText("Tracked with LEAN Motorcycle Angle Analyzer", IMAGE_WIDTH / 2f, 1295f, footerSubPaint)

        return bitmap
    }

    private fun drawMetricCol(
        canvas: Canvas,
        label: String,
        valText: String,
        colorHex: String,
        centerX: Float,
        centerY: Float,
        textSizeSp: Float = 34f
    ) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(label, centerX, centerY - 20f, labelPaint)

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            textSize = textSizeSp
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(valText, centerX, centerY + 15f, valPaint)
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, rideId: Long): Uri? {
        return try {
            val shareDir = File(context.cacheDir, "share_images").apply {
                if (!exists()) mkdirs()
            }
            // Clean up old temporary share images in cache
            shareDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("ride_share_")) {
                    file.delete()
                }
            }

            val imageFile = File(shareDir, "ride_share_${rideId}_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to cache", e)
            null
        }
    }

    private fun formatDurationText(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh %dm", hours, minutes)
        } else {
            String.format(Locale.US, "%d min %ds", minutes, seconds)
        }
    }
}
