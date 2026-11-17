package com.textswag.studio

import android.content.Context
import android.graphics.RectF
import android.util.Xml
import java.io.IOException
import java.util.ArrayList
import java.util.Comparator
import java.util.HashSet
import java.util.function.ToIntFunction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

internal object CollageTemplates {
    private val READING_ORDER: Comparator<android.graphics.Path> =
        Comparator.comparingInt<android.graphics.Path>(ToIntFunction { path: android.graphics.Path ->
            java.lang.Math.round(CollageTemplates.bounds(path).top * 10000)
        })
            .thenComparingInt(ToIntFunction { path: android.graphics.Path ->
                java.lang.Math.round(
                    CollageTemplates.bounds(path).left * 10000
                )
            })

    fun load(context: Context, count: Int): MutableList<MutableList<android.graphics.Path>> {
        val templates: MutableList<MutableList<android.graphics.Path>> =
            ArrayList<MutableList<android.graphics.Path>>()
        try {
            val json: JSONArray =
                JSONArray(DesignAssets(context).text("json/collage_" + count + ".json"))
            for (i in 0..<json.length()) {
                try {
                    val layout: JSONObject = json.getJSONObject(i)
                    val pieces: MutableList<android.graphics.Path> =
                        if (layout.getString("type") == "MaskLayout.Info")
                            CollageTemplates.masks(context, layout.getJSONArray("areaInfo"))
                        else
                            CollageTemplates.split(layout)
                    if (pieces.size == count) templates.add(pieces)
                } catch (ignored: JSONException) {
                    // Skip a malformed template while keeping the rest available.
                } catch (ignored: java.lang.IllegalArgumentException) {
                }
            }
        } catch (ignored: IOException) {
            // The regular grid is also available without template assets.
        } catch (ignored: JSONException) {
        }
        if (templates.isEmpty()) {
            val columns: Int = if (count > 4) 3 else 2
            val rows: Int = (count + columns - 1) / columns
            val grid: MutableList<android.graphics.Path> = ArrayList<android.graphics.Path>()
            for (row in 0..<rows) {
                val cells: Int = kotlin.math.min(columns, count - row * columns)
                for (column in 0..<cells) {
                    grid.add(
                        CollageTemplates.rect(
                            column / cells.toFloat(), row / rows.toFloat(),
                            (column + 1f) / cells, (row + 1f) / rows
                        )
                    )
                }
            }
            templates.add(grid)
        }
        return templates
    }

    @Throws(JSONException::class)
    private fun split(layout: JSONObject): MutableList<android.graphics.Path> {
        val pieces: MutableList<android.graphics.Path> = ArrayList<android.graphics.Path>()
        pieces.add(CollageTemplates.rect(0f, 0f, 1f, 1f))
        val operations: JSONArray = layout.getJSONArray("areaInfo")
        for (i in 0..<operations.length()) {
            val line: JSONObject = operations.getJSONObject(i)
            val position: Int = line.optInt("areaPosition")
            kotlin.require(!(position < 0 || position >= pieces.size)) { "Invalid collage area" }
            val piece: android.graphics.Path = pieces.removeAt(position)
            val box: RectF = CollageTemplates.bounds(piece)
            val vertical: Boolean = line.optString("direction") == "VERTICAL"
            val f: Float = line.optDouble("f", .5).toFloat()
            val f2: Float = line.optDouble("f2", f.toDouble()).toFloat()
            val type: String = line.getString("type")
            if (type.endsWith("CrossLineInfo")) {
                CollageTemplates.partition(pieces, piece, false, f, f)
                val top: android.graphics.Path = pieces.removeAt(pieces.size - 2)
                val bottom: android.graphics.Path = pieces.removeAt(pieces.size - 1)
                CollageTemplates.partition(pieces, top, true, f2, f2)
                CollageTemplates.partition(pieces, bottom, true, f2, f2)
            } else if (type.endsWith("CutAreaEqualDirectionPartLineInfo")) {
                val count: Int = kotlin.math.max(1, line.optInt("spanCount", 2))
                if (vertical) CollageTemplates.grid(pieces, piece, box, count, 1)
                else CollageTemplates.grid(pieces, piece, box, 1, count)
            } else if (type.endsWith("CutAreaEqualPartLineInfo")) {
                CollageTemplates.grid(
                    pieces,
                    piece,
                    box,
                    line.optInt("v", 0) + 1,
                    line.optInt("spanCount", 0) + 1
                )
            } else {
                CollageTemplates.partition(
                    pieces,
                    piece,
                    vertical,
                    f,
                    if (type.endsWith("SlantLineInfo")) f2 else f
                )
            }
            pieces.sortWith(READING_ORDER)
        }
        val disabled: JSONArray? = layout.optJSONArray("disableAreas")
        if (disabled != null) {
            val omitted: MutableSet<Int?> = HashSet<Int?>()
            for (i in 0..<disabled.length()) omitted.add(disabled.getInt(i))
            for (i in pieces.indices.reversed()) if (omitted.contains(i)) pieces.removeAt(i)
        }
        return pieces
    }

    private fun partition(
        output: MutableList<android.graphics.Path>,
        piece: android.graphics.Path,
        vertical: Boolean,
        f: Float,
        f2: Float
    ) {
        val box: RectF = CollageTemplates.bounds(piece)
        val x: Float = box.left + box.width() * f
        val x2: Float = box.left + box.width() * f2
        val y: Float = box.top + box.height() * f
        val y2: Float = box.top + box.height() * f2
        val first: android.graphics.Path = if (vertical)
            CollageTemplates.polygon(
                box.left,
                box.top,
                x,
                box.top,
                x2,
                box.bottom,
                box.left,
                box.bottom
            )
        else
            CollageTemplates.polygon(
                box.left,
                box.top,
                box.right,
                box.top,
                box.right,
                y2,
                box.left,
                y
            )
        val second: android.graphics.Path = android.graphics.Path(piece)
        first.op(piece, android.graphics.Path.Op.INTERSECT)
        second.op(first, android.graphics.Path.Op.DIFFERENCE)
        output.add(first)
        output.add(second)
    }

    private fun grid(
        output: MutableList<android.graphics.Path>,
        piece: android.graphics.Path,
        box: RectF,
        columns: Int,
        rows: Int
    ) {
        val width: Float = box.width() / columns
        val height: Float = box.height() / rows
        for (row in 0..<rows) {
            for (col in 0..<columns) {
                val left: Float = box.left + col * width
                val top: Float = box.top + row * height
                val cell: android.graphics.Path =
                    CollageTemplates.rect(left, top, left + width, top + height)
                cell.op(piece, android.graphics.Path.Op.INTERSECT)
                output.add(cell)
            }
        }
    }

    @Throws(JSONException::class)
    private fun masks(context: Context, areas: JSONArray): MutableList<android.graphics.Path> {
        val pieces: MutableList<android.graphics.Path> = ArrayList<android.graphics.Path>()
        for (i in 0..<areas.length()) {
            val area: JSONObject = areas.getJSONObject(i)
            val points: JSONArray = area.getJSONArray("points")
            val coordinates: FloatArray = FloatArray(points.length() * 2)
            for (point in 0..<points.length()) {
                val p: JSONObject = points.getJSONObject(point)
                coordinates[2 * point] = p.getDouble("x").toFloat()
                coordinates[2 * point + 1] = p.getDouble("y").toFloat()
            }
            val outline: android.graphics.Path = CollageTemplates.polygon(*coordinates)
            val source: String = area.optString("maskPath")
            pieces.add(
                if (source.isEmpty()) outline else CollageTemplates.svg(
                    context,
                    source,
                    outline
                )
            )
        }
        return pieces
    }

    private fun svg(
        context: Context,
        source: String,
        outline: android.graphics.Path
    ): android.graphics.Path {
        try {
            context.getAssets().open(source).use { input ->
                val xml: XmlPullParser = Xml.newPullParser()
                xml.setInput(input, null)
                val mask: android.graphics.Path = android.graphics.Path()
                var viewport: RectF? = null
                var event: Int = xml.getEventType()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event != XmlPullParser.START_TAG) {
                        event = xml.next()
                        continue
                    }
                    if ("svg" == xml.getName()) {
                        val viewBox: String? = xml.getAttributeValue(null, "viewBox")
                        if (viewBox != null) {
                            val values: Array<String> =
                                viewBox.trim { it <= ' ' }.split("[, ]+".toRegex())
                                    .dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (values.size == 4) {
                                val left: Float = values[0].toFloat()
                                val top: Float = values[1].toFloat()
                                viewport = RectF(
                                    left, top, left + values[2].toFloat(),
                                    top + values[3].toFloat()
                                )
                            }
                        }
                    } else if ("path" == xml.getName()) {
                        val data: String? = xml.getAttributeValue(null, "d")
                        if (data != null) mask.addPath(
                            androidx.core.graphics.PathParser.createPathFromPathData(
                                data
                            )
                        )
                    } else if ("polygon" == xml.getName()) {
                        val points: String? = xml.getAttributeValue(null, "points")
                        if (points != null) {
                            val values: Array<String> =
                                points.trim { it <= ' ' }.split("[,\\s]+".toRegex())
                                    .dropLastWhile { it.isEmpty() }.toTypedArray()
                            val polygon: FloatArray = FloatArray(values.size)
                            for (i in values.indices) polygon[i] = values[i].toFloat()
                            mask.addPath(CollageTemplates.polygon(*polygon))
                        }
                    }
                    event = xml.next()
                }
                if (mask.isEmpty()) return outline
                if (viewport == null) viewport = CollageTemplates.bounds(mask)
                val matrix: android.graphics.Matrix = android.graphics.Matrix()
                matrix.setRectToRect(
                    viewport,
                    CollageTemplates.bounds(outline),
                    android.graphics.Matrix.ScaleToFit.FILL
                )
                mask.transform(matrix)
                return mask

            }
        } catch (error: java.lang.Exception) {
            return outline
        }
    }

    fun rect(left: Float, top: Float, right: Float, bottom: Float): android.graphics.Path {
        val path: android.graphics.Path = android.graphics.Path()
        path.addRect(left, top, right, bottom, android.graphics.Path.Direction.CW)
        return path
    }

    fun bounds(path: android.graphics.Path): RectF {
        val result: RectF = RectF()
        path.computeBounds(result, true)
        return result
    }

    private fun polygon(vararg xy: Float): android.graphics.Path {
        val path: android.graphics.Path = android.graphics.Path()
        path.moveTo(xy[0], xy[1])
        var i: Int = 2
        while (i + 1 < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.close()
        return path
    }
}
