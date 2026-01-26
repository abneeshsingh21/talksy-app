package com.jengachat.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedSplashScreen(
    onSplashComplete: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Main animation progress
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    // 3D Rotation animation
    val rotationY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationY"
    )
    
    // Floating animation
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    // Pulse animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Entry animations
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000),
        label = "logoAlpha"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 500),
        label = "textAlpha"
    )
    
    val taglineAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 800),
        label = "taglineAlpha"
    )
    
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(4000) // Show splash for 4 seconds
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated background particles
        ParticleBackground(
            modifier = Modifier.fillMaxSize()
        )
        
        // Glowing rings
        GlowingRings(
            modifier = Modifier
                .size(300.dp)
                .alpha(glowAlpha),
            rotationY = rotationY
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D Animated Logo
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(y = floatOffset.dp)
                    .scale(logoScale * pulseScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .alpha(glowAlpha * 0.5f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF),
                                Color(0xFF00D9FF).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
                
                // Main 3D Logo
                Animated3DLogo(
                    modifier = Modifier.size(150.dp),
                    rotationY = rotationY
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // App Name with gradient
            Text(
                text = "Talksy",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(textAlpha),
                style = MaterialTheme.typography.headlineLarge.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00D9FF),
                            Color(0xFF00FF88),
                            Color(0xFFFFD700)
                        )
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tagline
            Text(
                text = "Connect. Chat. Call.",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Light,
                modifier = Modifier.alpha(taglineAlpha),
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(60.dp))
            
            // Loading indicator
            LoadingDots(
                modifier = Modifier.alpha(textAlpha)
            )
        }
    }
}

@Composable
fun Animated3DLogo(
    modifier: Modifier = Modifier,
    rotationY: Float
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2.5f
        
        // Calculate 3D perspective effect
        val perspective = cos(Math.toRadians(rotationY.toDouble())).toFloat()
        val depthScale = 0.3f + (0.7f * ((perspective + 1f) / 2f))
        
        // Shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.3f),
            radius = radius * depthScale,
            center = Offset(centerX + 5f, centerY + 8f)
        )
        
        // Main chat bubble - 3D effect with gradient
        val mainGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF00D9FF),
                Color(0xFF0099CC),
                Color(0xFF006699)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        
        // Draw 3D chat bubble
        drawPath(
            path = createChatBubblePath(centerX, centerY, radius * depthScale),
            brush = mainGradient
        )
        
        // Highlight for 3D effect
        drawPath(
            path = createChatBubblePath(centerX - 5f, centerY - 5f, radius * depthScale * 0.85f),
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.5f, size.height * 0.5f)
            )
        )
        
        // Inner elements - chat dots
        val dotRadius = radius * 0.08f
        val dotY = centerY - radius * 0.1f
        val dotSpacing = radius * 0.35f
        
        // Animate dots based on rotation
        val dotOffset = sin(Math.toRadians(rotationY.toDouble() * 2)).toFloat() * 3f
        
        drawCircle(
            color = Color.White,
            radius = dotRadius,
            center = Offset(centerX - dotSpacing, dotY + dotOffset)
        )
        drawCircle(
            color = Color.White,
            radius = dotRadius,
            center = Offset(centerX, dotY - dotOffset)
        )
        drawCircle(
            color = Color.White,
            radius = dotRadius,
            center = Offset(centerX + dotSpacing, dotY + dotOffset)
        )
        
        // Video call icon overlay
        drawVideoIcon(
            center = Offset(centerX, centerY + radius * 0.3f),
            size = radius * 0.4f,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

private fun DrawScope.createChatBubblePath(
    centerX: Float,
    centerY: Float,
    radius: Float
): Path {
    return Path().apply {
        // Main bubble
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = centerX - radius,
                top = centerY - radius * 0.8f,
                right = centerX + radius,
                bottom = centerY + radius * 0.5f,
                radiusX = radius * 0.3f,
                radiusY = radius * 0.3f
            )
        )
        
        // Tail
        moveTo(centerX - radius * 0.3f, centerY + radius * 0.5f)
        lineTo(centerX - radius * 0.6f, centerY + radius * 0.9f)
        lineTo(centerX, centerY + radius * 0.5f)
        close()
    }
}

private fun DrawScope.drawVideoIcon(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        // Camera body
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = center.x - size * 0.5f,
                top = center.y - size * 0.3f,
                right = center.x + size * 0.2f,
                bottom = center.y + size * 0.3f,
                radiusX = size * 0.1f,
                radiusY = size * 0.1f
            )
        )
        
        // Camera lens triangle
        moveTo(center.x + size * 0.2f, center.y - size * 0.2f)
        lineTo(center.x + size * 0.5f, center.y - size * 0.35f)
        lineTo(center.x + size * 0.5f, center.y + size * 0.35f)
        lineTo(center.x + size * 0.2f, center.y + size * 0.2f)
        close()
    }
    
    drawPath(path, color)
}

@Composable
fun GlowingRings(
    modifier: Modifier = Modifier,
    rotationY: Float
) {
    Canvas(modifier = modifier.rotate(rotationY * 0.1f)) {
        val center = Offset(size.width / 2, size.height / 2)
        
        // Outer ring
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF00D9FF).copy(alpha = 0.6f),
                    Color(0xFF00FF88).copy(alpha = 0.3f),
                    Color(0xFFFFD700).copy(alpha = 0.6f),
                    Color(0xFFFF6B6B).copy(alpha = 0.3f),
                    Color(0xFF00D9FF).copy(alpha = 0.6f)
                ),
                center = center
            ),
            radius = size.minDimension / 2,
            center = center,
            style = Stroke(width = 3f)
        )
        
        // Middle ring
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF00FF88).copy(alpha = 0.5f),
                    Color(0xFF00D9FF).copy(alpha = 0.2f),
                    Color(0xFFFFD700).copy(alpha = 0.5f),
                    Color(0xFF00FF88).copy(alpha = 0.5f)
                ),
                center = center
            ),
            radius = size.minDimension / 2.5f,
            center = center,
            style = Stroke(width = 2f)
        )
        
        // Inner ring
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFFD700).copy(alpha = 0.4f),
                    Color(0xFF00D9FF).copy(alpha = 0.2f),
                    Color(0xFFFFD700).copy(alpha = 0.4f)
                ),
                center = center
            ),
            radius = size.minDimension / 3.5f,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
fun ParticleBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleProgress"
    )
    
    Canvas(modifier = modifier) {
        val particleCount = 30
        
        for (i in 0 until particleCount) {
            val baseX = (i * 137.5f) % size.width
            val baseY = ((i * 73.7f) + (particleProgress * size.height * (1 + (i % 3) * 0.5f))) % size.height
            val particleSize = 2f + (i % 4) * 1.5f
            val alpha = 0.2f + (i % 5) * 0.1f
            
            drawCircle(
                color = when (i % 3) {
                    0 -> Color(0xFF00D9FF)
                    1 -> Color(0xFF00FF88)
                    else -> Color(0xFFFFD700)
                }.copy(alpha = alpha),
                radius = particleSize,
                center = Offset(baseX, baseY)
            )
        }
    }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = Color(0xFF00D9FF).copy(alpha = dot1Alpha),
                radius = size.minDimension / 2
            )
        }
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = dot2Alpha),
                radius = size.minDimension / 2
            )
        }
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = dot3Alpha),
                radius = size.minDimension / 2
            )
        }
    }
}
