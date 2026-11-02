package xyz.anands.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---- Anands palette ----
private val ORANGE = Color.parseColor("#F68B1F")
private val INK = Color.parseColor("#0B0B0C")
private val PANEL = Color.parseColor("#D9151517")
private val LINE = Color.parseColor("#2A2A2D")
private val TILE = Color.parseColor("#1A1A1C")
private val GREY = Color.parseColor("#A1A1A6")
private val LABEL = Color.parseColor("#C9C9CC")
private val SCRIM = Color.parseColor("#EB0B0B0C")
private val WHITE = Color.WHITE

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private const val OWNER_NAME = "Anand"

// ---- Your web apps. Edit this list to add or change shortcuts. ----
private class Fav(val name: String, val url: String)

private val FAVORITES = listOf(
    Fav("Photos", "https://photos.anands.xyz"),
    Fav("Files", "https://files.anands.xyz"),
    Fav("Vault", "https://pass.anands.xyz"),
    Fav("Cloud", "https://cloud.anands.xyz"),
    Fav("Desktop", "https://desktop.anands.xyz"),
    Fav("Remote", "https://remote.anands.xyz"),
    Fav("Storage", "https://storage.anands.xyz"),
    Fav("Status", "https://status.anands.xyz"),
)

private class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: Drawable,
)

/** Four rounded squares, the "all apps" glyph. */
private class GridGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }

    override fun onDraw(canvas: Canvas) {
        val s = width * 0.18f
        val gap = width * 0.12f
        val total = s * 2 + gap
        val x0 = (width - total) / 2f
        val y0 = (height - total) / 2f
        for (r in 0..1) {
            for (c in 0..1) {
                val left = x0 + c * (s + gap)
                val top = y0 + r * (s + gap)
                canvas.drawRoundRect(RectF(left, top, left + s, top + s), s * 0.25f, s * 0.25f, paint)
            }
        }
    }
}

class HomeActivity : Activity() {

    private lateinit var homeLayer: View
    private lateinit var drawerLayer: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var countView: TextView
    private lateinit var greetingView: TextView
    private lateinit var dateView: TextView
    private lateinit var adapter: AppAdapter
    private lateinit var swipe: GestureDetector
    private val allApps = ArrayList<AppEntry>()
    private val shown = ArrayList<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (drawerLayer.visibility != View.VISIBLE && dy < -dp(80) && abs(dy) > abs(dx) * 1.5f) {
                    openDrawer(false)
                    return true
                }
                return false
            }
        })

        val root = FrameLayout(this)
        homeLayer = buildHome()
        drawerLayer = buildDrawer()
        drawerLayer.visibility = View.GONE
        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawerLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) &&
            drawerLayer.visibility == View.VISIBLE
        ) {
            closeDrawer()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never exits. Back only closes the drawer.
        if (drawerLayer.visibility == View.VISIBLE) closeDrawer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ------------------------------------------------------------ home

    private fun buildHome(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_launcher) },
            LinearLayout.LayoutParams(dp(28), dp(28)),
        )
        header.addView(
            label("ANANDS OS", 12f, GREY, bold = true).apply { letterSpacing = 0.18f },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) },
        )
        col.addView(header)

        dateView = label("", 14f, GREY)
        greetingView = label("", 28f, WHITE, bold = true)
        col.addView(dateView, linear(top = dp(18)))
        col.addView(greetingView, linear(top = dp(2)))

        val search = label("Search apps", 15f, GREY).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = rounded(PANEL, 26, LINE)
            setOnClickListener { openDrawer(true) }
        }
        col.addView(search, linear(h = dp(52), top = dp(16)))

        col.addView(label("YOUR APPS", 11f, GREY, bold = true).apply { letterSpacing = 0.14f }, linear(top = dp(24)))

        for (chunk in FAVORITES.chunked(4)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (fav in chunk) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setOnClickListener { openUrl(fav.url) }
                }
                cell.addView(letterTile(fav.name.first().toString(), TILE, ORANGE), LinearLayout.LayoutParams(dp(56), dp(56)))
                cell.addView(label(fav.name, 11f, LABEL).apply { gravity = Gravity.CENTER }, linear(top = dp(6)))
                row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            repeat(4 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            col.addView(row, linear(top = dp(14)))
        }

        col.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        col.addView(buildDock())
        return col
    }

    private fun buildDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(PANEL, 30, LINE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val tiles = listOf(
            dockApp(Intent(Intent.ACTION_DIAL), "P"),
            dockApp(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "M"),
            dockApp(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "C"),
            FrameLayout(this).apply {
                background = rounded(ORANGE, 18)
                addView(GridGlyph(context), FrameLayout.LayoutParams(MATCH, MATCH))
                setOnClickListener { openDrawer(false) }
                contentDescription = "All apps"
            },
        )
        for (tile in tiles) {
            val cell = FrameLayout(this)
            cell.addView(tile, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return dock
    }

    /** A dock slot that shows the real icon of whichever app handles [intent]. */
    @Suppress("DEPRECATION")
    private fun dockApp(intent: Intent, fallback: String): View {
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val view: View = if (info != null) {
            ImageView(this).apply {
                setImageDrawable(info.loadIcon(packageManager))
                contentDescription = info.loadLabel(packageManager)
            }
        } else {
            letterTile(fallback, TILE, WHITE)
        }
        view.setOnClickListener { startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return view
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        greetingView.text = "$hello, $OWNER_NAME"
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
    }

    // ------------------------------------------------------------ drawer

    private fun buildDrawer(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SCRIM)
            setPadding(dp(16), dp(36), dp(16), 0)
            isClickable = true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_launcher) },
            LinearLayout.LayoutParams(dp(28), dp(28)),
        )
        top.addView(
            label("All apps", 20f, WHITE, bold = true),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10) },
        )
        countView = label("", 13f, GREY)
        top.addView(countView)
        top.addView(
            label("Close", 14f, ORANGE, bold = true).apply {
                setPadding(dp(16), dp(10), dp(4), dp(10))
                setOnClickListener { closeDrawer() }
            },
        )
        col.addView(top)

        searchBox = EditText(this).apply {
            hint = "Search all apps"
            setHintTextColor(GREY)
            setTextColor(WHITE)
            textSize = 15f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(Color.parseColor("#17171A"), 24, LINE)
            setPadding(dp(18), 0, dp(18), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO && shown.isNotEmpty()) {
                    launchApp(shown[0])
                    true
                } else {
                    false
                }
            }
        }
        col.addView(searchBox, linear(h = dp(48), top = dp(14)))

        adapter = AppAdapter()
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(24))
            this.adapter = this@HomeActivity.adapter
            setOnItemClickListener { _, _, position, _ -> launchApp(shown[position]) }
            setOnItemLongClickListener { _, _, position, _ ->
                showAppMenu(shown[position])
                true
            }
        }
        col.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps.clear()
        for (info in pm.queryIntentActivities(query, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue
            allApps.add(
                AppEntry(
                    info.loadLabel(pm).toString(),
                    pkg,
                    ComponentName(pkg, info.activityInfo.name),
                    info.loadIcon(pm),
                ),
            )
        }
        allApps.sortBy { it.label.lowercase(Locale.getDefault()) }
        applyFilter(searchBox.text.toString())
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        shown.clear()
        shown.addAll(allApps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) })
        adapter.notifyDataSetChanged()
        countView.text = "${shown.size} apps"
    }

    private fun openDrawer(focusSearch: Boolean) {
        searchBox.setText("")
        drawerLayer.animate().cancel()
        drawerLayer.visibility = View.VISIBLE
        drawerLayer.alpha = 0f
        drawerLayer.translationY = dp(60).toFloat()
        drawerLayer.animate().alpha(1f).translationY(0f).setDuration(180).start()
        if (focusSearch) {
            searchBox.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchBox, 0)
        }
    }

    private fun closeDrawer() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchBox.windowToken, 0)
        drawerLayer.animate().cancel()
        drawerLayer.animate()
            .alpha(0f)
            .translationY(dp(60).toFloat())
            .setDuration(150)
            .withEndAction { drawerLayer.visibility = View.GONE }
            .start()
    }

    private fun launchApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startSafely(intent)
        closeDrawer()
    }

    private fun showAppMenu(app: AppEntry) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("App info", "Uninstall")) { _, which ->
                val uri = Uri.parse("package:${app.pkg}")
                if (which == 0) {
                    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } else {
                    startSafely(Intent(Intent.ACTION_DELETE, uri))
                }
            }
            .show()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = shown[position]
            val cell = (convertView as? LinearLayout) ?: LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context), LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(
                    label("", 11f, LABEL).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    linear(top = dp(6)),
                )
            }
            (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }

    // ------------------------------------------------------------ helpers

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) = startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).also {
        it.text = text
        it.textSize = size
        it.setTextColor(color)
        if (bold) it.typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun letterTile(letter: String, fill: Int, textColor: Int): View = FrameLayout(this).apply {
        background = rounded(fill, 18, LINE)
        addView(
            label(letter, 22f, textColor, bold = true).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(MATCH, MATCH),
        )
    }

    private fun linear(w: Int = MATCH, h: Int = WRAP, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = top }
}
