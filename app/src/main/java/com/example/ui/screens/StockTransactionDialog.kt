package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Item
import com.example.ui.StockViewModel
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun StockTransactionDialog(
    item: Item,
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    var action by remember { mutableStateOf("in") } // "in" or "out"
    var qtyText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var negativeWarningAcknowledged by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                action = "in"
                                negativeWarningAcknowledged = false
                            }
                            .background(if (action == "in") SuccessGreen else Color.Transparent)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Stock in",
                            fontWeight = FontWeight.Bold,
                            color = if (action == "in") Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                action = "out"
                                negativeWarningAcknowledged = false
                            }
                            .background(if (action == "out") DangerRed else Color.Transparent)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Stock out",
                            fontWeight = FontWeight.Bold,
                            color = if (action == "out") Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val curQtyStr = if (item.qty % 1.0 == 0.0) item.qty.toLong().toString() else "%.1f".format(item.qty)
                Text(
                    text = "Current stock: $curQtyStr ${item.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                val hasSecondary = !item.secondaryUnit.isNullOrBlank() && item.conversionFactor > 1.0
                var selectedUnitMode by remember { mutableStateOf("primary") } // "primary" or "secondary"

                if (hasSecondary) {
                    val secUnit = item.secondaryUnit!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedUnitMode == "primary") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedUnitMode = "primary" }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = item.unit.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (selectedUnitMode == "primary") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedUnitMode == "secondary") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedUnitMode = "secondary" }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "$secUnit (x${item.conversionFactor.toInt()})".uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (selectedUnitMode == "secondary") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                val activeUnitLabel = if (hasSecondary && selectedUnitMode == "secondary") item.secondaryUnit!! else item.unit

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it
                        negativeWarningAcknowledged = false
                    },
                    label = { Text("Quantity ($activeUnitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasSecondary && selectedUnitMode == "secondary" && (qtyText.toDoubleOrNull() ?: 0.0) > 0.0) {
                    val rawVal = qtyText.toDoubleOrNull() ?: 0.0
                    val convertedPcs = rawVal * item.conversionFactor
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 $rawVal ${item.secondaryUnit} = ${if (convertedPcs % 1.0 == 0.0) convertedPcs.toInt().toString() else "%.1f".format(convertedPcs)} ${item.unit}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (supplier, customer, bill no…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (action == "out" && (qtyText.toDoubleOrNull() ?: 0.0) > item.qty && !negativeWarningAcknowledged) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Only $curQtyStr in stock. Tap Confirm again to go negative.",
                        color = DangerRed,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Button(
                        onClick = {
                            val enteredQty = qtyText.trim().toDoubleOrNull()
                            if (enteredQty == null || enteredQty <= 0.0) {
                                viewModel.showToast("Enter a valid quantity")
                                return@Button
                            }
                            val effectiveQty = if (hasSecondary && selectedUnitMode == "secondary") {
                                enteredQty * item.conversionFactor
                            } else {
                                enteredQty
                            }
                            if (action == "out" && effectiveQty > item.qty && !negativeWarningAcknowledged) {
                                negativeWarningAcknowledged = true
                                return@Button
                            }
                            val finalNote = if (hasSecondary && selectedUnitMode == "secondary") {
                                val unitNote = "[$enteredQty ${item.secondaryUnit}]"
                                if (note.isNotBlank()) "$unitNote ${note.trim()}" else unitNote
                            } else {
                                note.trim()
                            }
                            viewModel.performTransaction(item.id, action, effectiveQty, finalNote)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (action == "in") SuccessGreen else DangerRed
                        )
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
