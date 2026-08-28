/**
 * AIBIMediaPipeline.kt
 * Ordered, privacy-preserving image preparation for AIBI tasks.
 */
package com.aibi.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

data class AIBIMediaAttachment(
    val data: ByteArray,
    val mimeType: String = "image/jpeg",
    val filename: String,
    val sourceIndex: Int,
    val role: String? = null
) {
    fun dataUrl(): String = "data:$mimeType;base64," + Base64.encodeToString(data, Base64.NO_WRAP)

    override fun equals(other: Any?): Boolean =
        other is AIBIMediaAttachment &&
            data.contentEquals(other.data) &&
            mimeType == other.mimeType &&
            filename == other.filename &&
            sourceIndex == other.sourceIndex &&
            role == other.role

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + sourceIndex
        result = 31 * result + (role?.hashCode() ?: 0)
        return result
    }
}

data class AIBIImageNormalizationPolicy(
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

class AIBIMediaPreparationException(message: String) : Exception(message)

object AIBIImageNormalizer {
    /**
     * Normalizes one image at a time. Original bytes are never mutated and decoded bitmaps are
     * released before the next source is opened, keeping an eight-photo task memory-bounded.
     */
    fun normalizeOrdered(
        sourceImages: List<ByteArray>,
        roles: List<String?> = emptyList(),
        policy: AIBIImageNormalizationPolicy = AIBIImageNormalizationPolicy()
    ): List<AIBIMediaAttachment> {
        if (sourceImages.size > policy.maximumImageCount) {
            throw AIBIMediaPreparationException("ATTACHMENT_LIMIT_EXCEEDED")
        }
        return sourceImages.mapIndexed { index, source ->
            val normalized = normalizeOne(source, policy)
            AIBIMediaAttachment(
                data = normalized,
                filename = "aibi-${(index + 1).toString().padStart(2, '0')}.jpg",
                sourceIndex = index,
                role = roles.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)?.take(64)
            )
        }
    }

    private fun normalizeOne(source: ByteArray, policy: AIBIImageNormalizationPolicy): ByteArray {
        if (source.isEmpty()) throw AIBIMediaPreparationException("EMPTY_IMAGE")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw AIBIMediaPreparationException("UNSUPPORTED_IMAGE")
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
        ) ?: throw AIBIMediaPreparationException("IMAGE_DECODE_FAILED")

        var bitmap = scaleToLongEdge(decoded, policy.maximumLongEdgePixels)
        if (bitmap !== decoded) decoded.recycle()
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
                throw AIBIMediaPreparationException("IMAGE_SIZE_TARGET_UNREACHABLE")
            }
            return encoded
        } finally {
            bitmap.recycle()
        }
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
            throw AIBIMediaPreparationException("IMAGE_ENCODE_FAILED")
        }
        return output.toByteArray()
    }
}
