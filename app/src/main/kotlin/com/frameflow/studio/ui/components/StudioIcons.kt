package com.frameflow.studio.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Crisp minimalist stroke-based vector icons designed for FrameFlow's phone studio UI.
 * Zero external icon-pack bloat.
 */
object StudioIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private val strokeBrush = SolidColor(Color.Black)

    private fun ImageVector.Builder.strokePath(
        width: Float = 1.9f,
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) {
        path(
            stroke = strokeBrush,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathData,
        )
    }

    val Back: ImageVector by lazy {
        icon("Back") {
            strokePath {
                moveTo(15f, 6f)
                lineTo(9f, 12f)
                lineTo(15f, 18f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            strokePath(2.1f) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            path(fill = strokeBrush) {
                moveTo(8f, 5.5f)
                lineTo(19f, 12f)
                lineTo(8f, 18.5f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            strokePath(2.6f) {
                moveTo(9f, 6f)
                lineTo(9f, 18f)
                moveTo(15f, 6f)
                lineTo(15f, 18f)
            }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            strokePath {
                moveTo(9f, 14f)
                lineTo(4f, 9f)
                lineTo(9f, 4f)
                moveTo(4f, 9f)
                lineTo(14.5f, 9f)
                curveTo(17.5f, 9f, 20f, 11.2f, 20f, 14f)
                curveTo(20f, 16.8f, 17.5f, 19f, 14.5f, 19f)
                lineTo(11f, 19f)
            }
        }
    }

    val Redo: ImageVector by lazy {
        icon("Redo") {
            strokePath {
                moveTo(15f, 14f)
                lineTo(20f, 9f)
                lineTo(15f, 4f)
                moveTo(20f, 9f)
                lineTo(9.5f, 9f)
                curveTo(6.5f, 9f, 4f, 11.2f, 4f, 14f)
                curveTo(4f, 16.8f, 6.5f, 19f, 9.5f, 19f)
                lineTo(13f, 19f)
            }
        }
    }

    val Pen: ImageVector by lazy {
        icon("Pen") {
            strokePath {
                moveTo(17.5f, 3.5f)
                lineTo(20.5f, 6.5f)
                lineTo(8f, 19f)
                lineTo(4f, 20f)
                lineTo(5f, 16f)
                close()
                moveTo(14.5f, 6.5f)
                lineTo(17.5f, 9.5f)
            }
        }
    }

    val Pencil: ImageVector by lazy {
        icon("Pencil") {
            strokePath {
                moveTo(4f, 20f)
                lineTo(9f, 19f)
                lineTo(19.5f, 8.5f)
                lineTo(15.5f, 4.5f)
                lineTo(5f, 15f)
                close()
                moveTo(13.5f, 6.5f)
                lineTo(17.5f, 10.5f)
                moveTo(5f, 15f)
                lineTo(9f, 19f)
            }
        }
    }

    val Marker: ImageVector by lazy {
        icon("Marker") {
            strokePath {
                moveTo(7f, 17f)
                lineTo(4f, 20f)
                moveTo(6f, 14f)
                lineTo(14f, 6f)
                lineTo(18f, 10f)
                lineTo(10f, 18f)
                close()
                moveTo(15f, 5f)
                lineTo(17f, 3f)
                lineTo(21f, 7f)
                lineTo(19f, 9f)
            }
        }
    }

    val Highlighter: ImageVector by lazy {
        icon("Highlighter") {
            strokePath {
                moveTo(4f, 19f)
                lineTo(13f, 19f)
                moveTo(8f, 15f)
                lineTo(16f, 7f)
                lineTo(19f, 10f)
                lineTo(11f, 18f)
                lineTo(7f, 18f)
                close()
            }
        }
    }

    val Eraser: ImageVector by lazy {
        icon("Eraser") {
            strokePath {
                moveTo(7f, 20f)
                lineTo(20f, 20f)
                moveTo(4.5f, 14.5f)
                lineTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(11.5f, 19.5f)
                lineTo(7.5f, 19.5f)
                lineTo(4.5f, 16.5f)
                close()
            }
        }
    }

    val Bucket: ImageVector by lazy {
        icon("Bucket") {
            strokePath {
                moveTo(19f, 11f)
                lineTo(11f, 3f)
                lineTo(3.5f, 10.5f)
                lineTo(11.5f, 18.5f)
                close()
                moveTo(7f, 7f)
                lineTo(13f, 13f)
                moveTo(20f, 16f)
                curveTo(20f, 16f, 22f, 18.2f, 22f, 19.5f)
                curveTo(22f, 20.6f, 21.1f, 21.5f, 20f, 21.5f)
                curveTo(18.9f, 21.5f, 18f, 20.6f, 18f, 19.5f)
                curveTo(18f, 18.2f, 20f, 16f, 20f, 16f)
            }
        }
    }

    val Shapes: ImageVector by lazy {
        icon("Shapes") {
            strokePath {
                moveTo(12f, 4f)
                lineTo(19f, 17f)
                lineTo(5f, 17f)
                close()
                moveTo(15f, 14f)
                moveTo(19.5f, 16.5f)
            }
            strokePath {
                moveTo(17f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 9f)
                lineTo(17f, 9f)
                close()
            }
        }
    }

    val Lasso: ImageVector by lazy {
        icon("Lasso") {
            strokePath {
                moveTo(6f, 9f)
                curveTo(6f, 5.5f, 10f, 4f, 14f, 5f)
                curveTo(18f, 6f, 20f, 9.5f, 18f, 13f)
                curveTo(16f, 16f, 10f, 16.5f, 7f, 14f)
                moveTo(7f, 14f)
                lineTo(5f, 20f)
                moveTo(9f, 15.5f)
                circle(0f)
            }
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(dummy: Float) {
        // helper unused; keep standard path ops
    }

    val SparkleAi: ImageVector by lazy {
        icon("SparkleAi") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(13.8f, 8.6f)
                lineTo(19.5f, 10.5f)
                lineTo(13.8f, 12.4f)
                lineTo(12f, 18f)
                lineTo(10.2f, 12.4f)
                lineTo(4.5f, 10.5f)
                lineTo(10.2f, 8.6f)
                close()
                moveTo(18.5f, 16.5f)
                lineTo(19.2f, 18.3f)
                lineTo(21f, 19f)
                lineTo(19.2f, 19.7f)
                lineTo(18.5f, 21.5f)
                lineTo(17.8f, 19.7f)
                lineTo(16f, 19f)
                lineTo(17.8f, 18.3f)
                close()
            }
        }
    }

    val OnionSkin: ImageVector by lazy {
        icon("OnionSkin") {
            strokePath {
                moveTo(12f, 5f)
                curveTo(8.5f, 5f, 6f, 8f, 6f, 12f)
                curveTo(6f, 16f, 8.5f, 19f, 12f, 19f)
                curveTo(15.5f, 19f, 18f, 16f, 18f, 12f)
                curveTo(18f, 8f, 15.5f, 5f, 12f, 5f)
                moveTo(7.5f, 7.5f)
                curveTo(4.8f, 8.6f, 3.5f, 11f, 4f, 14f)
                moveTo(16.5f, 7.5f)
                curveTo(19.2f, 8.6f, 20.5f, 11f, 20f, 14f)
            }
        }
    }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            strokePath {
                moveTo(12f, 4f)
                lineTo(20f, 8.5f)
                lineTo(12f, 13f)
                lineTo(4f, 8.5f)
                close()
                moveTo(4f, 12.5f)
                lineTo(12f, 17f)
                lineTo(20f, 12.5f)
                moveTo(4f, 16.5f)
                lineTo(12f, 21f)
                lineTo(20f, 16.5f)
            }
        }
    }

    val Export: ImageVector by lazy {
        icon("Export") {
            strokePath {
                moveTo(12f, 15f)
                lineTo(12f, 4f)
                moveTo(7.5f, 8.5f)
                lineTo(12f, 4f)
                lineTo(16.5f, 8.5f)
                moveTo(4f, 15f)
                lineTo(4f, 19f)
                lineTo(20f, 19f)
                lineTo(20f, 15f)
            }
        }
    }

    val Focus: ImageVector by lazy {
        icon("Focus") {
            strokePath {
                moveTo(4f, 9f)
                lineTo(4f, 4f)
                lineTo(9f, 4f)
                moveTo(15f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 9f)
                moveTo(20f, 15f)
                lineTo(20f, 20f)
                lineTo(15f, 20f)
                moveTo(9f, 20f)
                lineTo(4f, 20f)
                lineTo(4f, 15f)
            }
        }
    }

    val Duplicate: ImageVector by lazy {
        icon("Duplicate") {
            strokePath {
                moveTo(9f, 9f)
                lineTo(20f, 9f)
                lineTo(20f, 20f)
                lineTo(9f, 20f)
                close()
                moveTo(15f, 9f)
                lineTo(15f, 4f)
                lineTo(4f, 4f)
                lineTo(4f, 15f)
                lineTo(9f, 15f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            strokePath {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(9f, 7f)
                lineTo(9f, 4f)
                lineTo(15f, 4f)
                lineTo(15f, 7f)
                moveTo(6f, 7f)
                lineTo(7f, 20f)
                lineTo(17f, 20f)
                lineTo(18f, 7f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        icon("Grid") {
            strokePath {
                moveTo(9f, 4f)
                lineTo(9f, 20f)
                moveTo(15f, 4f)
                lineTo(15f, 20f)
                moveTo(4f, 9f)
                lineTo(20f, 9f)
                moveTo(4f, 15f)
                lineTo(20f, 15f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            strokePath {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
                moveTo(9f, 4f)
                lineTo(9f, 10f)
                moveTo(15f, 14f)
                lineTo(15f, 20f)
            }
        }
    }

    val EyeOpen: ImageVector by lazy {
        icon("EyeOpen") {
            strokePath {
                moveTo(3f, 12f)
                curveTo(5.5f, 7.5f, 9f, 6f, 12f, 6f)
                curveTo(15f, 6f, 18.5f, 7.5f, 21f, 12f)
                curveTo(18.5f, 16.5f, 15f, 18f, 12f, 18f)
                curveTo(9f, 18f, 5.5f, 16.5f, 3f, 12f)
                moveTo(12f, 10f)
                lineTo(12f, 14f)
            }
        }
    }
}
