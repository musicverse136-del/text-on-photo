package com.textswag.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.textswag.studio.databinding.ActivityMainBinding

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.root)
        binding.create.setOnClickListener { openEditor(EditorActivity.MODE_BACKGROUND) }
        binding.openPhoto.setOnClickListener { openEditor(EditorActivity.MODE_PHOTO) }
        binding.collage.setOnClickListener {
            startActivityForResult(Intent(this, CollageRecentActivity::class.java), COLLAGE)
        }
        binding.classic.setOnClickListener { openEditor(EditorActivity.MODE_BLANK) }
        binding.aiStudio.setOnClickListener {
            showInfo(R.string.ai_studio, "The original AI service is unavailable in this build. Use Create to design locally.")
        }
        binding.more.setOnClickListener {
            showInfo(R.string.more, "Exported designs appear in Pictures → Text Swag.")
        }
        binding.tutorials.setOnClickListener {
            showInfo(R.string.tutorials, "Tap Create and choose a background. Add text or stickers, drag layers to position them, then tap Done to save.")
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == COLLAGE && result == RESULT_OK) {
            val image = data?.data ?: return
            startActivity(Intent(this, EditorActivity::class.java).setData(image)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    private fun openEditor(mode: Int) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.ENTRY_MODE, mode))
    }

    private fun showInfo(title: Int, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    companion object {
        private const val COLLAGE = 1

        @JvmStatic
        fun applyInsets(view: View) {
            view.setOnApplyWindowInsetsListener { root, insets ->
                root.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets.consumeSystemWindowInsets()
            }
            view.requestApplyInsets()
        }
    }
}
