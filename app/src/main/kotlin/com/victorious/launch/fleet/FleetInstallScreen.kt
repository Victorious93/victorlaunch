package com.victorious.launch.fleet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

/**
 * Stub only. The fleet update manifest schema is still pending
 * (Victorious93/fleet-manifest). This screen does not download or install APKs.
 */
@Composable
fun FleetInstallScreen(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        Text("Install / Update fleet apps", color = Magenta, fontSize = 20.sp)
        Text(
            "This surface is a stub. The signed fleet update manifest schema is not frozen yet, so VictorLaunch will not install or update anything from here.\n\n" +
                "When the schema lands, this screen will read a local or controller-provided manifest (package, version, SHA-256, cert pin) and ask you before each install. It will not gain an INTERNET permission just to poll GitHub.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
