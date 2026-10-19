package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Item
import com.example.ui.theme.SuccessGreen

@Composable
fun MultiUnitConverterDialog(
    item: Item,
    onDismiss: () -> Unit,
    onSavePackaging: ((secondaryUnit: String, conversionFactor: Double, secondaryPrice: Double) -> Unit)? = null
) {
    val primaryUnit = item.unit.ifBlank { "pcs" }
    var secondaryUnit by remember { mutableStateOf(item.secondaryUnit.ifBlank { "Box" }) }
    var factorStr by remember { mutableStateOf(if (item.conversionFactor > 1) item.conversionFactor.toInt().toString() else "24") }
    var secondaryPriceStr by remember {
        mutableStateOf(
            if (item.secondaryPrice > 0) item.secondaryPrice.toString()
            else if (item.price > 0 && item.conversionFactor > 1) (item.price * item.conversionFactor).toString()
            else ""
        )
    }

    // Live Converter inputs
    var calcBoxesInput by remember { mutableStateOf("1") }
    var calcPiecesInput by remember { mutableStateOf("") }
    var calcDirection by remember { mutableStateOf("box_to_pieces") } // "box_to_pieces" or "pieces_to_box"

    val factor = factorStr.toDoubleOrNull() ?: 1.0

    // Compute live conversions
    val piecesFromBoxes = (calcBoxesInput.toDoubleOrNull() ?: 0.0) * factor
    val totalLooseInput = calcPiecesInput.toDoubleOrNull() ?: 0.0
    val wholeBoxes = if (factor > 0) (totalLooseInput / factor).toLong() else 0
    val remainderPieces = if (factor > 0) (totalLooseInput % factor) else 0.0

    val presetSecondaryUnits = listOf("Box", "Carton", "Bundle", "Drum", "Pack", "Bag", "Roll", "Dozen")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Packaging & Unit Converter", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(item.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Packaging Setup Card
                Text("1. PACKAGING SPECIFICATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = secondaryUnit,
                        onValueChange = { secondaryUnit = it },
                        label = { Text("Secondary Unit") },
                        placeholder = { Text("e.g. Box, Carton") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = factorStr,
                        onValueChange = { factorStr = it },
                        label = { Text("Qty / $secondaryUnit") },
                        placeholder = { Text("e.g. 24") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                // Quick chip presets for packaging unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetSecondaryUnits.take(5).forEach { unitPreset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (secondaryUnit.equals(unitPreset, ignoreCase = true)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                secondaryUnit = unitPreset
                                if (unitPreset == "Dozen") factorStr = "12"
                            }
                        ) {
                            Text(
                                text = unitPreset,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (secondaryUnit.equals(unitPreset, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = secondaryPriceStr,
                    onValueChange = { secondaryPriceStr = it },
                    label = { Text("Price per $secondaryUnit (₹)") },
                    placeholder = { Text("Wholesale packaging price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Live Two-Way Calculator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. LIVE PACKAGING CONVERTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Converter Toggle Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (calcDirection == "box_to_pieces") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (calcDirection == "box_to_pieces") BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { calcDirection = "box_to_pieces" }
                    ) {
                        Text(
                            text = "$secondaryUnit ➔ $primaryUnit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calcDirection == "box_to_pieces") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (calcDirection == "pieces_to_box") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (calcDirection == "pieces_to_box") BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { calcDirection = "pieces_to_box" }
                    ) {
                        Text(
                            text = "$primaryUnit ➔ $secondaryUnit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calcDirection == "pieces_to_box") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (calcDirection == "box_to_pieces") {
                    OutlinedTextField(
                        value = calcBoxesInput,
                        onValueChange = { calcBoxesInput = it },
                        label = { Text("Enter Number of ${secondaryUnit}s") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CONVERTED PIECES:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Text(
                                text = "= ${piecesFromBoxes.toInt()} $primaryUnit",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SuccessGreen
                            )
                            val secPrice = secondaryPriceStr.toDoubleOrNull() ?: (item.price * factor)
                            if (secPrice > 0) {
                                Text(
                                    text = "Estimated Amount: ₹${"%.0f".format((calcBoxesInput.toDoubleOrNull() ?: 0.0) * secPrice)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = calcPiecesInput,
                        onValueChange = { calcPiecesInput = it },
                        label = { Text("Enter Total Loose $primaryUnit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("PACKAGING BREAKDOWN:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "= $wholeBoxes ${secondaryUnit}(s) + ${remainderPieces.toInt()} loose $primaryUnit",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Total Stock in Multi-Unit
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val stockBoxes = if (factor > 0) (item.qty / factor).toLong() else 0
                    val stockRemainder = if (factor > 0) (item.qty % factor).toInt() else 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Inventory:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${item.qty.toInt()} $primaryUnit ($stockBoxes $secondaryUnit + $stockRemainder $primaryUnit)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (onSavePackaging != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val f = factorStr.toDoubleOrNull() ?: 1.0
                            val secP = secondaryPriceStr.toDoubleOrNull() ?: 0.0
                            onSavePackaging(secondaryUnit.trim(), f, secP)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Packaging Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
