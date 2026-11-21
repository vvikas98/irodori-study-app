package com.irodoristudy.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.irodoristudy.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: StudyStorage

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private var pomodoroTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingMillis = 25L * 60L * 1000L

    private val studyTickerHandler = Handler(Looper.getMainLooper())
    private val studyTicker = object : Runnable {
        override fun run() {
            storage.recordStudyMinute()
            updateStudyBadge()
            studyTickerHandler.postDelayed(this, 60_000L)
        }
    }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val req = pendingPermissionRequest
            pendingPermissionRequest = null
            if (req != null) {
                if (granted) req.grant(req.resources) else req.deny()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = StudyStorage(this)
        applyKeepAwake(storage.isKeepAwakeEnabled())

        setupWebView()
        setupBottomBar()
        setupBackNavigation()
        updateStudyBadge()

        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }

        binding.btnRetryOffline.setOnClickListener {
            binding.offlineLayout.visibility = View.GONE
            binding.webView.reload()
        }

        // Resume last visited Irodori page automatically
        binding.webView.loadUrl(storage.getLastUrl())
    }

    override fun onResume() {
        super.onResume()
        studyTickerHandler.postDelayed(studyTicker, 60_000L)
    }

    override fun onPause() {
        super.onPause()
        studyTickerHandler.removeCallbacks(studyTicker)
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pomodoroTimer?.cancel()
        super.onDestroy()
    }

    private fun applyKeepAwake(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Remove '; wv' so Google OAuth sign-in is not blocked as an embedded WebView
        settings.userAgentString = settings.userAgentString.replace("; wv", "")

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.offlineLayout.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                if (!url.isNullOrBlank()) {
                    storage.saveLastUrl(url)
                }
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host.orEmpty()

                // Keep Irodori, Japan Foundation, and login auth domains inside the app
                val isInternal = host.endsWith("jpf.go.jp") ||
                    host.contains("accounts.google.com") ||
                    host.contains("facebook.com") ||
                    host.contains("auth") ||
                    host.contains("login")

                if (isInternal) {
                    return false
                }

                // Open external resources (e.g. Play Store, PDFs, YouTube) in Chrome Custom Tabs
                openInCustomTab(uri.toString())
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.offlineLayout.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (needsAudio) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            request.grant(request.resources)
                        } else {
                            pendingPermissionRequest = request
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        request.grant(request.resources)
                    }
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(view)
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.mainContainer.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                binding.fullscreenContainer.removeAllViews()
                binding.fullscreenContainer.visibility = View.GONE
                binding.mainContainer.visibility = View.VISIBLE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun setupBottomBar() {
        binding.btnHome.setOnClickListener {
            binding.webView.loadUrl(StudyStorage.HOME_URL)
        }

        binding.btnBookmarks.setOnClickListener {
            showBookmarksSheet()
        }

        binding.btnNotes.setOnClickListener {
            showNotesSheet()
        }

        binding.btnDictionary.setOnClickListener {
            showDictionaryDialog()
        }

        binding.btnTools.setOnClickListener {
            showStudyToolsSheet()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    binding.webView.webChromeClient?.onHideCustomView()
                } else if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun updateStudyBadge() {
        val streak = storage.getStreakDays()
        val mins = storage.getTodayMinutes()
        val timerText = if (isTimerRunning) {
            val m = (remainingMillis / 1000) / 60
            val s = (remainingMillis / 1000) % 60
            String.format(" • %02d:%02d", m, s)
        } else ""
        binding.tvStudyStatus.text = "JFT/N4 Study • ${mins}m today • ${streak}d streak$timerText"
    }

    private fun showBookmarksSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_bookmarks, null)
        val container = view.findViewById<LinearLayout>(R.id.bookmarkListContainer)
        val btnSaveCurrent = view.findViewById<MaterialButton>(R.id.btnSaveCurrentLesson)

        fun renderList() {
            container.removeAllViews()
            val bookmarks = storage.getBookmarks()
            if (bookmarks.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "No bookmarks yet. Tap 'Bookmark Current Lesson' to save your place."
                    setPadding(8, 16, 8, 16)
                }
                container.addView(empty)
                return
            }

            bookmarks.forEach { item ->
                val row = layoutInflater.inflate(R.layout.item_bookmark, container, false)
                row.findViewById<TextView>(R.id.tvBookmarkTitle).text = item.title
                row.findViewById<TextView>(R.id.tvBookmarkMeta).text = item.savedAt
                row.setOnClickListener {
                    binding.webView.loadUrl(item.url)
                    dialog.dismiss()
                }
                row.findViewById<View>(R.id.btnDeleteBookmark).setOnClickListener {
                    storage.removeBookmark(item.url)
                    renderList()
                }
                container.addView(row)
            }
        }

        btnSaveCurrent.setOnClickListener {
            val currentUrl = binding.webView.url ?: StudyStorage.HOME_URL
            val defaultTitle = binding.webView.title ?: "Irodori Lesson"
            val input = EditText(this).apply { setText(defaultTitle) }
            MaterialAlertDialogBuilder(this)
                .setTitle("Save Lesson Bookmark")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    storage.addBookmark(input.text.toString(), currentUrl)
                    renderList()
                    Toast.makeText(this, "Lesson bookmarked", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        renderList()
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showNotesSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_notes, null)
        val etNotes = view.findViewById<EditText>(R.id.etStudyNotes)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveNotes)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnShareNotes)

        etNotes.setText(storage.getNotes())

        btnSave.setOnClickListener {
            storage.saveNotes(etNotes.text.toString())
            Toast.makeText(this, "Notes saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            val text = etNotes.text.toString()
            storage.saveNotes(text)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Irodori Japanese Study Notes")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(sendIntent, "Export Study Notes"))
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showDictionaryDialog() {
        val input = EditText(this).apply {
            hint = "Enter Japanese word, kana, or English..."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Quick Jisho.org Lookup")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    val encoded = Uri.encode(query)
                    openInCustomTab("https://jisho.org/search/$encoded")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStudyToolsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_tools, null)

        val switchAwake = view.findViewById<MaterialSwitch>(R.id.switchKeepAwake)
        val tvTimer = view.findViewById<TextView>(R.id.tvPomodoroDisplay)
        val btnToggleTimer = view.findViewById<MaterialButton>(R.id.btnToggleTimer)
        val btnResetTimer = view.findViewById<MaterialButton>(R.id.btnResetTimer)
        val btnOpenPractice = view.findViewById<MaterialButton>(R.id.btnOpenPracticeApp)

        switchAwake.isChecked = storage.isKeepAwakeEnabled()
        switchAwake.setOnCheckedChangeListener { _, isChecked ->
            storage.setKeepAwakeEnabled(isChecked)
            applyKeepAwake(isChecked)
        }

        fun refreshTimerUi() {
            val m = (remainingMillis / 1000) / 60
            val s = (remainingMillis / 1000) % 60
            tvTimer.text = String.format("%02d:%02d", m, s)
            btnToggleTimer.text = if (isTimerRunning) "Pause" else "Start 25m Focus"
        }

        refreshTimerUi()

        btnToggleTimer.setOnClickListener {
            if (isTimerRunning) {
                pomodoroTimer?.cancel()
                isTimerRunning = false
                refreshTimerUi()
                updateStudyBadge()
            } else {
                isTimerRunning = true
                pomodoroTimer = object : CountDownTimer(remainingMillis, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingMillis = millisUntilFinished
                        refreshTimerUi()
                        updateStudyBadge()
                    }

                    override fun onFinish() {
                        isTimerRunning = false
                        remainingMillis = 25L * 60L * 1000L
                        refreshTimerUi()
                        updateStudyBadge()
                        Toast.makeText(
                            this@MainActivity,
                            "25-minute study session complete! Take a 5-min break.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.start()
                refreshTimerUi()
            }
        }

        btnResetTimer.setOnClickListener {
            pomodoroTimer?.cancel()
            isTimerRunning = false
            remainingMillis = 25L * 60L * 1000L
            refreshTimerUi()
            updateStudyBadge()
        }

        btnOpenPractice.setOnClickListener {
            val pkg = "jpf.go.jp.irodorionline.irodoriPractice"
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                openInCustomTab("https://play.google.com/store/apps/details?id=$pkg")
            }
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun openInCustomTab(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
