import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center))
    Window(
        title = "Sandbox",
        onCloseRequest = ::exitApplication,
        resizable = false,
        state = state,
    ) {
        app()
    }
}
