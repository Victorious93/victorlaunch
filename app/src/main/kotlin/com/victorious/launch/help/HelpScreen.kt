package com.victorious.launch.help

import android.content.res.AssetManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.OnDark

@Composable
fun HelpScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val body =
        remember {
            readManual(context.assets)
        }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        Text(
            body,
            color = OnDark,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        )
    }
}

private fun readManual(assets: AssetManager): String {
    val names = listOf("MANUAL.md", "docs/MANUAL.md")
    for (name in names) {
        try {
            assets.open(name).bufferedReader().use { return it.readText() }
        } catch (_: Exception) {
        }
    }
    return "The manual could not be loaded from app assets. See docs/MANUAL.md in the source tree."
}
