package com.textswag.studio

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import com.textswag.studio.databinding.ActivitySavedImageBinding

class SavedImageActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val image = intent.data
        if (image == null) {
            finish()
            return
        }

        val binding = ActivitySavedImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        binding.savedPreview.setImageURI(image)
        binding.editSaved.setOnClickListener { finish() }
        binding.finishSaved.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        binding.shareSaved.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND)
                .setType(contentResolver.getType(image) ?: "image/png")
                .putExtra(Intent.EXTRA_STREAM, image)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share.clipData = ClipData.newUri(contentResolver, "Text Swag", image)
            startActivity(Intent.createChooser(share, getString(R.string.share_image)))
        }
    }
}
