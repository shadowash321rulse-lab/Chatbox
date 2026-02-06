package com.scrapw.chatbox.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scrapw.chatbox.R
import com.scrapw.chatbox.overlay.ui.ButtonOverlay
import com.scrapw.chatbox.overlay.ui.MessengerOverlay
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.theme.OverlayTheme
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"

        private const val CHANNEL_ID = "vrca_overlay_fg"
        private const val CHANNEL_NAME = "VRC-A Background"
        private const val NOTIF_ID = 31001

        const val ACTION_STOP = "com.scrapw.chatbox.overlay.STOP"
    }

    private lateinit var buttonComposeView: ComposeView
    private lateinit var msgComposeView: ComposeView

    private val lifecycleOwner = MyLifecycleOwner()

    private val buttonDefaultPos = Offset(1f, 0.7f)
    private val msgDefaultPos = Offset(0f, 0.1f)

    enum class Window { NONE, BUTTON, MESSENGER }
    private var currentWindow = Window.NONE

    private val windowManager get() = getSystemService(WINDOW_SERVICE) as WindowManager

    private val defaultOverlayParams
        get() = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            format = PixelFormat.TRANSLUCENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            windowAnimations = android.R.style.Animation_Dialog
            gravity = Gravity.START or Gravity.TOP
            flags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

    private var buttonWindowParams = defaultOverlayParams
    private var msgWindowParams = defaultOverlayParams.apply {
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
    }

    // Orientation + stored positions
    var orientation = mutableStateOf(Configuration.ORIENTATION_PORTRAIT)

    private fun isPortrait(): Boolean = orientation.value != Configuration.ORIENTATION_LANDSCAPE

    private var buttonPortraitPos: Offset? = null
    private var buttonLandscapePos: Offset? = null

    private var msgPortraitPos: Offset? = null
    private var msgLandscapePos: Offset? = null

    private var overlayOffset: Offset by mutableStateOf(Offset.Zero)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        // ✅ CRITICAL: foreground immediately so Android won’t freeze/kill us on screen-off
        ensureForeground()

        buttonComposeView = ComposeView(this)
        msgComposeView = ComposeView(this)

        orientation.value = resources.configuration.orientation

        onOrientationChange()
        initOverlay()
        switchOverlay(Window.BUTTON)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Keep notification present if system restarts us
        ensureForeground()

        // ✅ This tells Android to restart service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")

        try {
            when (currentWindow) {
                Window.BUTTON -> windowManager.removeViewImmediate(buttonComposeView)
                Window.MESSENGER -> windowManager.removeViewImmediate(msgComposeView)
                Window.NONE -> {}
            }
        } catch (_: Throwable) {
        }

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        currentWindow = Window.NONE
    }

    override fun onBind(intent: Intent): IBinder? = null

    // =========================
    // Foreground service
    // =========================
    private fun ensureForeground() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = nm.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Keeps VRC-A running so Now Playing works with screen off."
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_SECRET
                    }
                    nm.createNotificationChannel(ch)
                }
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // uses your existing launcher icon
                .setContentTitle("VRC-A running")
                .setContentText("Keeping Now Playing active in the background.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            startForeground(NOTIF_ID, notification)
        } catch (t: Throwable) {
            // If something goes wrong, don’t crash the app — but logs help.
            Log.e(TAG, "Failed to start foreground", t)
        }
    }

    // =========================
    // Overlay UI
    // =========================
    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlay() {
        buttonComposeView.setContent {
            OverlayTheme {
                OverlayDraggableContainer {
                    ButtonOverlay { switchOverlay(Window.MESSENGER) }
                }

                val configuration = LocalConfiguration.current
                LaunchedEffect(configuration) {
                    orientation.value = configuration.orientation
                    onOrientationChange()
                }
            }
        }

        msgComposeView.setContent {
            val chatboxViewModel: ChatboxViewModel =
                if (!ChatboxViewModel.isInstanceInitialized()) {
                    viewModel(factory = ChatboxViewModel.Factory)
                } else {
                    ChatboxViewModel.getInstance()
                }

            OverlayTheme {
                MessengerOverlay(chatboxViewModel) {
                    switchOverlay(Window.BUTTON)
                }

                val configuration = LocalConfiguration.current
                LaunchedEffect(configuration) {
                    orientation.value = configuration.orientation
                    onOrientationChange()
                }
            }
        }

        msgComposeView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                switchOverlay(Window.BUTTON)
            }
            true
        }

        // Trick the ComposeView into thinking we track lifecycle
        val viewModelStoreOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = ViewModelStore()
        }

        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        buttonComposeView.setViewTreeLifecycleOwner(lifecycleOwner)
        buttonComposeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        buttonComposeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        msgComposeView.setViewTreeLifecycleOwner(lifecycleOwner)
        msgComposeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        msgComposeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun switchOverlay(destinationWindow: Window) {
        try {
            when (currentWindow) {
                Window.BUTTON -> windowManager.removeView(buttonComposeView)
                Window.MESSENGER -> windowManager.removeView(msgComposeView)
                Window.NONE -> {}
            }
        } catch (_: Throwable) {
        }

        try {
            when (destinationWindow) {
                Window.BUTTON -> {
                    buttonComposeView.removeAllViews()
                    windowManager.addView(buttonComposeView, buttonWindowParams)
                }
                Window.MESSENGER -> {
                    msgComposeView.removeAllViews()
                    windowManager.addView(msgComposeView, msgWindowParams)
                }
                Window.NONE -> {}
            }
        } catch (_: Throwable) {
        }

        currentWindow = destinationWindow
    }

    private fun update() {
        try {
            when (currentWindow) {
                Window.BUTTON -> windowManager.updateViewLayout(buttonComposeView, buttonWindowParams)
                Window.MESSENGER -> windowManager.updateViewLayout(msgComposeView, msgWindowParams)
                Window.NONE -> {}
            }
        } catch (_: Throwable) {
        }
    }

    private fun onOrientationChange() {
        if (isPortrait()) {
            if (buttonPortraitPos == null) {
                val f = Rect().also { buttonComposeView.getWindowVisibleDisplayFrame(it) }
                buttonPortraitPos = Offset(
                    x = f.width() * buttonDefaultPos.x,
                    y = f.height() * buttonDefaultPos.y,
                )
            }
            buttonWindowParams.apply {
                x = buttonPortraitPos!!.x.toInt()
                y = buttonPortraitPos!!.y.toInt()
            }
            overlayOffset = buttonPortraitPos as Offset

            if (msgPortraitPos == null) {
                val f = Rect().also { msgComposeView.getWindowVisibleDisplayFrame(it) }
                msgPortraitPos = Offset(
                    x = f.width() * msgDefaultPos.x,
                    y = f.height() * msgDefaultPos.y,
                )
            }
            msgWindowParams.apply {
                x = msgPortraitPos!!.x.toInt()
                y = msgPortraitPos!!.y.toInt()
            }

        } else {
            if (buttonLandscapePos == null) {
                val f = Rect().also { buttonComposeView.getWindowVisibleDisplayFrame(it) }
                buttonLandscapePos = Offset(
                    x = f.width() * buttonDefaultPos.x,
                    y = f.height() * buttonDefaultPos.y,
                )
            }
            buttonWindowParams.apply {
                x = buttonLandscapePos!!.x.toInt()
                y = buttonLandscapePos!!.y.toInt()
            }
            overlayOffset = buttonLandscapePos as Offset

            if (msgLandscapePos == null) {
                val f = Rect().also { msgComposeView.getWindowVisibleDisplayFrame(it) }
                msgLandscapePos = Offset(
                    x = f.width() * msgDefaultPos.x,
                    y = f.height() * msgDefaultPos.y,
                )
            }
            msgWindowParams.apply {
                x = msgLandscapePos!!.x.toInt()
                y = msgLandscapePos!!.y.toInt()
            }
        }

        update()
    }

    @Composable
    fun OverlayDraggableContainer(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) = Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()

                val newOffset = Offset(
                    if (buttonWindowParams.gravity and Gravity.END == Gravity.END) {
                        overlayOffset.x - dragAmount.x
                    } else {
                        overlayOffset.x + dragAmount.x
                    },
                    if (buttonWindowParams.gravity and Gravity.BOTTOM == Gravity.BOTTOM) {
                        overlayOffset.y - dragAmount.y
                    } else {
                        overlayOffset.y + dragAmount.y
                    }
                )

                overlayOffset = newOffset

                buttonWindowParams.apply {
                    x = overlayOffset.x.roundToInt()
                    y = overlayOffset.y.roundToInt()
                }

                if (isPortrait()) buttonPortraitPos = overlayOffset else buttonLandscapePos = overlayOffset

                try {
                    windowManager.updateViewLayout(buttonComposeView, buttonWindowParams)
                } catch (_: Throwable) {
                }
            }
        },
        content = content
    )
}
