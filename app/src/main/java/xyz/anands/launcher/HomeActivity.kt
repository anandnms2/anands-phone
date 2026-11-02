package xyz.anands.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ---- Anands palette ----
private val ORANGE = Color.parseColor("#F68B1F")
private val INK = Color.parseColor("#0B0B0C")
private val PANEL = Color.parseColor("#B317171A")
private val LINE = Color.parseColor("#2A2A2D")
private val TILE = Color.parseColor("#1A1A1C")
private val GREY = Color.parseColor("#A1A1A6")
private val LABEL = Color.parseColor("#E4E4E7")
private val SCRIM = Color.parseColor("#E60B0B0C")
private val TILE_ORANGE = IconTheme.TILE
private val WHITE = Color.WHITE

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private const val OWNER_NAME = "Anand"

private const val WIDGET_HOST_ID = 1024
private const val REQ_BIND = 11
private const val REQ_CONFIG = 12
private const val WIDGET_PAGES = 2 // pages after the home page

// ---- Your web apps. Edit this list to add or change shortcuts. ----
private class Fav(val name: String, val url: String, val glyph: Int)

private val FAVORITES = listOf(
    Fav("Photos", "https://photos.anands.xyz", R.drawable.g_photos),
    Fav("Files", "https://files.anands.xyz", R.drawable.g_files),
    Fav("Vault", "https://pass.anands.xyz", R.drawable.g_vault),
    Fav("Cloud", "https://cloud.anands.xyz", R.drawable.g_cloud),
    Fav("Desktop", "https://desktop.anands.xyz", R.drawable.g_desktop),
    Fav("Remote", "https://remote.anands.xyz", R.drawable.g_remote),
    Fav("Storage", "https://storage.anands.xyz", R.drawable.g_storage),
    Fav("Status", "https://status.anands.xyz", R.drawable.g_status),
)

private class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: Drawable,
)

/** Four rounded squares, the "all apps" glyph. */
private class GridGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE_ORANGE }

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
    private val iconCache = HashMap<String, Drawable>()
    private lateinit var pager: SnapPager
    private lateinit var dots: LinearLayout
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetMgr: AppWidgetManager
    private val pageCols = ArrayList<LinearLayout>() // index = page
    private lateinit var homeScroll: ScrollView
    private val widgets = ArrayList<Pair<Int, Int>>() // page, widget id
    private val widgetFrames = HashMap<Int, View>()
    private var pendingId = -1
    private var pendingPage = 0
    private lateinit var rootLayer: FrameLayout
    private var introPending = false
    private var introShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        widgetMgr = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)

        swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (drawerLayer.visibility != View.VISIBLE && pager.currentPage() == 0 && !homeScroll.canScrollVertically(1) && dy < -dp(80) && abs(dy) > abs(dx) * 1.5f) {
                    openDrawer(false)
                    return true
                }
                return false
            }
        })

        val root = FrameLayout(this)
        // Soft darkening over the wallpaper so text stays readable but the wallpaper stays visible.
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#99000000"), Color.parseColor("#D9000000")),
        )
        rootLayer = root
        homeLayer = buildHome()
        drawerLayer = buildDrawer()
        drawerLayer.visibility = View.GONE
        root.addView(homeLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawerLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        introPending = bootChanged()
        restoreWidgets()
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (e: Exception) { }
    }

    override fun onStop() {
        super.onStop()
        try { widgetHost.stopListening() } catch (e: Exception) { }
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
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            pager.goTo(0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never exits. Back only closes the drawer.
        if (drawerLayer.visibility == View.VISIBLE) closeDrawer() else if (pager.currentPage() > 0) pager.goTo(0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (hasFocus && introPending && !keyguard.isKeyguardLocked) playIntro()
    }

    /** True once per reboot (and on first install), so the intro plays at the first unlock after boot. */
    private fun bootChanged(): Boolean {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        return boot != getSharedPreferences("anands", Context.MODE_PRIVATE).getInt("boot", -2)
    }

    /** Plays the Anands boot animation over the home screen. Tap the logo in the header to replay it. */
    private fun playIntro() {
        if (introShowing) return
        introPending = false
        introShowing = true
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        getSharedPreferences("anands", Context.MODE_PRIVATE).edit().putInt("boot", boot).apply()
        val logo = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
        var view: BootIntroView? = null
        view = BootIntroView(this, logo) {
            rootLayer.removeView(view)
            introShowing = false
        }
        rootLayer.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ------------------------------------------------------------ home

    private fun buildHome(): View {
        val screenW = resources.displayMetrics.widthPixels
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(36), 0, dp(28))
        }

        pager = SnapPager(this)
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        strip.addView(buildHomePage(), LinearLayout.LayoutParams(screenW, MATCH))
        for (n in 1..WIDGET_PAGES) strip.addView(buildWidgetPage(n), LinearLayout.LayoutParams(screenW, MATCH))
        pager.addView(strip, FrameLayout.LayoutParams(WRAP, MATCH))
        pager.onPage = { updateDots(it) }
        root.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f))

        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        root.addView(dots, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8); bottomMargin = dp(16) })
        updateDots(0)

        root.addView(
            buildDock(),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { marginStart = dp(24); marginEnd = dp(24) },
        )
        return root
    }

    private fun updateDots(active: Int) {
        dots.removeAllViews()
        for (n in 0..WIDGET_PAGES) {
            val dot = View(this).apply {
                background = rounded(if (n == active) ORANGE else Color.parseColor("#66FFFFFF"), 4)
            }
            dots.addView(
                dot,
                LinearLayout.LayoutParams(dp(if (n == active) 22 else 8), dp(8)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                },
            )
        }
    }

    private fun buildHomePage(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_launcher) },
            LinearLayout.LayoutParams(dp(32), dp(32)),
        )
        header.setOnClickListener { playIntro() }
        col.addView(header)

        dateView = label("", 14f, LABEL)
        greetingView = label("", 34f, WHITE, bold = true)
        col.addView(dateView, linear(top = dp(56)))
        col.addView(greetingView, linear(top = dp(6)))

        val search = label("Search apps", 15f, GREY).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = rounded(PANEL, 26)
            setOnClickListener { openDrawer(true) }
        }
        col.addView(search, linear(h = dp(52), top = dp(28)))

        for (chunk in FAVORITES.chunked(4)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (fav in chunk) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setOnClickListener { openUrl(fav.url) }
                }
                cell.addView(glyphTile(fav.glyph), LinearLayout.LayoutParams(dp(64), dp(64)))
                cell.addView(label(fav.name, 12f, LABEL).apply { gravity = Gravity.CENTER }, linear(top = dp(6)))
                row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            repeat(4 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            col.addView(row, linear(top = dp(24)))
        }
        col.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showEditMenu(0)
            true
        }
        pageCols.add(col)
        homeScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        return homeScroll
    }

    /** Press and hold on empty space, like One UI: Widgets, Wallpaper, Home settings. */
    private fun showEditMenu(page: Int) {
        AlertDialog.Builder(this)
            .setTitle("Home screen")
            .setItems(arrayOf("Widgets", "Wallpaper", "Home settings")) { _, which ->
                when (which) {
                    0 -> pickWidget(page)
                    1 -> startSafely(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"),
                    )
                    else -> startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            .show()
    }

    // ------------------------------------------------------------ widget pages

    private fun buildWidgetPage(page: Int): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(16))
            setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showEditMenu(page)
                true
            }
        }
        col.addView(label("Page ${page + 1} \u00b7 Widgets", 13f, LABEL))
        col.addView(
            label("Press and hold to add widgets", 14f, GREY).apply { gravity = Gravity.CENTER },
            linear(top = dp(24)),
        )
        pageCols.add(col)
        return ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun pickWidget(page: Int) {
        val providers = widgetMgr.installedProviders.sortedBy { it.loadLabel(packageManager).lowercase(Locale.getDefault()) }
        if (providers.isEmpty()) {
            Toast.makeText(this, "No widgets installed", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Add widget")
            .setItems(providers.map { it.loadLabel(packageManager) }.toTypedArray()) { _, which ->
                val info = providers[which]
                pendingPage = page
                pendingId = widgetHost.allocateAppWidgetId()
                val ok = try {
                    widgetMgr.bindAppWidgetIdIfAllowed(pendingId, info.provider)
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    afterBind(pendingId)
                } else {
                    val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(ask, REQ_BIND)
                    } catch (e: Exception) {
                        widgetHost.deleteAppWidgetId(pendingId)
                        Toast.makeText(this, "Can't add that widget", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun afterBind(id: Int) {
        val info = widgetMgr.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                @Suppress("DEPRECATION")
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
                // fall through and place it unconfigured
            }
        }
        placeWidget(id)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind(id) else widgetHost.deleteAppWidgetId(id)
            REQ_CONFIG -> if (resultCode == RESULT_OK) placeWidget(id) else widgetHost.deleteAppWidgetId(id)
        }
    }

    private fun placeWidget(id: Int) {
        if (addWidgetView(pendingPage, id)) {
            widgets.add(pendingPage to id)
            saveWidgets()
            pager.post { pager.goTo(pendingPage) }
        }
    }

    private fun addWidgetView(page: Int, id: Int): Boolean {
        val info = widgetMgr.getAppWidgetInfo(id) ?: return false
        val col = pageCols.getOrNull(page) ?: return false
        val frame = LongPressFrame(this) { confirmRemove(id) }
        val host = widgetHost.createView(this, id, info)
        val wDp = ((resources.displayMetrics.widthPixels - dp(48)) / resources.displayMetrics.density).toInt()
        val hPx = min(max(info.minHeight, dp(110)), dp(420))
        val hDp = (hPx / resources.displayMetrics.density).toInt()
        host.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
        frame.addView(host, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(frame, if (page > 0) col.childCount - 1 else col.childCount, linear(h = hPx, top = dp(12)))
        widgetFrames[id] = frame
        return true
    }

    private fun confirmRemove(id: Int) {
        AlertDialog.Builder(this)
            .setTitle("Widget")
            .setItems(arrayOf("Remove")) { _, _ ->
                widgetFrames.remove(id)?.let { (it.parent as? ViewGroup)?.removeView(it) }
                widgetHost.deleteAppWidgetId(id)
                widgets.removeAll { it.second == id }
                saveWidgets()
            }
            .show()
    }

    private fun saveWidgets() {
        getSharedPreferences("anands", Context.MODE_PRIVATE).edit()
            .putString("widgets", widgets.joinToString(",") { "${it.first}:${it.second}" }).apply()
    }

    private fun restoreWidgets() {
        val saved = getSharedPreferences("anands", Context.MODE_PRIVATE).getString("widgets", "").orEmpty()
        for (part in saved.split(",").filter { it.contains(":") }) {
            val (page, id) = part.split(":").map { it.toInt() }
            if (addWidgetView(page, id)) widgets.add(page to id) else widgetHost.deleteAppWidgetId(id)
        }
        saveWidgets()
    }

    private fun buildDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val tiles = listOf(
            dockApp(Intent(Intent.ACTION_DIAL), R.drawable.g_phone),
            dockApp(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), R.drawable.g_messages),
            dockApp(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), R.drawable.g_camera),
            FrameLayout(this).apply {
                background = rounded(WHITE, 20)
                addView(GridGlyph(context), FrameLayout.LayoutParams(MATCH, MATCH))
                setOnClickListener { openDrawer(false) }
                contentDescription = "All apps"
            },
        )
        for (tile in tiles) {
            val cell = FrameLayout(this)
            cell.addView(tile, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return dock
    }

    /** A dock slot: Anands glyph tile that opens whichever app handles [intent]. */
    private fun dockApp(intent: Intent, glyph: Int): View =
        glyphTile(glyph).apply {
            setOnClickListener { startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
            setPadding(dp(24), dp(36), dp(24), 0)
            isClickable = true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_launcher) },
            LinearLayout.LayoutParams(dp(32), dp(32)),
        )
        top.addView(
            label("All apps", 20f, WHITE, bold = true),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10) },
        )
        countView = label("", 13f, GREY)
        top.addView(countView)
        top.addView(
            label("Done", 15f, ORANGE, bold = true).apply {
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
            background = rounded(PANEL, 24)
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
            verticalSpacing = dp(22)
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
                    iconFor("$pkg/${info.activityInfo.name}") { info.loadIcon(pm) },
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
                addView(ImageView(context), LinearLayout.LayoutParams(dp(60), dp(60)))
                addView(
                    label("", 12f, LABEL).apply {
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

    /** Strict theming: every icon goes through the Anands tile, no opt-out. */
    private fun iconFor(key: String, raw: () -> Drawable): Drawable =
        iconCache.getOrPut(key) { IconTheme.restyle(this, raw()) }

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

    /** Orange rounded tile with a white line glyph, the Anands icon style. */
    private fun glyphTile(glyph: Int): View = FrameLayout(this).apply {
        background = rounded(TILE_ORANGE, 20)
        addView(
            ImageView(context).apply { setImageResource(glyph) },
            FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER),
        )
    }

    private fun linear(w: Int = MATCH, h: Int = WRAP, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = top }
}
