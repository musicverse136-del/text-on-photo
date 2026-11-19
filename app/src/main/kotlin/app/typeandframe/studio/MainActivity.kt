package app.typeandframe.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.typeandframe.studio.databinding.ActivityMainBinding

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
        binding.more.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(this, ExportGalleryActivity::class.java))
            } else {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), OPEN_EXPORT)
            }
        }
        binding.tutorials.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.studio_help)
                .setMessage(R.string.studio_help_body)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == OPEN_EXPORT && result == RESULT_OK) {
            data?.data?.let { SavedImageActivity.open(this, it) }
        }
        if (request == COLLAGE && result == RESULT_OK) {
            val image = data?.data ?: return
            startActivity(Intent(this, EditorActivity::class.java).setData(image)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    private fun openEditor(mode: Int) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.ENTRY_MODE, mode))
    }

    companion object {
        private const val COLLAGE = 1
        private const val OPEN_EXPORT = 2

        @JvmStatic
        fun applyInsets(view: View) {
            val left = view.paddingLeft
            val top = view.paddingTop
            val right = view.paddingRight
            val bottom = view.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
                val system = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
                root.setPadding(left + system.left, top + system.top,
                    right + system.right, bottom + system.bottom)
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.requestApplyInsets(view)
        }
    }
}
