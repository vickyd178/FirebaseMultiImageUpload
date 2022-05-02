package com.avion.multiimageupload.utils

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.avion.multiimageupload.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class Constants {
    companion object {
        var IMAGE_PERMISSIONS =
            if (Build.VERSION.SDK_INT > 28)
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            else
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )

        fun createUriToTempFile(context: Context, uri: Uri): File? {
            context.contentResolver.openInputStream(uri)?.let { inputStream ->
                val tempFile: File = createImageFile(context)
                val fileOutputStream = FileOutputStream(tempFile)

                inputStream.copyTo(fileOutputStream)
                inputStream.close()
                return tempFile;
            }
            return null
        }

        fun createImageFile(context: Context): File {
            val timeStamp = SimpleDateFormat.getDateTimeInstance().format(Date())
            val storageDir = context.cacheDir

            return File.createTempFile(
                "IMAGE_${timeStamp}_",
                ".jpg",
                storageDir
            )
        }


         fun getCompressedImage(context: Context, fullSizeBitmap: Bitmap): File =
            getCompressedBitmapFile(context, fullSizeBitmap)


        fun getCompressedBitmapFile(context: Context, fullSizeBitmap: Bitmap): File {

            val file = File.createTempFile(
                "IMAGE",
                ".jpg",
                context.cacheDir
            )

            val MAX_IMAGE_SIZE = 500 * 1024
            var streamLength = MAX_IMAGE_SIZE
            var compressQuality = 110

            val bos = ByteArrayOutputStream()


            while (streamLength >= MAX_IMAGE_SIZE && compressQuality > 10) {
                bos.use {
                    it.flush() //to avoid out of memory error
                    it.reset()
                }

                compressQuality -= 10
                fullSizeBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, bos)
                val bmpPicByteArray: ByteArray = bos.toByteArray()
                streamLength = bmpPicByteArray.size

                if (BuildConfig.DEBUG) {
                    println("image quality: " + compressQuality)
                    println("image Size: " + streamLength)
                }
            }

            val byteArray = bos.toByteArray();
            val fos = FileOutputStream(file)
            fos.write(byteArray)
            fos.flush()
            fos.close()

            return file
        }
    }

}