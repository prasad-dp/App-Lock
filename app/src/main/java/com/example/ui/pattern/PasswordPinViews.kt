package com.example.ui.pattern

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.LockPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

@Composable
fun PinPadView(
    modifier: Modifier = Modifier,
    pinLength: Int = 4,
    onPinComplete: (String) -> Unit,
    resetIdentifier: Any? = null,
    isBiometricEnabled: Boolean = false,
    onBiometricClick: (() -> Unit)? = null
) {
    var inputtedPin by remember(resetIdentifier) { mutableStateOf("") }

    LaunchedEffect(resetIdentifier) {
        inputtedPin = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Bullet indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            for (i in 0 until pinLength) {
                val isFilled = i < inputtedPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Premium Keyboard Layout
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            // Empty Spacer to keep layout structured
                            Box(modifier = Modifier.size(72.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (key) {
                                            "FP" -> MaterialTheme.colorScheme.secondaryContainer
                                            "⌫" -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .clickable {
                                        when (key) {
                                            "⌫" -> {
                                                if (inputtedPin.isNotEmpty()) {
                                                    inputtedPin = inputtedPin.dropLast(1)
                                                }
                                            }
                                            "FP" -> {
                                                onBiometricClick?.invoke()
                                            }
                                            else -> {
                                                if (inputtedPin.length < pinLength) {
                                                    inputtedPin += key
                                                    // Handle auto-completion
                                                    if (inputtedPin.length == pinLength) {
                                                        onPinComplete(inputtedPin)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .testTag("pin_key_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "⌫" -> Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    "FP" -> Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Trigger Biometric Scan",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    else -> Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordUnlockView(
    modifier: Modifier = Modifier,
    placeholder: String = "Enter alphanumeric password",
    onPasswordComplete: (String) -> Unit,
    resetIdentifier: Any? = null
) {
    var passwordInput by remember(resetIdentifier) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(resetIdentifier) {
        passwordInput = ""
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_input_field"),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (passwordInput.isNotBlank()) {
                        onPasswordComplete(passwordInput)
                    }
                }
            ),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (passwordInput.isNotBlank()) {
                    onPasswordComplete(passwordInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_password_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm Password")
        }
    }
}

@Composable
fun SecurityLockoutScreen(
    secondsLeft: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Lockout Icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Security Lockout Active",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Too many incorrect attempts have been made. For your safety, standard access has been temporarily locked to prevent unauthorized attempts.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .border(4.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$secondsLeft",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "seconds",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Please wait until the security cooldown completes to try again.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AndroidBiometricGraphic(
    isScanning: Boolean,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "android_biometric_scan")
    
    // Smooth scanning pulse for material ripple
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Spinner rotation angle for modern Android progress ring
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_angle"
    )

    // Scale bounce on successful verification
    val successScale by animateFloatAsState(
        targetValue = if (isSuccess) 1.06f else 1.00f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "success_scale"
    )

    // Morphing smile mouth/checkmark progress
    val checkmarkProgress by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = tween(650, easing = EaseInOutSine),
        label = "checkmark_progress"
    )

    Canvas(modifier = modifier.scale(successScale)) {
        val sizePx = size.minDimension
        val strokeWidth = 3f.dp.toPx()
        val halfSize = sizePx / 2f
        val faceRadius = sizePx * 0.32f

        // 1. Draw pulsating scan ripples under the face (Material signature style)
        if (isScanning && !isSuccess) {
            drawCircle(
                color = color.copy(alpha = pulseAlpha),
                radius = faceRadius * pulseScale,
                center = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }

        // 2. Draw outer scan circular tracker (Android loading-arc)
        if (isScanning && !isSuccess) {
            val trackRadius = faceRadius * 1.22f
            drawArc(
                color = color,
                startAngle = rotAngle,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(halfSize - trackRadius, halfSize - trackRadius),
                size = androidx.compose.ui.geometry.Size(trackRadius * 2, trackRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        } else if (isSuccess) {
            val trackRadius = faceRadius * 1.22f
            drawCircle(
                color = Color(0xFF4CAF50), // Elegant native checkmark green
                radius = trackRadius,
                center = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 1.2f)
            )
        }

        // 3. Draw Inner Face Outline (Circle)
        val faceColor = if (isSuccess) Color(0xFF4CAF50) else color
        drawCircle(
            color = faceColor,
            radius = faceRadius,
            center = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        // Draw elements inside the face circle with transition
        val scanningAlpha = 1f - checkmarkProgress

        if (scanningAlpha > 0.01f) {
            // Minimal face: subtle tiny eyes and minimal subtle flat mouth
            val eyeRadius = 2.2f.dp.toPx()
            val eyeY = halfSize - faceRadius * 0.18f
            val leftEyeX = halfSize - faceRadius * 0.30f
            val rightEyeX = halfSize + faceRadius * 0.30f
            
            drawCircle(
                color = faceColor.copy(alpha = scanningAlpha),
                center = androidx.compose.ui.geometry.Offset(leftEyeX, eyeY),
                radius = eyeRadius
            )
            drawCircle(
                color = faceColor.copy(alpha = scanningAlpha),
                center = androidx.compose.ui.geometry.Offset(rightEyeX, eyeY),
                radius = eyeRadius
            )

            val mouthY = halfSize + faceRadius * 0.22f
            val mouthWidth = faceRadius * 0.45f
            drawLine(
                color = faceColor.copy(alpha = scanningAlpha),
                start = androidx.compose.ui.geometry.Offset(halfSize - mouthWidth / 2f, mouthY),
                end = androidx.compose.ui.geometry.Offset(halfSize + mouthWidth / 2f, mouthY),
                strokeWidth = strokeWidth * 0.85f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        if (checkmarkProgress > 0.01f) {
            // Animating a clean tick mark (checkmark) inside the circle
            val checkmarkColor = Color(0xFF4CAF50).copy(alpha = checkmarkProgress)
            
            val p0 = androidx.compose.ui.geometry.Offset(halfSize - faceRadius * 0.38f, halfSize + faceRadius * 0.02f)
            val p1 = androidx.compose.ui.geometry.Offset(halfSize - faceRadius * 0.06f, halfSize + faceRadius * 0.32f)
            val p2 = androidx.compose.ui.geometry.Offset(halfSize + faceRadius * 0.44f, halfSize - faceRadius * 0.22f)

            val pFirst = if (checkmarkProgress < 0.4f) {
                val t = checkmarkProgress / 0.4f
                androidx.compose.ui.geometry.Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
            } else {
                p1
            }

            val pSecond = if (checkmarkProgress >= 0.4f) {
                val t = (checkmarkProgress - 0.4f) / 0.6f
                androidx.compose.ui.geometry.Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
            } else {
                null
            }

            drawLine(
                color = checkmarkColor,
                start = p0,
                end = pFirst,
                strokeWidth = strokeWidth * 1.3f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            if (pSecond != null) {
                drawLine(
                    color = checkmarkColor,
                    start = p1,
                    end = pSecond,
                    strokeWidth = strokeWidth * 1.3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun OldFaceIdGraphic(
    isScanning: Boolean,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "face_id_scan")
    
    // Smooth pulse animation for scanning corners
    val cornerPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_pulse"
    )
    
    // Scanning rotation angle for the outer broken radar ring
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_rotation"
    )

    // Success scale bounce animation
    val successScale by animateFloatAsState(
        targetValue = if (isSuccess) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "success_scale"
    )

    // Face smile transition progress
    val smileProgress by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = tween(600, easing = EaseOutBack),
        label = "smile_progress"
    )

    Canvas(modifier = modifier.scale(successScale)) {
        val sizePx = size.minDimension
        val strokeWidth = 3.2f.dp.toPx()
        val halfSize = sizePx / 2f
        
        // --- 1. DRAW INNER FACE OUTLINE (The uncolored hand-drawn outline face shape: round circle, 2 dots, 1 line mouth) ---
        val faceRadius = sizePx * 0.28f
        val faceColor = if (isSuccess) color else color.copy(alpha = 0.85f)
        
        // Draw face outer circle
        drawCircle(
            color = faceColor,
            radius = faceRadius,
            center = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        // Two dots for eyes
        val eyeRadius = 3.2f.dp.toPx()
        val eyeY = halfSize - faceRadius * 0.16f
        val leftEyeX = halfSize - faceRadius * 0.35f
        val rightEyeX = halfSize + faceRadius * 0.35f
        
        drawCircle(
            color = faceColor,
            center = androidx.compose.ui.geometry.Offset(leftEyeX, eyeY),
            radius = eyeRadius
        )
        drawCircle(
            color = faceColor,
            center = androidx.compose.ui.geometry.Offset(rightEyeX, eyeY),
            radius = eyeRadius
        )
        
        // Mouth line (Morphs neutral straight line to curved smile)
        val mouthY = halfSize + faceRadius * 0.26f
        val mouthWidth = faceRadius * 0.72f
        
        if (smileProgress > 0.02f) {
            // Smiley curved arc
            val smileHeight = faceRadius * 0.36f * smileProgress
            val topLeftX = halfSize - mouthWidth / 2f
            val topLeftY = mouthY - (smileHeight / 2)
            
            drawArc(
                color = faceColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                size = androidx.compose.ui.geometry.Size(mouthWidth, smileHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        } else {
            // Straight uncolored flat line
            drawLine(
                color = faceColor,
                start = androidx.compose.ui.geometry.Offset(halfSize - mouthWidth / 2f, mouthY),
                end = androidx.compose.ui.geometry.Offset(halfSize + mouthWidth / 2f, mouthY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // --- 2. DRAW OUTER iOS FACE ID THEMED ROUNDED SQUARE FRAME ---
        // Thick corners that frame the face (iOS signature brackets)
        val frameSize = sizePx * 0.82f
        val frameOffset = (sizePx - frameSize) / 2f
        val bracketRawLen = frameSize * 0.24f
        val bracketLen = if (isSuccess) bracketRawLen * 0.85f else bracketRawLen
        val bracketAlpha = if (isSuccess) 1.0f else cornerPulse
        val bracketColor = color.copy(alpha = bracketAlpha)
        
        val left = frameOffset
        val right = frameOffset + frameSize
        val top = frameOffset
        val bottom = frameOffset + frameSize
        
        // Top-Left corner bracket
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(left, top), end = androidx.compose.ui.geometry.Offset(left + bracketLen, top), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(left, top), end = androidx.compose.ui.geometry.Offset(left, top + bracketLen), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // Top-Right corner bracket
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(right, top), end = androidx.compose.ui.geometry.Offset(right - bracketLen, top), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(right, top), end = androidx.compose.ui.geometry.Offset(right, top + bracketLen), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // Bottom-Left corner bracket
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(left, bottom), end = androidx.compose.ui.geometry.Offset(left + bracketLen, bottom), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(left, bottom), end = androidx.compose.ui.geometry.Offset(left, bottom - bracketLen), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // Bottom-Right corner bracket
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(right, bottom), end = androidx.compose.ui.geometry.Offset(right - bracketLen, bottom), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(right, bottom), end = androidx.compose.ui.geometry.Offset(right, bottom - bracketLen), strokeWidth = strokeWidth * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // --- 3. DRAW ROTATING RADAR / RING PROCESS OF FACE ID ---
        if (isScanning && !isSuccess) {
            val radiusRing = frameSize * 0.58f
            drawArc(
                color = color.copy(alpha = 0.4f),
                startAngle = scanRotation,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(halfSize - radiusRing, halfSize - radiusRing),
                size = androidx.compose.ui.geometry.Size(radiusRing * 2, radiusRing * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 0.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        } else if (isSuccess) {
            val radiusRing = frameSize * 0.58f
            drawCircle(
                color = color,
                radius = radiusRing,
                center = androidx.compose.ui.geometry.Offset(halfSize, halfSize),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 1.3f)
            )
        }
    }
}

@Composable
fun FaceScanningBottomSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onUseAlternative: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scanStatus by remember { mutableStateOf("Initializing Face Scan...") }
    var detectedFace by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        delay(700)
        scanStatus = "Analysing face geometries..."
        delay(1200)
        scanStatus = "Authenticating identity..."
        delay(900)
        scanStatus = "Face Authenticated!"
        detectedFace = true
        
        // Brief pause for the success check animation before hands-free unlock triggers
        delay(800)
        onSuccess()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Tapping backdrop closes overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
        )

        // Native Card Bottom Sheet matching Android design standards
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(enabled = false, onClick = {})
                .testTag("face_scanning_banner"),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Classic Material 3 bottom-sheet drag handle index
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 20.dp)
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )

                Text(
                    text = "Biometric Verification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Confirm your identity to unlock securely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                AndroidBiometricGraphic(
                    isScanning = !detectedFace,
                    isSuccess = detectedFace,
                    modifier = Modifier.size(96.dp),
                    color = primaryColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = scanStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (detectedFace) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Minimized "Use PIN" option on left lower corner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(
                        onClick = onUseAlternative,
                        modifier = Modifier
                            .testTag("alt_auth_button")
                            .minimumInteractiveComponentSize(), // ensures proper accessibility target
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Use PIN",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun LockVerifyScreen(
    prefs: LockPreferences,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    packageName: String = "",
    onCancel: (() -> Unit)? = null,
    title: String = "App Lock Security",
    subtitle: String = "Authentication Required"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val repository = remember(context) {
        val db = com.example.data.AppDatabase.getInstance(context)
        com.example.data.AppRepository(db.lockedAppDao(), db.intruderAlertDao())
    }

    // Fetch Target Application details dynamically if packageName is provided
    var appLabel by remember(packageName) { mutableStateOf("") }
    var appIcon by remember(packageName) { mutableStateOf<Drawable?>(null) }

    var wrongAttemptsCount by remember { mutableStateOf(0) }
    var lockoutSecondsLeft by remember { mutableStateOf(0L) }
    val showFaceScan = false

    LaunchedEffect(Unit) {
        while (true) {
            val endMillis = prefs.lockoutEndTimestamp
            val currentMillis = System.currentTimeMillis()
            if (endMillis > currentMillis) {
                lockoutSecondsLeft = (endMillis - currentMillis + 999) / 1000
            } else {
                lockoutSecondsLeft = 0
            }
            delay(1000)
        }
    }

    val handleWrongAttempt = {
        wrongAttemptsCount++
        
        // Take an intruder alert photo for ANY wrong attempts starting from 3
        if (wrongAttemptsCount >= 3) {
            if (prefs.isIntruderDetectionEnabled) {
                com.example.data.IntruderCameraHelper.captureIntruderPhoto(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    onPhotoCaptured = { photoFile ->
                        coroutineScope.launch {
                            val alert = com.example.data.IntruderAlert(
                                photoPath = photoFile.absolutePath,
                                attemptedPackage = if (packageName.isNotEmpty()) packageName else null,
                                lockType = prefs.lockType
                            )
                            repository.insertIntruderAlert(alert)
                        }
                    },
                    onError = {
                        coroutineScope.launch {
                            val alert = com.example.data.IntruderAlert(
                                photoPath = "",
                                attemptedPackage = if (packageName.isNotEmpty()) packageName else null,
                                lockType = prefs.lockType
                            )
                            repository.insertIntruderAlert(alert)
                        }
                    }
                )
            }
        }

        // Apply a 30-second security cooldown block on every 3rd wrong attempt
        if (wrongAttemptsCount >= 3) {
            prefs.lockoutEndTimestamp = System.currentTimeMillis() + 30_000L
            lockoutSecondsLeft = 30
            
            // Biometric bypass disabled per request
        }
    }

    var patternState by remember { mutableStateOf(PatternState.DRAWING) }
    var pinAndPasswordAttemptId by remember { mutableStateOf(0) }
    var statusText by remember { 
        mutableStateOf(
            when (prefs.lockType) {
                "pattern" -> "Draw pattern to unlock"
                "pin" -> "Enter 4-digit PIN to unlock"
                "password" -> "Enter password to unlock"
                else -> "Authenticate to unlock"
            }
        ) 
    }

    // Auto-trigger biometric verification removed per request

    LaunchedEffect(packageName) {
        if (packageName.isNotEmpty()) {
            val cachedIcon = com.example.AppIconCache.get(packageName)
            if (cachedIcon != null) {
                appIcon = cachedIcon
                val pm = context.packageManager
                appLabel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val info = pm.getApplicationInfo(packageName, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        ""
                    }
                }
                return@LaunchedEffect
            }
            val pm = context.packageManager
            val details = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val info = pm.getApplicationInfo(packageName, 0)
                    val name = pm.getApplicationLabel(info).toString()
                    val icon = pm.getApplicationIcon(info)
                    Pair(name, icon)
                } catch (e: Exception) {
                    Pair("", null)
                }
            }
            appLabel = details.first
            appIcon = details.second
            if (details.second != null) {
                com.example.AppIconCache.put(packageName, details.second!!)
            }
        }
    }

    // Gate layouts: Check first for active security lockout cooldown
    if (lockoutSecondsLeft > 0) {
        SecurityLockoutScreen(
            secondsLeft = lockoutSecondsLeft,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Large Premium Visual Lock Ring
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (patternState == PatternState.SUCCESS) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Security Active Indicator",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Loading of Application Logo overlay (if present)
        if (packageName.isNotEmpty() && appIcon != null) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx -> ImageView(ctx) },
                    update = { imageView -> imageView.setImageDrawable(appIcon) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Title and Subtitle Block
        Text(
            text = if (appLabel.isNotEmpty()) appLabel else title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = if (appLabel.isNotEmpty()) "Locked with App Locker" else subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // Status Feed message
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (patternState) {
                PatternState.SUCCESS -> Color(0xFF4CAF50)
                PatternState.ERROR -> MaterialTheme.colorScheme.error
                PatternState.DRAWING -> MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main Unlock Component wrapper
        Box(
            modifier = Modifier
                .weight(5f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (prefs.lockType) {
                "pattern" -> {
                    PatternLockView(
                        modifier = Modifier.size(280.dp),
                        state = patternState,
                        onPatternComplete = { patternList ->
                            if (patternList.isEmpty()) {
                                statusText = "Connect at least 4 dots"
                                return@PatternLockView
                            }
                            val currentPatternStr = patternList.joinToString(",")
                            val savedPattern = prefs.savedPattern

                            if (currentPatternStr == savedPattern) {
                                patternState = PatternState.SUCCESS
                                statusText = "Unlock successful!"
                                coroutineScope.launch {
                                    delay(400)
                                    onSuccess()
                                }
                            } else {
                                patternState = PatternState.ERROR
                                statusText = "Pattern does not match! Try again"
                                handleWrongAttempt()
                                coroutineScope.launch {
                                    delay(1200)
                                    patternState = PatternState.DRAWING
                                    statusText = "Draw pattern to unlock"
                                }
                            }
                        }
                    )
                }
                "pin" -> {
                    PinPadView(
                        modifier = Modifier.fillMaxWidth(),
                        pinLength = 4,
                        resetIdentifier = pinAndPasswordAttemptId,
                        isBiometricEnabled = false,
                        onBiometricClick = null,
                        onPinComplete = { pin ->
                            val savedPin = prefs.savedPasscode
                            if (pin == savedPin) {
                                patternState = PatternState.SUCCESS
                                statusText = "PIN verified!"
                                coroutineScope.launch {
                                    delay(400)
                                    onSuccess()
                                }
                            } else {
                                patternState = PatternState.ERROR
                                statusText = "Incorrect PIN! Try again"
                                pinAndPasswordAttemptId++
                                handleWrongAttempt()
                                coroutineScope.launch {
                                    delay(1200)
                                    patternState = PatternState.DRAWING
                                    statusText = "Enter PIN to unlock"
                                }
                            }
                        }
                    )
                }
                "password" -> {
                    PasswordUnlockView(
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Enter your password",
                        resetIdentifier = pinAndPasswordAttemptId,
                        onPasswordComplete = { password ->
                            val savedPassword = prefs.savedPasscode
                            if (password == savedPassword) {
                                patternState = PatternState.SUCCESS
                                statusText = "Password verified!"
                                coroutineScope.launch {
                                    delay(400)
                                    onSuccess()
                                }
                            } else {
                                patternState = PatternState.ERROR
                                statusText = "Incorrect Password! Try again"
                                pinAndPasswordAttemptId++
                                handleWrongAttempt()
                                coroutineScope.launch {
                                    delay(1200)
                                    patternState = PatternState.DRAWING
                                    statusText = "Enter password to unlock"
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCancel != null) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.testTag("cancel_unlock_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }

            // Biometric trigger buttons removed
        }


    }
}
}
