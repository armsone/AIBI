package com.armsone.imanagerai.ui.externalai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class ExternalAIImageNormalizationPolicy(
    val maximumImageCount: Int = 8,
    val maximumLongEdgePixels: Int = 2_048,
    val maximumBytesPerImage: Int = 2_000_000,
    val initialJpegQuality: Int = 84,
    val minimumJpegQuality: Int = 50
) {
    init {
        require(maximumImageCount in 1..8)
        require(maximumLongEdgePixels >= 512)
        require(maximumBytesPerImage >= 128_000)
        require(initialJpegQuality in 1..100)
        require(minimumJpegQuality in 1..initialJpegQuality)
    }
}

class ExternalAIMediaPreparationException(message: String) : Exception(message)

/** 선택된 이미지를 순서대로, 한 장씩만 디코딩해 AIBI 전송 규격으로 정규화한다. */
object ExternalAIImageNormalizer {
    fun normalizeOrdered(
        sourceImages: List<ByteArray>,
        policy: ExternalAIImageNormalizationPolicy = ExternalAIImageNormalizationPolicy()
    ): List<ExternalAIAttachment> {
        if (sourceImages.size > policy.maximumImageCount) {
            throw ExternalAIMediaPreparationException("ATTACHMENT_LIMIT_EXCEEDED")
        }
        return sourceImages.mapIndexed { index, source ->
            ExternalAIAttachment(
                data = normalizeOne(source, policy),
                filename = "aibi-${(index + 1).toString().padStart(2, '0')}.jpg",
                sourceIndex = index
            )
        }
    }

    private fun normalizeOne(source: ByteArray, policy: ExternalAIImageNormalizationPolicy): ByteArray {
        if (source.isEmpty()) throw ExternalAIMediaPreparationException("EMPTY_IMAGE")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ExternalAIMediaPreparationException("UNSUPPORTED_IMAGE")
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= policy.maximumLongEdgePixels) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw ExternalAIMediaPreparationException("IMAGE_DECODE_FAILED")

        var bitmap = applyExifOrientation(decoded, source)
        if (bitmap !== decoded) decoded.recycle()
        val scaled = scaleToLongEdge(bitmap, policy.maximumLongEdgePixels)
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled

        try {
            var quality = policy.initialJpegQuality
            var encoded = encodeJpeg(bitmap, quality)
            while (encoded.size > policy.maximumBytesPerImage && quality > policy.minimumJpegQuality) {
                quality = maxOf(policy.minimumJpegQuality, quality - 7)
                encoded = encodeJpeg(bitmap, quality)
            }
            while (encoded.size > policy.maximumBytesPerImage && maxOf(bitmap.width, bitmap.height) > 640) {
                val smaller = Bitmap.createScaledBitmap(
                    bitmap,
                    maxOf(1, (bitmap.width * 0.85f).toInt()),
                    maxOf(1, (bitmap.height * 0.85f).toInt()),
                    true
                )
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
                encoded = encodeJpeg(bitmap, quality)
            }
            if (encoded.size > policy.maximumBytesPerImage) {
                throw ExternalAIMediaPreparationException("IMAGE_SIZE_TARGET_UNREACHABLE")
            }
            return encoded
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, source: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToLongEdge(bitmap: Bitmap, maximumLongEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maximumLongEdge) return bitmap
        val ratio = maximumLongEdge.toFloat() / longEdge.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * ratio).toInt()),
            maxOf(1, (bitmap.height * ratio).toInt()),
            true
        )
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw ExternalAIMediaPreparationException("IMAGE_ENCODE_FAILED")
        }
        return output.toByteArray()
    }
}
