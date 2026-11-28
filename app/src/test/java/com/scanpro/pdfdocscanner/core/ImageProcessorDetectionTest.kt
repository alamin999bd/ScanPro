package com.scanpro.pdfdocscanner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageProcessorDetectionTest {

    private val context: Context
        get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test
    fun detectsHighContrastPageBoundsAndFourCorners() = runBlocking {
        val bitmap = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(24, 24, 24))
            canvas.drawRect(
                44f, 36f, 276f, 364f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE },
            )

            val processor = ImageProcessor(PdfEngine(context))
            val bounds = processor.detectDocument(bitmap)
            assertNotNull("A clear high-contrast page should be detected", bounds)
            val rect = requireNotNull(bounds)
            assertTrue(rect.left in 0.08f..0.18f)
            assertTrue(rect.top in 0.04f..0.14f)
            assertTrue(rect.right in 0.82f..0.92f)
            assertTrue(rect.bottom in 0.86f..0.96f)

            val quad = processor.detectQuad(bitmap)
            assertNotNull("The page's four corners should be detected", quad)
            assertTrue(PageQuadGeometry.isValid(quad, minArea = 0.12f, minEdge = 0.1f))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun rejectsUniformImageInsteadOfTreatingTheWholePhotoAsAPage() = runBlocking {
        val bitmap = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            val processor = ImageProcessor(PdfEngine(context))
            assertNull(processor.detectDocument(bitmap))
            assertNull(processor.detectQuad(bitmap))
        } finally {
            bitmap.recycle()
        }
    }
}