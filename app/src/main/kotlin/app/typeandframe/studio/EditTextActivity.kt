package app.typeandframe.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.typeandframe.studio.databinding.ActivityEditTextBinding
import app.typeandframe.studio.databinding.ItemSuggestedQuoteBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.LinkedHashMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class EditTextActivity : Activity() {
    private lateinit var binding: ActivityEditTextBinding
    private val categories: MutableMap<String, MutableList<String>> =
        LinkedHashMap<String, MutableList<String>>()
    private lateinit var tabs: LinearLayout
    private lateinit var quotes: ListView
    private lateinit var input: EditText
    private var selectedCategory: String? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityEditTextBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MainActivity.applyInsets(binding.root)
        input = binding.quoteTextInput
        quotes = binding.quoteList
        tabs = binding.quoteTabs
        if (state == null) input.setText(intent.getStringExtra(EditTextActivity.TEXT))
        binding.close.setOnClickListener(View.OnClickListener { view: View -> finish() })
        binding.clearText.setOnClickListener(View.OnClickListener { view: View -> input.setText("") })
        binding.confirmText.setOnClickListener(View.OnClickListener { view: View -> submitText() })

        loadQuotes()
        if (!categories.isEmpty()) {
            initTabs()
            val category: String? = if (state == null) null else state.getString("category")
            selectCategory(
                if (category != null && categories.containsKey(category)) category else categories.keys.iterator()
                    .next()
            )
        }
    }

    private fun loadQuotes() {
        try {
            BufferedReader(
                InputStreamReader(
                    getAssets().open("quotes/data.json"), StandardCharsets.UTF_8
                )
            ).use { reader ->
                val file: java.lang.StringBuilder = java.lang.StringBuilder()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) file.append(line)
                    .append('\n')
                val entries: JSONArray = JSONObject(file.toString()).getJSONArray("data")
                for (i in 0..<entries.length()) {
                    val quote: JSONObject = entries.getJSONObject(i)
                    val group: String = quote.getString("category")
                    categories.getOrPut(group) { ArrayList() }.add(quote.getString("content"))
                }

            }
        } catch (e: IOException) {
            android.util.Log.w("EditTextActivity", "Could not load bundled quotes", e)
        } catch (e: JSONException) {
            android.util.Log.w("EditTextActivity", "Could not load bundled quotes", e)
        }
    }

    private fun initTabs() {
        var face: Typeface? = Typeface.create("sans-serif-light", Typeface.NORMAL)
        if (android.os.Build.VERSION.SDK_INT >= 26) face =
            getResources().getFont(R.font.font_default)
        for (category in categories.keys) {
            val tab: LinearLayout = LinearLayout(this)
            tab.setOrientation(LinearLayout.VERTICAL)
            tab.setTag(category)
            tab.setBackgroundResource(android.R.drawable.list_selector_background)
            val title: TextView = TextView(this)
            title.setGravity(Gravity.CENTER)
            title.setPadding(dp(20), 0, dp(20), 0)
            title.setText(category)
            title.setTextSize(16f)
            title.setTypeface(face)
            tab.addView(title, LinearLayout.LayoutParams(-1, 0, 1f))
            tab.addView(View(this), LinearLayout.LayoutParams(-1, dp(1)))
            tab.setOnClickListener(View.OnClickListener { view: View -> selectCategory(category) })
            tabs.addView(tab, LinearLayout.LayoutParams(-2, -1))
        }
    }

    private fun selectCategory(category: String) {
        selectedCategory = category
        for (i in 0..<tabs.getChildCount()) {
            val tab: LinearLayout = tabs.getChildAt(i) as LinearLayout
            val active: Boolean = category == tab.getTag()
            (tab.getChildAt(0) as TextView).setTextColor(if (active) getColor(R.color.foreground) else -0x6f6f70)
            tab.getChildAt(1).setBackgroundColor(if (active) getColor(R.color.foreground) else 0)
            tab.setSelected(active)
        }
        quotes.setAdapter(QuoteAdapter(categories.getValue(category)))
        quotes.setSelection(0)
    }

    private fun submitText() {
        val value: String = input.getText().toString().trim { it <= ' ' }
        if (value.isEmpty()) {
            input.setError(getString(R.string.empty_text_error))
            return
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(EditTextActivity.TEXT, value))
        finish()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("category", selectedCategory)
    }

    private fun dp(value: Int): Int {
        return java.lang.Math.round(value * getResources().getDisplayMetrics().density)
    }

    private inner class QuoteAdapter(items: MutableList<String>) : BaseAdapter() {
        private val items: MutableList<String>

        init {
            this.items = items
        }

        override fun getCount(): Int {
            return items.size
        }

        override fun getItem(position: Int): String? {
            return items.get(position)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val rowBinding: ItemSuggestedQuoteBinding = if (recycled == null)
                ItemSuggestedQuoteBinding.inflate(layoutInflater, parent, false)
            else
                ItemSuggestedQuoteBinding.bind(recycled)
            val row: View = rowBinding.root
            val quote: String? = getItem(position)
            val content: TextView = rowBinding.quoteContent
            content.setText(quote)
            rowBinding.copyQuote.setOnClickListener(View.OnClickListener { view: View ->
                input.setText(quote)
                input.setSelection(input.length())
                getSystemService<InputMethodManager?>(InputMethodManager::class.java).hideSoftInputFromWindow(
                    input.getWindowToken(),
                    0
                )

            })
            return row
        }
    }

    companion object {
        const val TEXT: String = "text"
    }
}
