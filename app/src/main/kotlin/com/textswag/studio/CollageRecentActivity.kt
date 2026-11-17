package com.textswag.studio

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.textswag.studio.databinding.ActivityCollageRecentBinding
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CollageRecentActivity : Activity() {
    private lateinit var binding: ActivityCollageRecentBinding
    private val media: ArrayList<Uri> = ArrayList<Uri>()
    private val selected: ArrayList<Uri> = ArrayList<Uri>()
    private val thumbnails: LruCache<Uri?, Bitmap?> = LruCache<Uri?, Bitmap?>(80)
    private val worker: ExecutorService = Executors.newFixedThreadPool(2)
    private lateinit var grid: GridView
    private lateinit var adapter: BaseAdapter
    private lateinit var strip: LinearLayout
    private lateinit var assets: DesignAssets

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityCollageRecentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        assets = DesignAssets(this)
        grid = binding.recentGrid
        strip = binding.recentSelected
        if (state != null) {
            val restored: ArrayList<Uri>? =
                state.getParcelableArrayList<Uri>(CollageRecentActivity.PHOTOS)
            if (restored != null) selected.addAll(restored)
        }
        binding.recentBack.setOnClickListener(View.OnClickListener { view: View -> finish() })
        binding.recentBrowse.setOnClickListener(View.OnClickListener { view: View ->
            CollagePicker.open(
                this,
                CollageRecentActivity.BROWSE
            )
        })
        binding.recentStart.setOnClickListener(View.OnClickListener { view: View ->
            if (selected.isEmpty()) return@OnClickListener
            val edit: Intent = Intent(this, CollageActivity::class.java)
                .putParcelableArrayListExtra(CollageRecentActivity.PHOTOS, ArrayList<Uri>(selected))
                .putExtra(
                    CollageRecentActivity.REPLACE,
                    intent.getBooleanExtra(CollageRecentActivity.REPLACE, false)
                )
            startActivityForResult(edit, CollageRecentActivity.EDIT)

        })
        adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return media.size
            }

            override fun getItem(position: Int): Any? {
                return media.get(position)
            }

            override fun getItemId(position: Int): Long {
                return position.toLong()
            }

            override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
                val uri: Uri = media.get(position)
                val tile: FrameLayout =
                    if (recycled == null) FrameLayout(this@CollageRecentActivity) else recycled as FrameLayout
                tile.removeAllViews()
                tile.setLayoutParams(
                    AbsListView.LayoutParams(
                        -1,
                        kotlin.math.max(dp(70), grid.getWidth() / 4)
                    )
                )
                val preview: ImageView = thumbnail(uri)
                tile.addView(preview, FrameLayout.LayoutParams(-1, -1))
                val order: Int = selected.indexOf(uri)
                if (order >= 0) {
                    val badge: TextView = TextView(this@CollageRecentActivity)
                    badge.setText((order + 1).toString())
                    badge.setGravity(Gravity.CENTER)
                    badge.setTextColor(android.graphics.Color.WHITE)
                    badge.setBackgroundColor(-0xbe6260)
                    val params: FrameLayout.LayoutParams =
                        FrameLayout.LayoutParams(dp(25), dp(25), Gravity.TOP or Gravity.END)
                    tile.addView(badge, params)
                }
                tile.setContentDescription("Photo " + (position + 1))
                tile.setOnClickListener(View.OnClickListener { view: View -> toggle(uri) })
                return tile
            }
        }
        grid.setAdapter(adapter)
        updateSelection()
        if (canReadMedia()) loadMedia()
        else if (state == null) requestPermissions(
            if (Build.VERSION.SDK_INT >= 34)
                kotlin.arrayOf<String>(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            else
                arrayOf(mediaPermission()), CollageRecentActivity.MEDIA_PERMISSION
        )
    }

    private fun mediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun canReadMedia(): Boolean {
        return checkSelfPermission(mediaPermission()) == PackageManager.PERMISSION_GRANTED
                || (Build.VERSION.SDK_INT >= 34 && (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != CollageRecentActivity.MEDIA_PERMISSION) return
        if (canReadMedia()) loadMedia()
        else CollagePicker.open(this, CollageRecentActivity.BROWSE)
    }

    private fun loadMedia() {
        worker.execute(Runnable {
            val photos: ArrayList<Uri> = ArrayList<Uri>()
            val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            try {
                contentResolver.query(
                    collection, kotlin.arrayOf<String>(MediaStore.Images.Media._ID),
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC"
                ).use { cursor ->
                    while (cursor != null && cursor.moveToNext()) photos.add(
                        ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(0)
                        )
                    )
                }
            } catch (ignored: java.lang.SecurityException) {
            }
            runOnUiThread(Runnable {
                if (isDestroyed()) return@Runnable
                media.clear()
                media.addAll(photos)
                adapter.notifyDataSetChanged()
                if (media.isEmpty()) CollagePicker.open(this, CollageRecentActivity.BROWSE)

            })

        })
    }

    private fun thumbnail(uri: Uri): ImageView {
        val view: ImageView = ImageView(this)
        view.setScaleType(ImageView.ScaleType.CENTER_CROP)
        view.setBackgroundColor(-0x19191a)
        val cached: Bitmap? = thumbnails.get(uri)
        if (cached != null) view.setImageBitmap(cached)
        else worker.execute(Runnable {
            try {
                val image: Bitmap? =
                    if (Build.VERSION.SDK_INT >= 29 && "media" == uri.getAuthority())
                        contentResolver.loadThumbnail(uri, android.util.Size(280, 280), null)
                    else
                        assets.photo(uri, 280)
                thumbnails.put(uri, image)
                runOnUiThread(Runnable { if (!isDestroyed()) view.setImageBitmap(image) })
            } catch (ignored: IOException) {
            } catch (ignored: java.lang.SecurityException) {
            }
        })
        return view
    }

    private fun toggle(uri: Uri?) {
        if (!selected.remove(uri)) {
            if (selected.size >= 9) {
                Toast.makeText(this, "You can select up to 9 photos.", Toast.LENGTH_SHORT).show()
                return
            }
            selected.add(requireNotNull(uri))
        }
        updateSelection()
    }

    private fun updateSelection() {
        binding.recentCount.setText("Select 1–9 photos (" + selected.size + ")")
        binding.recentStart.setEnabled(!selected.isEmpty())
        strip.removeAllViews()
        for (uri in selected) {
            val tile: ImageView = thumbnail(uri)
            tile.setContentDescription("Remove selected photo " + (selected.indexOf(uri) + 1))
            val size: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(61), dp(61))
            size.setMargins(dp(3), 0, dp(3), 0)
            strip.addView(tile, size)
            tile.setOnClickListener(View.OnClickListener { view: View -> toggle(uri) })
        }
        if (adapter != null) adapter.notifyDataSetChanged()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putParcelableArrayList(CollageRecentActivity.PHOTOS, selected)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (result != Activity.RESULT_OK) return
        if (request == CollageRecentActivity.EDIT) {
            setResult(result, data)
            finish()
        } else if (request == CollageRecentActivity.BROWSE && data != null) {
            val photos: ArrayList<Uri> = CollagePicker.photos(data)
            CollagePicker.keepAccess(this, photos)
            for (uri in photos) if (selected.size < 9 && !selected.contains(uri)) selected.add(
                requireNotNull(uri)
            )
            updateSelection()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(size: Int): Int {
        return java.lang.Math.round(getResources().getDisplayMetrics().density * size)
    }

    companion object {
        const val REPLACE: String = "collageReplace"
        const val PHOTOS: String = "collageSources"
        private const val BROWSE: Int = 1
        private const val EDIT: Int = 2
        private const val MEDIA_PERMISSION: Int = 3
    }
}
