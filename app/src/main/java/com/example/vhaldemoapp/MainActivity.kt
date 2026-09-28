package com.example.vhaldemoapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vhaldemoapp.model.VehicleUiState
import com.example.vhaldemoapp.service.CarPropertyMonitoringService
import com.example.vhaldemoapp.ui.VhalViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlin.math.max

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, CarPropertyMonitoringService::class.java))
        enableEdgeToEdge()
        setContent {
            val darkColorScheme = darkColorScheme(
                primary = Color(0xFF6366F1),
                secondary = Color(0xFF10B981),
                background = Color(0xFF0F172A),
                surface = Color(0xFF1E293B),
                surfaceVariant = Color(0xFF334155),
            )
            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VhalDemoScreen()
                }
            }
        }
    }
}

@Composable
private fun VhalDemoScreen(viewModel: VhalViewModel = hiltViewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Full Width Header
        HeaderSection(state)

        // Main 2-Column Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Side: Speedometer Gauge Card
            SpeedometerCard(
                speedKmh = state.speedKmh ?: 0f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )

            // Right Side: Telemetry Grid & Climate Controls
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Row: Fuel/Energy + Gear Side by Side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EnergyLevelCard(
                        fuelLevelPercent = state.fuelLevelPercent ?: 0f,
                        modifier = Modifier.weight(1f)
                    )
                    GearCard(
                        gearSelection = state.gearSelection,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Row: Climate Control
                ClimateControlCard(
                    state = state,
                    onIncreaseFan = viewModel::increaseFanSpeed,
                    onDecreaseFan = viewModel::decreaseFanSpeed,
                    onMaxFan = viewModel::setMaxFanSpeed,
                    onTogglePower = viewModel::toggleHvacPower,
                    onToggleAc = viewModel::toggleAc,
                    onToggleAuto = viewModel::toggleAutoClimate,
                    onIncreaseTemp = viewModel::increaseTemperature,
                    onDecreaseTemp = viewModel::decreaseTemperature,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ $error",
                    color = Color.White,
                    modifier = Modifier.padding(6.dp),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun HeaderSection(state: VehicleUiState) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ROADSYNC AUTOMOTIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.2.sp
                )
                state.carInfo?.let { info ->
                    Text(
                        text = "${info.make} ${info.model} (${info.year})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: Text(
                    text = "Vehicle Connecting...",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (state.isCarConnected) Color(0xFF065F46) else Color(0xFF7F1D1D)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.isCarConnected) Color(0xFF34D399) else Color(0xFFF87171))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isCarConnected) "VHAL ONLINE" else "DISCONNECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SpeedometerCard(speedKmh: Float, modifier: Modifier = Modifier) {
    var displaySpeed by remember { mutableFloatStateOf(0f) }

    val safeTargetSpeed = speedKmh.coerceAtLeast(0f)

    LaunchedEffect(safeTargetSpeed) {
        while (abs(safeTargetSpeed - displaySpeed) >= 0.5f) {
            val step = if (safeTargetSpeed > displaySpeed) 1f else -1f
            // Clamp displaySpeed so it never steps below 0f
            displaySpeed = (displaySpeed + step).coerceAtLeast(0f)
            delay(15.milliseconds)
        }
        displaySpeed = safeTargetSpeed
    }

    val gaugeColor by animateColorAsState(
        targetValue = when {
            displaySpeed < 60f -> Color(0xFF3B82F6)   // Blue
            displaySpeed < 120f -> Color(0xFF6366F1)  // Indigo
            displaySpeed < 180f -> Color(0xFFF59E0B)  // Orange
            else -> Color(0xFFEF4444)                  // Red
        },
        animationSpec = tween(durationMillis = 300),
        label = "gaugeColor"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val progress = (displaySpeed / 240f).coerceIn(0f, 1f)

            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val strokeWidth = 14.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    x = (size.width - diameter) / 2f,
                    y = (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = Color(0xFF334155),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawArc(
                    color = gaugeColor,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Speed Numeric Display (Integer rounded & absolute value to prevent IEEE 754 -0)
            val speedDisplayInt = max(0, abs(displaySpeed).roundToInt())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$speedDisplayInt",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "KM/H",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun EnergyLevelCard(fuelLevelPercent: Float, modifier: Modifier = Modifier) {
    val displayPercent = if (fuelLevelPercent > 100f) 100f else fuelLevelPercent.coerceAtLeast(0f)
    val progress = (displayPercent / 100f).coerceIn(0f, 1f)
    val color = when {
        displayPercent > 30f -> Color(0xFF10B981)
        displayPercent > 15f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ ENERGY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("%.1f%%".format(displayPercent), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = color,
                trackColor = Color(0xFF334155),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun GearCard(gearSelection: Int?, modifier: Modifier = Modifier) {
    val gearName = when (gearSelection) {
        4 -> "P"
        8 -> "D"
        2 -> "R"
        1 -> "N"
        else -> gearSelection?.toString() ?: "-"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚙️ GEAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("P", "R", "N", "D").forEach { gear ->
                    val isSelected = gear == gearName
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF6366F1) else Color(0xFF334155))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = gear,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClimateControlCard(
    state: VehicleUiState,
    onIncreaseFan: () -> Unit,
    onDecreaseFan: () -> Unit,
    onMaxFan: () -> Unit,
    onTogglePower: () -> Unit,
    onToggleAc: () -> Unit,
    onToggleAuto: () -> Unit,
    onIncreaseTemp: () -> Unit,
    onDecreaseTemp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFan = state.fanSpeed ?: 1
    val isConnected = state.isCarConnected

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Header + Power + A/C + AUTO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("❄️ CLIMATE CONTROL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onTogglePower,
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isHvacPowerOn) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("POWER", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onToggleAc,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isAcOn) Color(0xFF0284C7) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("A/C", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onToggleAuto,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isAutoClimateOn) Color(0xFF8B5CF6) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Row 2: Temperature Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEMP: ${"%.1f°C".format(state.hvacTemperatureC ?: 21.5f)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onDecreaseTemp,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Temp -", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onIncreaseTemp,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Temp +", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Row 3: Fan Speed Controls & Level Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    (1..state.maxFanSpeed).forEach { level ->
                        val isActive = level <= currentFan && state.isHvacPowerOn
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive) Color(0xFF38BDF8) else Color(0xFF334155))
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FAN $currentFan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onDecreaseFan,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Fan -", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onIncreaseFan,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Fan +", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onMaxFan,
                        enabled = isConnected && state.isHvacPowerOn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("MAX", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
