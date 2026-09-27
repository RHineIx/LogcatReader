package com.dp.logcatapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import com.dp.logcatapp.services.LogcatService
import com.dp.logcatapp.ui.screens.App
import com.dp.logcatapp.ui.screens.ScreenKey
import com.dp.logcatapp.ui.theme.LogcatReaderTheme
import com.dp.logcatapp.util.SettingsPrefKeys
import com.dp.logcatapp.util.getDefaultSharedPreferences
import com.dp.logcatapp.util.setKeepScreenOn
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

  private val viewModel: MainActivityViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    if (handleExitNotificationAction(intent)) {
      return
    }

    if (intent.shouldStopRecording()) {
      viewModel.sendStopRecordingSignal()
    }

    val uri = intent.data
    val initialScreen = if (uri != null) {
      ScreenKey.SavedLogsViewer(uri)
    } else {
      ScreenKey.DeviceLogs
    }
    setContent {
      LogcatReaderTheme {
        App(
          initialScreen = initialScreen,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    setKeepScreenOn(
      getDefaultSharedPreferences().getBoolean(
        SettingsPrefKeys.General.KEY_KEEP_SCREEN_ON,
        SettingsPrefKeys.General.Default.KEY_KEEP_SCREEN_ON
      )
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (handleExitNotificationAction(intent)) {
      return
    }

    if (intent.shouldStopRecording()) {
      viewModel.sendStopRecordingSignal()
      return
    }

    val uri = intent.data
    if (uri != null) {
      viewModel.showViewLogsScreen(uri)
      return
    }
  }

  private fun Intent.shouldStopRecording(): Boolean {
    return getBooleanExtra(STOP_RECORDING_EXTRA, false)
  }

  private fun Intent?.shouldExit(): Boolean {
    return this?.getBooleanExtra(EXIT_EXTRA, false) == true
  }

  private fun handleExitNotificationAction(intent: Intent?): Boolean =
    if (intent.shouldExit()) {
      // Stop logcat service first.
      LogcatService.stop(this)
      ActivityCompat.finishAfterTransition(this)
      true
    } else {
      false
    }

  companion object {
    const val EXIT_EXTRA = "exit_extra"
    const val STOP_RECORDING_EXTRA = "stop_recording_extra"
  }
}

class MainActivityViewModel : ViewModel() {
  private var _stopRecordingSignal = Channel<Unit>(
    capacity = 1,
    onBufferOverflow = DROP_OLDEST,
  )
  val stopRecordingSignal = _stopRecordingSignal.receiveAsFlow()

  private var _viewLogs = Channel<Uri>(
    capacity = 1,
    onBufferOverflow = DROP_OLDEST,
  )
  val viewLogs = _viewLogs.receiveAsFlow()

  fun sendStopRecordingSignal() {
    _stopRecordingSignal.trySend(Unit)
  }

  fun showViewLogsScreen(uri: Uri) {
    _viewLogs.trySend(uri)
  }
}
