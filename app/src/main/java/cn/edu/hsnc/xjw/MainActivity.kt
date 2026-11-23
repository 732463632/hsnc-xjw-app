package cn.edu.hsnc.xjw

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cn.edu.hsnc.xjw.ui.AppRoot
import cn.edu.hsnc.xjw.ui.XjwTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val startTab = intent?.getIntExtra("open_tab", 0) ?: 0

        setContent {
            XjwTheme {
                AskNotificationPermission()
                AppRoot(startTab = startTab)
            }
        }
    }
}

/** Android 13+ 才需要显式申请；只服务于「新成绩提醒」，拒绝也不影响查询功能 */
@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
