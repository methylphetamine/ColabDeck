package dev.colabdeck.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView

@Composable
fun TermuxTerminalComposable(
    controller: TermuxTerminalController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TerminalView(context).also { view ->
                controller.attach(context, view)
            }
        }
    )
}
