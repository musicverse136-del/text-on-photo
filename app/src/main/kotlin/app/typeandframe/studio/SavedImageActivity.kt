package app.typeandframe.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import app.typeandframe.studio.databinding.ActivitySavedImageBinding

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
        if (intent.getBooleanExtra(FROM_GALLERY, false)) {
            binding.editSaved.visibility = View.GONE
            binding.savedStatus.visibility = View.GONE
            binding.savedHeading.setText(R.string.studio_export_preview)
        }
        binding.editSaved.setOnClickListener { finish() }
        binding.finishSaved.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val FROM_GALLERY = "from_gallery"

        fun open(activity: Activity, image: Uri) {
            activity.startActivity(Intent(activity, SavedImageActivity::class.java)
                .setData(image)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(FROM_GALLERY, true))
        }
    }
}
