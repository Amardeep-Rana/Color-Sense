package com.legendamardeep.colorsense

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import com.legendamardeep.colorsense.ui.theme.ColorSenseTheme
import com.legendamardeep.colorsense.ui.theme.PrimaryGradientEnd
import com.legendamardeep.colorsense.ui.theme.PrimaryGradientStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var initialUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            val systemInDarkTheme = isSystemInDarkTheme()
            
            LaunchedEffect(systemInDarkTheme) { isDarkMode = systemInDarkTheme }

            ColorSenseTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(isDarkMode, onToggleDarkMode = { isDarkMode = !isDarkMode }, initialUri = initialUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else {
                intent.data
            }
            initialUri = uri
        }
    }
}

@Composable
fun MainNavigation(isDarkMode: Boolean, onToggleDarkMode: () -> Unit, initialUri: Uri? = null) {
    var currentScreen by remember { mutableStateOf("home") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                val btm = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                if (btm.width > 1200 || btm.height > 1200) {
                    val ratio = btm.width.toFloat() / btm.height.toFloat()
                    if (ratio > 1) Bitmap.createScaledBitmap(btm, 1200, (1200 / ratio).toInt(), true)
                    else Bitmap.createScaledBitmap(btm, (1200 * ratio).toInt(), 1200, true)
                } else btm
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.setTargetSize(1080, 1920)
                }
            }
        } catch (e: Exception) { null }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { uri ->
            val btm = loadBitmapFromUri(uri)
            if (btm != null) {
                bitmap = btm
                currentScreen = "main"
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val btm = loadBitmapFromUri(it)
            if (btm != null) {
                bitmap = btm
                currentScreen = "main"
            }
        }
    }

    when (currentScreen) {
        "home" -> HomeScreen(isDarkMode, onToggleDarkMode, onPickImage = { launcher.launch("image/*") })
        "main" -> MainScreen(bitmap, onBack = { currentScreen = "home" })
    }
}

@Composable
fun HomeScreen(isDarkMode: Boolean, onToggleDarkMode: () -> Unit, onPickImage: () -> Unit) {
    val context = LocalContext.current
    val wheelBitmap = remember {
        try {
            context.assets.open("color_wheel.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        } catch (e: Exception) { null }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { pv ->
        Box(modifier = Modifier.fillMaxSize().padding(pv).systemBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Color Sense", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(0.4f))
                
                Box(contentAlignment = Alignment.TopCenter) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(top = 45.dp)
                            .shadow(8.dp, RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp).padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Analyze colors\nfrom images.",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 38.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            
                            val animatedBlur by animateDpAsState(
                                targetValue = if (isPressed) 20.dp else 12.dp,
                                animationSpec = tween(200), label = "blur"
                            )
                            val animatedAlpha by animateFloatAsState(
                                targetValue = if (isPressed) 0.7f else 0.45f,
                                animationSpec = tween(200), label = "alpha"
                            )
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isPressed) 0.96f else 1f,
                                animationSpec = tween(150), label = "scale"
                            )

                            // Interactive Pick Image Button with 360-degree GLOW
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(50.dp)
                                    .graphicsLayer(scaleX = animatedScale, scaleY = animatedScale)
                                    .drawBehind {
                                        val shadowBlur = animatedBlur.toPx()
                                        drawIntoCanvas { canvas ->
                                            val paint = Paint().asFrameworkPaint().apply {
                                                maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
                                                shader = android.graphics.LinearGradient(
                                                    0f, 0f, size.width, 0f,
                                                    PrimaryGradientStart.toArgb(),
                                                    PrimaryGradientEnd.toArgb(),
                                                    android.graphics.Shader.TileMode.CLAMP
                                                )
                                                alpha = (animatedAlpha * 255).toInt()
                                            }
                                            
                                            // Centered drawing for 360-degree glow
                                            canvas.nativeCanvas.drawRoundRect(
                                                -2.dp.toPx(), -2.dp.toPx(), 
                                                size.width + 2.dp.toPx(), size.height + 2.dp.toPx(),
                                                25.dp.toPx(), 25.dp.toPx(),
                                                paint
                                            )
                                        }
                                    }
                                    .background(
                                        Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)), 
                                        RoundedCornerShape(25.dp)
                                    )
                                    .clip(RoundedCornerShape(25.dp))
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(),
                                        onClick = onPickImage
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick an Image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text("Info", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Select a photo to begin\ncolor sampling.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .shadow(4.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        wheelBitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Brush.linearGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)), CircleShape)
                        .clip(CircleShape)
                        .clickable { onToggleDarkMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isDarkMode) Icons.Default.WbSunny else Icons.Default.Brightness3, 
                        null, 
                        tint = Color.White, 
                        modifier = Modifier.size(24.dp).graphicsLayer(rotationZ = 135f)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(bitmap: Bitmap?, onBack: () -> Unit) {
    var selectedColor by remember { mutableStateOf(Color.White) }
    var pickerOffset by remember { mutableStateOf(Offset.Zero) }
    var activeTab by remember { mutableStateOf("HEX") }
    var containerSize by remember { mutableStateOf(Offset.Zero) }
    var isDropperEnabled by remember { mutableStateOf(true) }
    
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    
    val glowAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val hex = String.format("#%06X", (0xFFFFFF and selectedColor.toArgb()))
    val rgb = "${(selectedColor.red * 255).toInt()}, ${(selectedColor.green * 255).toInt()}, ${(selectedColor.blue * 255).toInt()}"
    val hsv = FloatArray(3).apply { android.graphics.Color.colorToHSV(selectedColor.toArgb(), this) }
    val hsl = "${hsv[0].toInt()}°, ${(hsv[1] * 100).toInt()}%, ${(hsv[2] * 100).toInt()}%"

    fun updateColor(pos: Offset, cw: Float, ch: Float) {
        if (!isDropperEnabled) return
        bitmap?.let { btm ->
            try {
                val bw = btm.width.toFloat()
                val bh = btm.height.toFloat()
                val fitScale = minOf(cw / bw, ch / bh)
                val fitDx = (cw - bw * fitScale) / 2f
                val fitDy = (ch - bh * fitScale) / 2f
                val centerX = cw / 2f
                val centerY = ch / 2f
                val pX = (pos.x - translation.x - centerX) / scale + centerX
                val pY = (pos.y - translation.y - centerY) / scale + centerY
                val bx = ((pX - fitDx) / fitScale).roundToInt().coerceIn(0, btm.width - 1)
                val by = ((pY - fitDy) / fitScale).roundToInt().coerceIn(0, btm.height - 1)
                selectedColor = Color(btm.getPixel(bx, by))
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(scale, translation, pickerOffset, isDropperEnabled) {
        if (containerSize != Offset.Zero) {
            updateColor(pickerOffset, containerSize.x, containerSize.y)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, null)
                }
                Text("Color Sense", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDropperEnabled) Brush.linearGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd))
                            else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                        )
                        .clickable { isDropperEnabled = !isDropperEnabled },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Colorize, 
                        null, 
                        tint = if (isDropperEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(selectedColor).shadow(1.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.background.copy(0.5f)).padding(4.dp)) {
                            listOf("HEX", "RGB", "HSL").forEach { tab ->
                                val sel = activeTab == tab
                                Box(modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) Brush.horizontalGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                    .clickable { activeTab = tab }, contentAlignment = Alignment.Center) {
                                    Text(tab, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val currentVal = when(activeTab) { "HEX" -> hex; "RGB" -> rgb; else -> hsl }
                            Text(currentVal, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.background.copy(0.5f))
                                    .clickable {
                                        val txt = when(activeTab) { "HEX" -> hex; "RGB" -> rgb; else -> hsl }
                                        clipboard.setText(AnnotatedString(txt))
                                        Toast.makeText(context, "$activeTab Copied!", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Start) {
                            Column(modifier = Modifier.width(80.dp)) { 
                                Text("HEX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text(hex, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) 
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.width(100.dp)) { 
                                Text("RGB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text(rgb, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) 
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) { 
                                Text("HSL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text(hsl, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) 
                            }
                        }
                    }
                }
            }
        }
    ) { pv ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(pv)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                if (glowAnim.value > 0f) {
                    val expansion = glowAnim.value * 30.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(PrimaryGradientStart, PrimaryGradientEnd)),
                        topLeft = Offset(-expansion, -expansion),
                        size = size.copy(width = size.width + expansion * 2, height = size.height + expansion * 2),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx() * glowAnim.value),
                        alpha = glowAnim.value
                    )
                }
            }
            .onGloballyPositioned { 
                if (containerSize == Offset.Zero) {
                    containerSize = Offset(it.size.width.toFloat(), it.size.height.toFloat())
                    pickerOffset = Offset(containerSize.x / 2f, containerSize.y / 2f)
                }
            }
            .pointerInput(isDropperEnabled) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        if (pointers.size == 1) {
                            val change = pointers[0]
                            val pan = change.position - change.previousPosition
                            if (isDropperEnabled) {
                                pickerOffset = Offset(
                                    (pickerOffset.x + pan.x).coerceIn(0f, size.width.toFloat()),
                                    (pickerOffset.y + pan.y).coerceIn(0f, size.height.toFloat())
                                )
                                change.consume()
                            } else {
                                val maxTx = (size.width * (scale - 1f) / 2f)
                                val maxTy = (size.height * (scale - 1f) / 2f)
                                translation = Offset(
                                    (translation.x + pan.x).coerceIn(-maxTx, maxTx),
                                    (translation.y + pan.y).coerceIn(-maxTy, maxTy)
                                )
                                change.consume()
                            }
                        } else if (pointers.size > 1) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val oldScale = scale
                            scale = (scale * zoomChange).coerceIn(1f, 10f)
                            if (scale != oldScale) {
                                scope.launch {
                                    glowAnim.snapTo(1f)
                                    glowAnim.animateTo(0f, tween(600))
                                }
                            }
                            val maxTx = (size.width * (scale - 1f) / 2f)
                            val maxTy = (size.height * (scale - 1f) / 2f)
                            translation = Offset(
                                (translation.x + panChange.x).coerceIn(-maxTx, maxTx),
                                (translation.y + panChange.y).coerceIn(-maxTy, maxTy)
                            )
                            pointers.forEach { it.consume() }
                        }
                    } while (pointers.any { it.pressed })
                }
            }
        ) {
            bitmap?.let { btm ->
                Image(
                    bitmap = btm.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translation.x
                            translationY = translation.y
                        }
                )
            }
            if (isDropperEnabled) {
                val dropperSize = 80.dp
                val dropperSizePx = with(density) { dropperSize.toPx() }
                Box(modifier = Modifier.offset { IntOffset((pickerOffset.x - dropperSizePx/2).roundToInt(), (pickerOffset.y - dropperSizePx/2).roundToInt()) }.size(dropperSize)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        drawCircle(Color.White, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
                        drawCircle(Color.Black.copy(0.3f), radius = r + 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                        drawLine(Color.Red, Offset(center.x - 20, center.y), Offset(center.x + 20, center.y), 3f)
                        drawLine(Color.Red, Offset(center.x, center.y - 20), Offset(center.x, center.y + 20), 3f)
                        drawCircle(Color.White, radius = 2f)
                    }
                }
            }
        }
    }
}
