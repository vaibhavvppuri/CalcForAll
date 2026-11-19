package com.vaibhav.calc.android

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PhotoSourceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun encoded(image: Bitmap, format: Bitmap.CompressFormat): File {
        val file = File.createTempFile("photo-source-check-", if (format == Bitmap.CompressFormat.JPEG) ".jpg" else ".png", context.cacheDir)
        file.outputStream().use { assertTrue(image.compress(format, 100, it)) }
        return file
    }

    @Test fun croppingReadsOriginalDetailAfterThePreviewAndSourceFileAreGone() {
        val original = Bitmap.createBitmap(4096, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(4096 * 128) { if (it % 4096 % 2 == 0) Color.BLACK else Color.WHITE }
        original.setPixels(pixels, 0, 4096, 0, 0, 4096, 128)
        val file = encoded(original, Bitmap.CompressFormat.PNG)
        original.recycle()
        try {
            val source = PhotoInput.open(context, Uri.fromFile(file))
            val preview = source.preview()
            assertTrue(preview.width <= 2048)
            preview.recycle()
            assertTrue(file.delete())
            val crop = source.crop(PhotoRegion(.75, 0.0, 1.0, 1.0))
            try {
                assertEquals(1024, crop.width)
                assertEquals(128, crop.height)
                assertEquals(Color.BLACK, crop.getPixel(0, 64))
                assertEquals(Color.WHITE, crop.getPixel(1, 64))
                assertEquals(Color.BLACK, crop.getPixel(512, 64))
            } finally { crop.recycle() }
        } finally { file.delete() }
    }

    @Test fun nativeRegionDecodeAndBitmapTransformsAgreeForAllExifOrientationsAndRotations() {
        val red = Color.RED; val green = Color.GREEN; val blue = Color.BLUE; val yellow = Color.YELLOW
        val corners = listOf(listOf(red, green, blue, yellow), listOf(green, red, yellow, blue),
            listOf(yellow, blue, green, red), listOf(blue, yellow, red, green),
            listOf(red, blue, green, yellow), listOf(blue, red, yellow, green),
            listOf(yellow, green, blue, red), listOf(green, yellow, red, blue))
        for (orientation in 1..8) {
            val original = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
            original.setPixels(IntArray(80 * 40) { index ->
                val x = index % 80; val y = index / 80
                if (y < 20) { if (x < 40) red else green } else { if (x < 40) blue else yellow }
            }, 0, 80, 0, 0, 80, 40)
            val file = encoded(original, Bitmap.CompressFormat.JPEG)
            original.recycle()
            try {
                ExifInterface(file.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val source = PhotoInput.open(context, Uri.fromFile(file))
                var expected = corners[orientation - 1]
                for (turns in 0..3) {
                    val crop = source.crop(PhotoRegion(0.0, 0.0, .5, .5), turns)
                    try {
                        val swap = (orientation >= 5) xor (turns % 2 != 0)
                        assertEquals(if (swap) 20 else 40, crop.width)
                        assertEquals(if (swap) 40 else 20, crop.height)
                        val actual = crop.getPixel(crop.width / 2, crop.height / 2)
                        val wanted = expected[0]
                        assertTrue("EXIF $orientation, turns $turns: $actual should match $wanted",
                            abs(Color.red(actual) - Color.red(wanted)) + abs(Color.green(actual) - Color.green(wanted)) +
                                abs(Color.blue(actual) - Color.blue(wanted)) <= 60)
                    } finally { crop.recycle() }
                    expected = listOf(expected[2], expected[0], expected[3], expected[1])
                }
            } finally { file.delete() }
        }
    }

    @Test fun anOddSourceEdgeCannotDecodePastThe2048PixelLimit() {
        val original = Bitmap.createBitmap(4097, 20, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        val file = encoded(original, Bitmap.CompressFormat.PNG)
        original.recycle()
        try {
            val preview = PhotoInput.open(context, Uri.fromFile(file)).preview()
            try {
                assertTrue(preview.width in 1..2048)
                assertTrue(preview.height in 1..2048)
                assertEquals(Color.WHITE, preview.getPixel(preview.width / 2, preview.height / 2))
            } finally { preview.recycle() }
        } finally { file.delete() }
    }
}
