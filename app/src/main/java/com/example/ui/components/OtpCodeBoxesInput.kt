package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val OTP_CODE_LENGTH = 6

@Composable
fun OtpCodeBoxesInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val normalizedValue = remember(value) {
        value.filter { it.isDigit() }.take(OTP_CODE_LENGTH)
    }
    var isFocused by remember { mutableStateOf(false) }

    val cursorTransition = rememberInfiniteTransition(label = "otp_cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "otp_cursor_alpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .paddingBottomSpacing()
        )

        Spacer(modifier = Modifier.height(6.dp))

        BasicTextField(
            value = normalizedValue,
            onValueChange = { input ->
                onValueChange(input.filter { it.isDigit() }.take(OTP_CODE_LENGTH))
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .testTag(testTag),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Keep innerTextField mounted at zero visual size so IME, paste, autofill, and Compose tests attach cleanly.
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                    ) {
                        innerTextField()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(OTP_CODE_LENGTH) { index ->
                            val digit = normalizedValue.getOrNull(index)
                            val isFilled = digit != null
                            val isActiveBox = isFocused && (
                                index == normalizedValue.length ||
                                    (normalizedValue.length == OTP_CODE_LENGTH && index == OTP_CODE_LENGTH - 1)
                                )

                            val containerColor = when {
                                isActiveBox || isFilled -> MaterialTheme.colorScheme.surface
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                            val border = when {
                                isActiveBox -> BorderStroke(2.dp, accentColor)
                                isFilled -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline)
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                border = border,
                                tonalElevation = if (isActiveBox) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("${testTag}_box_$index")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when {
                                        digit != null -> {
                                            Text(
                                                text = digit.toString(),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        isActiveBox -> {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(22.dp)
                                                    .alpha(cursorAlpha)
                                                    .background(accentColor, RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun Modifier.paddingBottomSpacing(): Modifier = this
