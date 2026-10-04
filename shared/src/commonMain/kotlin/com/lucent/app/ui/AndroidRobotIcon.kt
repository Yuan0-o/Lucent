package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AndroidRobotIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "AndroidRobot",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = ink,
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8.0f, 2.5f); lineTo(9.7f, 5.2f)
            moveTo(16.0f, 2.5f); lineTo(14.3f, 5.2f)
        }

        path(fill = ink, pathFillType = PathFillType.EvenOdd) {
            moveTo(5.8f, 10.0f)
            arcTo(6.2f, 5.0f, 0f, false, true, 18.2f, 10.0f)
            close()
            moveTo(10.6f, 7.9f)
            arcTo(0.95f, 0.95f, 0f, false, true, 8.7f, 7.9f)
            arcTo(0.95f, 0.95f, 0f, false, true, 10.6f, 7.9f)
            close()
            moveTo(15.3f, 7.9f)
            arcTo(0.95f, 0.95f, 0f, false, true, 13.4f, 7.9f)
            arcTo(0.95f, 0.95f, 0f, false, true, 15.3f, 7.9f)
            close()
        }

        path(fill = ink, pathFillType = PathFillType.NonZero, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6.2f, 11.2f)
            lineTo(17.8f, 11.2f)
            lineTo(17.8f, 18.0f)
            arcTo(1.5f, 1.5f, 0f, false, true, 16.3f, 19.5f)
            lineTo(7.7f, 19.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6.2f, 18.0f)
            close()
        }

        path(
            stroke = ink,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4.35f, 12.4f); lineTo(4.35f, 16.6f)
            moveTo(19.65f, 12.4f); lineTo(19.65f, 16.6f)
        }
    }.build()
}
