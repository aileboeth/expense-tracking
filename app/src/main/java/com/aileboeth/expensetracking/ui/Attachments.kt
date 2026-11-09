package com.aileboeth.expensetracking.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Attachment
import com.aileboeth.expensetracking.data.Repo
import java.io.File
import java.util.UUID

/**
 * Receipt photos and invoice files. Picked files are copied into the app's own storage
 * (filesDir/attachments), so they stay even if the original is deleted; nothing is uploaded.
 * Photos are turned upright and shrunk to at most [MAX_SIDE] px, which keeps a receipt readable
 * at a fraction of the camera's size.
 */
object Attachments {
    private const val MAX_SIDE = 2560
    private val thumbs = LruCache<String, ImageBitmap>(40)

    private fun authority(ctx: Context) = ctx.packageName + ".files"

    /** Copies [uri] in. The result has txId 0: the editor links it when the entry is saved. */
    fun import(ctx: Context, uri: Uri): Attachment? = runCatching {
        Repo.init(ctx)
        val cr = ctx.contentResolver
        val name = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: ctx.getString(R.string.att_default_name)
        val mime = cr.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        if (mime.startsWith("image/")) {
            shrinkImage(ctx, uri)?.let { file -> return@runCatching Attachment(name = name, mime = "image/jpeg", file = file.name, size = file.length()) }
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: name.substringAfterLast('.', "bin")
        val out = File(Repo.attachDir, "${UUID.randomUUID()}.$ext")
        cr.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        Attachment(name = name, mime = mime, file = out.name, size = out.length())
    }.getOrNull()

    /** A file the camera app writes into, and the content:// uri to hand it. */
    fun cameraTarget(ctx: Context): Pair<File, Uri> {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, authority(ctx), f)
    }

    /** After the camera returned: shrink the photo into the attachment folder. */
    fun importCamera(ctx: Context, photo: File): Attachment? {
        if (!photo.exists() || photo.length() == 0L) return null
        val a = import(ctx, Uri.fromFile(photo))?.copy(name = ctx.getString(R.string.att_photo_name, java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())))
        photo.delete()
        return a
    }

    /** Deletes files of attachments that were added in the editor but never saved. */
    fun discard(list: List<Attachment>) = list.filter { it.id == 0L }.forEach { File(Repo.attachDir, it.file).delete() }

    fun open(ctx: Context, a: Attachment) {
        val f = File(Repo.attachDir, a.file)
        val uri = FileProvider.getUriForFile(ctx, authority(ctx), f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, ctx.getString(R.string.att_no_viewer), Toast.LENGTH_SHORT).show()
        }
    }

    /** Small preview of an image attachment (cached). */
    fun thumbnail(a: Attachment, px: Int): ImageBitmap? {
        val key = a.file + "@" + px
        thumbs.get(key)?.let { return it }
        val f = File(Repo.attachDir, a.file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bmp.asImageBitmap().also { thumbs.put(key, it) }
    }

    private fun shrinkImage(ctx: Context, uri: Uri): File? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        }
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        val out = File(Repo.attachDir, "${UUID.randomUUID()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return out
    }
}
