package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Intelligent auto-resizing text component that smoothly scales down font size
 * dynamically when content exceeds the container width/height, preventing
 * harsh truncation and ellipsis (...) across various device sizes.
 */
@Composable
fun AutoResizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    minFontSize: TextUnit = 9.sp,
    targetFontSize: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 12.sp,
    step: TextUnit = 0.8.sp,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true
) {
    var resizedFontSize by remember(text, targetFontSize) { mutableStateOf(targetFontSize) }
    var readyToDraw by remember(text, targetFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        style = style.copy(fontSize = resizedFontSize),
        onTextLayout = { result ->
            if (result.didOverflowWidth || result.didOverflowHeight) {
                if (resizedFontSize.value - step.value >= minFontSize.value) {
                    resizedFontSize = (resizedFontSize.value - step.value).sp
                } else {
                    resizedFontSize = minFontSize
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
