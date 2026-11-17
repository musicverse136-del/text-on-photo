package com.textswag.studio

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

internal object CollagePicker {
    private const val MAX_PHOTOS = 9

    @JvmStatic
    fun open(activity: Activity, request: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val picker = Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
                    .putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, minOf(MAX_PHOTOS, MediaStore.getPickImagesMaxLimit()))
                activity.startActivityForResult(picker, request)
                return
            } catch (_: ActivityNotFoundException) {
                // Some devices provide only DocumentsUI.
            }
        }

        Toast.makeText(activity, "Touch and hold the first photo to select multiple photos.", Toast.LENGTH_LONG).show()
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        activity.startActivityForResult(picker, request)
    }

    @JvmStatic
    fun photos(data: Intent): ArrayList<Uri> {
        val uris = linkedSetOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                if (uris.size == MAX_PHOTOS) break
                clip.getItemAt(index).uri?.let(uris::add)
            }
        }
        if (uris.size < MAX_PHOTOS) data.data?.let(uris::add)
        return ArrayList(uris)
    }

    @JvmStatic
    fun keepAccess(activity: Activity, photos: ArrayList<Uri>) {
        for (uri in photos) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The provider can grant temporary access only.
            }
        }
    }
}
