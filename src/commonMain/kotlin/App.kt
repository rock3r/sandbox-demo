import LookupResult.Empty
import LookupResult.Occupied
import LookupResult.OutOfBounds
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val DEBUG_DRAW = false
private const val LOGGING = false

@Composable
fun app(cellSize: Int = 5) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
    val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }
    val cellSizePx = with(LocalDensity.current) { cellSize.dp.toPx() }
    val width = (widthPx / cellSizePx).toInt()
    val height = (heightPx / cellSizePx).toInt()

    LaunchedEffect(widthPx, heightPx) {
        println("Canvas size: $widthPx*$heightPx -> unit $cellSizePx, grid $width*$height")
    }

    Content(cellSize.dp, width, height, 60)
}

@Composable
@Preview
fun BoxScope.Content(unitSize: Dp, width: Int, height: Int, fps: Int) {
    var grid by remember(width, height) { mutableStateOf(createEmptyGrid(width, height)) }

    LaunchedEffect(width, height) {
        if (LOGGING) {
            println(
                "Initialised grid: ${grid.size}*${grid.first().size} " +
                    "(lastIndices: ${grid.lastIndex}*${grid.first().lastIndex})"
            )
        }
    }

    // This is the core simulation loop
    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
            while (isActive) {
                grid = doSimulation(grid)
                delay((1.0 / fps).seconds)
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val hue = remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier =
            Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    handleDrag(change, dragAmount, unitSize, width, height, grid, hue)
                }
            }
    ) {
        if (DEBUG_DRAW) {
            drawDebugGridBounds(width, height, unitSize)
        }

        for ((x, column) in grid.withIndex()) {
            if (DEBUG_DRAW) {
                drawDebugColumn(x, unitSize, height, measurer)
            }

            for ((y, grain) in column.withIndex()) {
                if (DEBUG_DRAW) {
                    drawDebugRow(y, unitSize, width, measurer)
                }

                if (grain == null) continue
                val topLeft = Offset(grain.x * unitSize.toPx(), grain.y * unitSize.toPx())
                drawRect(
                    color = grain.color,
                    topLeft = topLeft,
                    size = DpSize(unitSize, unitSize).toSize()
                )

                if (LOGGING) {
                    println("Grain ${grain.id} drawn at $topLeft")
                }

                if (DEBUG_DRAW) {
                    // Draw grain ID on top of the grain
                    drawText(
                        measurer,
                        grain.id.toString(),
                        (grain.position.toOffset() * unitSize.toPx()) + Offset(2f, 2f),
                        TextStyle.Default.copy(
                            color = Color.Red,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            background = Color.White,
                        )
                    )
                }
            }
        }
    }

    BasicText(
        "Click here to clear",
        Modifier.padding(16.dp)
            .align(Alignment.TopEnd)
            .clickable { grid = createEmptyGrid(width, height) }
            .pointerHoverIcon(PointerIcon.Hand),
        TextStyle.Default.copy(color = Color.Gray, fontSize = 14.sp)
    )
}

private fun PointerInputScope.handleDrag(
    change: PointerInputChange,
    dragAmount: Offset,
    unitSize: Dp,
    width: Int,
    height: Int,
    grid: Array<Array<Grain?>>,
    hue: MutableFloatState,
) {
    var startX = change.position.x.roundToInt()
    var startY = change.position.y.roundToInt()
    var endX = startX + dragAmount.x.toInt()
    var endY = startY + dragAmount.y.toInt()

    if (startX > endX) {
        val tmp = endX
        endX = startX
        startX = tmp
    }

    if (startY > endY) {
        val tmp = endY
        endY = startY
        startY = tmp
    }

    for (x in startX..endX) {
        for (y in startY..endY) {
            addGrain(
                Offset(x.toFloat(), y.toFloat()),
                unitSize,
                width,
                height,
                grid,
                hue.value,
            )

            hue.value += .005f
            if (hue.value > 360f) hue.value = 0f
        }
    }
    change.consume()
}

private fun PointerInputScope.addGrain(
    offset: Offset,
    unitSize: Dp,
    width: Int,
    height: Int,
    grid: Array<Array<Grain?>>,
    hue: Float
) {
    val x = (offset.x / unitSize.toPx()).toInt()
    if (x !in 0 until width) return

    val y = (offset.y / unitSize.toPx()).toInt()
    if (y !in 0 until height) return

    val grain = grid[x][y]
    if (grain == null) {
        val newGrain = Grain(IntOffset(x, y), Color.hsv(hue, 1f, 1f))
        if (LOGGING) println("Adding grain $newGrain")
        grid[x][y] = newGrain
    }
}

private fun DrawScope.drawDebugRow(y: Int, unitSize: Dp, width: Int, measurer: TextMeasurer) {
    val yPx = y * unitSize.toPx()
    drawLine(
        if (y % 10 == 0) Color.DarkGray else Color.Gray,
        Offset(0f, yPx),
        Offset(width * unitSize.toPx(), yPx)
    )
    drawText(
        measurer,
        y.toString(),
        Offset(0f, yPx),
        TextStyle.Default.copy(color = Color.Gray, fontSize = 9.sp),
        size = Size(2 * unitSize.toPx(), unitSize.toPx())
    )
}

private fun DrawScope.drawDebugColumn(x: Int, unitSize: Dp, height: Int, measurer: TextMeasurer) {
    val xPx = x * unitSize.toPx()
    drawLine(
        if (x % 10 == 0) Color.DarkGray else Color.Gray,
        Offset(xPx, 0f),
        Offset(xPx, height * unitSize.toPx())
    )
    drawText(
        measurer,
        x.toString(),
        Offset(xPx, 0f),
        TextStyle.Default.copy(color = Color.Gray, fontSize = 9.sp),
        size = Size(unitSize.toPx(), unitSize.toPx())
    )
}

private fun DrawScope.drawDebugGridBounds(width: Int, height: Int, unitSize: Dp) {
    // Draw the background of the floor (last row)
    drawRect(
        Color.Red.copy(.5f),
        Offset(0f, (height - 1) * unitSize.toPx()),
        Size(width * unitSize.toPx(), unitSize.toPx())
    )

    // Draw the background of the left-hand wall (first column)
    drawRect(
        Color.Green.copy(.25f),
        Offset(0f, 0f),
        Size(unitSize.toPx(), height * unitSize.toPx())
    )

    // Draw the background of the right-hand wall (last column)
    drawRect(
        Color.Green.copy(.25f),
        Offset((width - 1) * unitSize.toPx(), 0f),
        Size(unitSize.toPx(), height * unitSize.toPx())
    )

    // Draw line at left wall (before first column)
    drawLine(Color.Blue, Offset(0f, 0f), Offset(0f, height * unitSize.toPx()), 3.dp.toPx())

    // Draw line at right wall (after last column)
    drawLine(
        Color.Blue,
        Offset(width * unitSize.toPx(), 0f),
        Offset(width * unitSize.toPx(), height * unitSize.toPx()),
        3.dp.toPx()
    )

    // Draw line at the floor (below last row)
    drawLine(
        Color.Red,
        Offset(0f, height * unitSize.toPx()),
        Offset(width * unitSize.toPx(), height * unitSize.toPx()),
        3.dp.toPx()
    )
}

private var id: Int = 0

private fun nextId(): Int = ++id

private data class Grain(
    var position: IntOffset,
    var color: Color,
    var age: Int = 0,
    var active: Boolean = true,
    val id: Int = nextId()
) {
    var x: Int
        get() = position.x
        set(value) {
            position = position.copy(x = value)
        }

    var y: Int
        get() = position.y
        set(value) {
            position = position.copy(y = value)
        }
}

private fun doSimulation(grid: Array<Array<Grain?>>): Array<Array<Grain?>> {
    val width = grid.size
    val height = grid[0].size
    val newGrid = createEmptyGrid(width, height)
    val maxY = height - 1

    for (row in grid) {
        for (grain in row) {
            if (grain == null) continue

            val x = grain.position.x
            val y = grain.position.y
            if (!grain.active) {
                newGrid[x][y] = grain
                continue
            }

            grain.age++

            val newY = y + 1 // TODO Increase based on grain.age
            val deltaX = if (Random.nextBoolean()) 1 else -1

            val lookupBelow = grid.lookup(x, newY)
            val lookupPlusDelta = grid.lookup(x + deltaX, newY)
            val lookupMinusDelta = grid.lookup(x - deltaX, newY)

            if (lookupBelow is Empty) {
                grain.y = newY
            } else if (lookupPlusDelta is Empty) {
                grain.x = x + deltaX
                grain.y = newY
            } else if (lookupMinusDelta is Empty) {
                grain.x = x - deltaX
                grain.y = newY
            }

            if (newY == maxY) {
                grain.active = false
                if (LOGGING) println("Grain ${grain.id} reached the bottom ($maxY)")
            }

            if (LOGGING) println("Updated grain $grain")
            newGrid[grain.x][grain.y] = grain
        }
    }

    return newGrid
}

private fun createEmptyGrid(width: Int, height: Int) = Array(width) { arrayOfNulls<Grain>(height) }

private inline fun <reified T> Array<Array<T?>>.lookup(x: Int, y: Int): LookupResult {
    if (x < 0 || x > lastIndex) return OutOfBounds
    if (y < 0 || y > first().lastIndex) return OutOfBounds

    return if (this[x][y] != null) Occupied else Empty
}

private sealed interface LookupResult {
    data object Empty : LookupResult

    data object Occupied : LookupResult

    data object OutOfBounds : LookupResult
}
