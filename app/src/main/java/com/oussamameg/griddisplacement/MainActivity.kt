package com.oussamameg.griddisplacement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oussamameg.griddisplacement.ui.theme.GridDeformationTheme
import kotlinx.coroutines.launch
import java.lang.reflect.Field

class MainActivity : ComponentActivity() {
    private val images = getDrawableResourceIdsArray("image_", 1..30)
    private var currentImageResId = images[0]

    //TODO add fullDisplacement (Grid size with horizontal or vertical when size is small) Show settings button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        savedInstanceState?.let { bundle ->
            if (bundle.containsKey("currentImageResId"))
                currentImageResId = bundle.getInt("currentImageResId")
        }
        val glImageSurfaceView = GLImageSurfaceView(this, currentImageResId)
        setContent {
            GridDeformationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .padding(0.dp)
                                .fillMaxSize(),
                            factory = {
                                glImageSurfaceView
                            }
                        )
                        Settings(glImageSurfaceView, images, currentImageResId)
                        //val accelerometerState = rememberAccelerometerSensorState()
                        //glImageSurfaceView.imageRenderer.square?.onAccelerometerInput(accelerometerState.xForce,accelerometerState.yForce)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentImageResId", currentImageResId)
    }

    private fun getResId(resName: String, c: Class<*>): Int {
        return try {
            val field: Field = c.getDeclaredField(resName)
            field.getInt(field)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun getDrawableResourceIdsArray(
        baseName: String,
        range: IntRange
    ): IntArray {
        val resourceIds = mutableListOf<Int>()
        val drawableClass = R.drawable::class.java
        for (index in range) {
            val resourceName = "${baseName}${index}"
            val resourceId = getResId(resourceName, drawableClass)
            if (resourceId != -1) {
                resourceIds.add(resourceId)
            }
        }
        return resourceIds.toIntArray()
    }

}


@Preview
@Composable
fun Settings(
    glImageSurfaceView: GLImageSurfaceView? = null,
    images: IntArray? = null,
    imageResId: Int? = 0
) {
    var currentImageResId by rememberSaveable { mutableIntStateOf(imageResId ?: 0) }
    var expandedState by rememberSaveable { mutableStateOf(false) }
    var gridMap by rememberSaveable { mutableStateOf(false) }
    var rgbShift by rememberSaveable { mutableStateOf(true) }
    var restoreDisplacement by rememberSaveable { mutableStateOf(true) }
    var currentImageIndex by rememberSaveable { mutableIntStateOf(0) }
    var relaxation by rememberSaveable { mutableFloatStateOf(0.965f) }
    var distance by rememberSaveable { mutableFloatStateOf(0.6f) }
    var strength by rememberSaveable { mutableFloatStateOf(0.8f) }
    var showSettings by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val offsetX =
        remember { Animatable(if (expandedState) 0f else context.resources.displayMetrics.widthPixels.toFloat()) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(false) {
        glImageSurfaceView?.imageRenderer?.setOnSurfaceCreatedAndReadyCallback {
            glImageSurfaceView.toggleGridMap(gridMap)
            glImageSurfaceView.toggleRGBShift(rgbShift)
            glImageSurfaceView.toggleRestoreDisplacement(restoreDisplacement)
            glImageSurfaceView.setRelaxation(relaxation)
            glImageSurfaceView.setDistance(distance)
            glImageSurfaceView.setStrength(strength)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp, y = 0.dp)
                .padding(top = 50.dp, start = 50.dp, end = 50.dp)
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                )
                .animateContentSize()
                .fillMaxSize()
        ) {
            Column(
                Modifier
                    .padding(horizontal = 10.dp)
                    .verticalScroll(rememberScrollState())
                    .wrapContentSize()
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(enabled = currentImageIndex > 0,
                        onClick = {
                            currentImageIndex--
                            if (currentImageIndex <= 0) {
                                currentImageIndex = 0
                            }
                            currentImageResId = images?.get(currentImageIndex) ?: 0
                            glImageSurfaceView?.setImageResId(currentImageResId)
                        }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                    Text("Image ${currentImageIndex + 1}")
                    IconButton(onClick = {
                        currentImageIndex++
                        if (currentImageIndex >= images?.size!!) {
                            currentImageIndex = 0
                        }
                        currentImageResId = images[currentImageIndex]
                        glImageSurfaceView?.setImageResId(currentImageResId)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
                SwitchToggle("RGB shift", rgbShift) {
                    rgbShift = it
                    glImageSurfaceView?.toggleRGBShift(it)
                }
                SwitchToggle("Show settings", showSettings) {
                    showSettings = it
                }
                SwitchToggle("Restore displacement", restoreDisplacement) {
                    restoreDisplacement = it
                    glImageSurfaceView?.toggleRestoreDisplacement(it)
                }
                SwitchToggle("Grid map", gridMap) {
                    gridMap = it
                    glImageSurfaceView?.toggleGridMap(it)
                }
                Slider("Relaxation", relaxation, 0.5f..0.99f) {
                    relaxation = it
                    glImageSurfaceView?.setRelaxation(it)
                }
                Slider("Distance", distance, 0f..1f) {
                    distance = it
                    glImageSurfaceView?.setDistance(it)
                }
                Slider("Strength", strength, 0f..1f) {
                    strength = it
                    glImageSurfaceView?.setStrength(it)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showSettings)
                IconButton(
                    modifier = Modifier
                        .padding(horizontal = 0.dp, vertical = 10.dp)
                        .align(Alignment.BottomEnd)
                        .background(
                            color = Color(0x833D3B3B),
                            shape = RoundedCornerShape(
                                topStart = 50.dp,
                                bottomStart = 50.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            )
                        ), onClick = {
                        expandedState = !expandedState
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = if (expandedState) 0f else context.resources.displayMetrics.widthPixels.toFloat(),
                                animationSpec = tween(durationMillis = 800)
                            )
                        }
                    }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null
                    )
                }
        }
    }
}

@Composable
fun Slider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 5.dp)) {
        Text(text = "$title: $value")
        Spacer(Modifier.size(5.dp))
        androidx.compose.material3.Slider(
            value = value,
            valueRange = range,
            onValueChange = onValueChange
        )
    }
}

@Composable
fun SwitchToggle(text: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Row(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
