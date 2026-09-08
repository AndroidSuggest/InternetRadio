package com.armanmaurya.internetradio.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import java.net.URLEncoder

class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_LOG = "EXTRA_CRASH_LOG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: "No crash log available"
        
        setContent {
            InternetRadioTheme {
                CrashScreen(
                    crashLog = crashLog,
                    onCopyClick = {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Crash Log", crashLog)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@CrashActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onShareClick = {
                        val encodedBody = URLEncoder.encode("```\n$crashLog\n```", "UTF-8")
                        val url = "https://github.com/armanmaurya/InternetRadio/issues/new?title=App+Crash&body=$encodedBody"
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                    },
                    onRestartClick = {
                        val intent = Intent(this, MobileActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashLog: String,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Whoops!") },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Button(
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share crash logs")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCopyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy to clipboard")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestartClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restart application")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Internet Radio ran into an unexpected error. We suggest you share the crash logs in our github.",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = crashLog,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
