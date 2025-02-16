package com.appy

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.math.max
import kotlin.math.min

const val APPWIDGET_HOST_ID = 1433

class AppsFragment : MyFragment() {
    class WidgetHost(context: Context?) : AppWidgetHost(context, APPWIDGET_HOST_ID)

    class ScaleLayout(context : Context) : FrameLayout(context)
    {
        var innerWidth = 0
            set(value) {
                if (field != value) {
                    field = value
                    postInvalidate()
                }
            }
        var innerHeight = 0
            set(value) {
                if (field != value) {
                    field = value
                    postInvalidate()
                }
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

            for (child in children) {
                child.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY))
            }
        }

        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
            ev?.transform(Matrix().apply {
                setScale(if (innerWidth == 0) 1f else innerWidth.toFloat() / width.toFloat(), if (innerHeight == 0) 1f else innerHeight.toFloat() / height.toFloat())
            })

            return super.onInterceptTouchEvent(ev)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            for (child in children) {
                child.layout(left, top, left + innerWidth, top + innerHeight)
            }
        }

        override fun dispatchDraw(canvas : android.graphics.Canvas) {
            canvas.save()
            canvas.scale(if (innerWidth == 0) 1f else width.toFloat() / innerWidth.toFloat(), if (innerHeight == 0) 1f else height.toFloat() / innerHeight.toFloat())
            super.dispatchDraw(canvas)
            canvas.restore()
        }
    }

    class WidgetItem (val widgetId: Int, val frame : ScaleLayout, val widgetView : AppWidgetHostView)

    private var widgetHost: WidgetHost? = null
    private var appyInfo: AppWidgetProviderInfo? = null

    private val _widgetGridList = mutableStateListOf<WidgetItem>()
    private val setListenerMethod = Reflection.getMethods(AppWidgetHost::class.java).find { it.name == "setListener"}

    override fun onShow(activity: MainActivity?) {
        if (!initializeOnce(activity)) {
            return
        }
        widgetHost!!.startListening()
    }

    override fun onHide(activity: MainActivity?) {
        if (!initializeOnce(activity)) {
            return
        }
        widgetHost!!.stopListening()
    }

    override fun onActivityResult(data: Intent) {
        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) {
            Log.e("APPY", "no widget id on bindapp permission request")
            return
        }

        _widgetGridList.add(startNewWidget(widgetId))
    }

    private fun startNewWidget(widgetId : Int) : WidgetItem
    {
        val frame = ScaleLayout(requireActivity())
        val widgetView = widgetHost!!.createView(requireActivity().applicationContext, widgetId, appyInfo)

        //frame.setBackgroundResource(R.drawable.drawable_outline_success_btn)
        //widgetView.setBackgroundResource(R.drawable.drawable_outline_danger_btn)

        val item = WidgetItem(widgetId, frame, widgetView)

        item.frame.tag = item

        item.frame.addView(widgetView)

        return item
    }

    @SuppressLint("NewApi")
    fun initializeOnce(context: Context?): Boolean {
        if (context == null) {
            return false
        }

        if (appyInfo != null && widgetHost != null) {
            return true
        }

        for (info in AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, null)) {
            if (info.provider.className.endsWith(".WidgetReceiver1x1")) {
                appyInfo = info
                break
            }
        }

        if (appyInfo == null) {
            throw RuntimeException("No appy widget found")
        }

        appyInfo!!.initialLayout = R.layout.initial_widget

        widgetHost = WidgetHost(context.applicationContext)

        return true
    }

    private fun requestPermission(widgetId: Int, name: ComponentName?, options: Bundle?) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, name)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        requestActivityResult(intent)
    }

    private fun clearWidgets()
    {
        if (!initializeOnce(activity)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (widgetId in widgetHost!!.appWidgetIds) {
                widgetHost!!.deleteAppWidgetId(widgetId)
            }
        }
    }

    private fun addWidget()
    {
        if (!initializeOnce(activity)) {
            return
        }

        val widgetId = widgetHost!!.allocateAppWidgetId()
        val options = Bundle()

        if (!AppWidgetManager.getInstance(activity)
                .bindAppWidgetIdIfAllowed(widgetId, appyInfo!!.provider, options)
        ) {
            requestPermission(widgetId, appyInfo!!.provider, options)
        } else {
            _widgetGridList.add(startNewWidget(widgetId))
        }
    }

    private fun updateWidgetSizeIfNeeded(widgetItem : WidgetItem, widthPixels : Int, heightPixels : Int)
    {
        val sizes = ArrayList<SizeF>()
        val r = resources
        val widthdips: Float = widthPixels / TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            r.displayMetrics
        )
        val heightdips: Float = heightPixels / TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            r.displayMetrics
        )

        sizes.add(SizeF(widthdips, heightdips))
        sizes.add(SizeF(heightdips, widthdips))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            widgetItem.widgetView.updateAppWidgetSize(Bundle(), sizes)
        } else {
            val min = min(widthdips.toDouble(), heightdips.toDouble()).toInt()
            val max = max(widthdips.toDouble(), heightdips.toDouble()).toInt()

            val prevOptions = AppWidgetManager.getInstance(widgetItem.widgetView.context).getAppWidgetOptions(widgetItem.widgetId)

            if (prevOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) == min &&
                prevOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) == min &&
                prevOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) == max &&
                prevOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) == max)
            {
                return
            }

            val options = Bundle()
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, min)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, min)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, max)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, max)

            widgetItem.widgetView.updateAppWidgetOptions(options)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_apps, container, false)
        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Content(composeView)
            }
        }
        return view
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun Grid(selectedState : Int,
             lastSelectedState : Int,
             fullSize : IntSize,
             onWidgetClick: (item : WidgetItem) -> Unit,
             containerView: View,
             lazyGridState: LazyGridState,
             sharedTransitionScope: SharedTransitionScope,
             animatedVisibilityScope: AnimatedVisibilityScope?
    )
    {
        val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
            if (from != to) {
                _widgetGridList.add(to.index, _widgetGridList.removeAt(from.index))
            }

            ViewCompat.performHapticFeedback(
                containerView,
                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
            )
        }

        with (sharedTransitionScope) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(count = 3),
                modifier = Modifier.fillMaxSize(),
                state = lazyGridState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(_widgetGridList, key = { it.widgetId }) { item ->
                    ReorderableItem(
                        reorderableLazyGridState,
                        key = item.widgetId
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }

                        Card(onClick = {},
                            colors = CardColors(containerColor = Color.DarkGray,
                                                contentColor = Color.DarkGray,
                                                disabledContainerColor = Color.DarkGray,
                                                disabledContentColor = Color.DarkGray
                                                ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp).aspectRatio(1f).draggableHandle(
                                    onDragStarted = {
                                        ViewCompat.performHapticFeedback(
                                            containerView,
                                            HapticFeedbackConstantsCompat.GESTURE_START
                                        )
                                    },
                                    onDragStopped = {
                                        ViewCompat.performHapticFeedback(
                                            containerView,
                                            HapticFeedbackConstantsCompat.GESTURE_END
                                        )
                                    },
                                    interactionSource = interactionSource,
                                ),
                        ) {
                            Column (horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(5.dp)){
                                Row (modifier = Modifier.wrapContentHeight().fillMaxWidth()){
                                    IconButton(

                                        onClick = {},
                                    ) {
                                        Icon(
                                            Icons.Rounded.Menu,
                                            contentDescription = "Reorder",
                                            tint = Color.White
                                        )
                                    }
                                }

                                Box(modifier = Modifier.border(2.dp, Color.Black).wrapContentWidth().fillMaxHeight().aspectRatio(fullSize.width.toFloat() / fullSize.height.toFloat())) {
                                    var modifier = Modifier.fillMaxSize()

                                    if (hasListenerMethod()) {
                                        modifier = modifier.sharedElement(
                                            rememberSharedContentState(key = "widget_${item.widgetId}"),
                                            animatedVisibilityScope = animatedVisibilityScope!!
                                        )
                                    }

                                    AndroidView(
                                        factory = {
                                            item.frame.apply {
                                                innerWidth = fullSize.width
                                                innerHeight = fullSize.height
                                            }
                                        },
                                        update = {
                                            item.frame.apply {
                                                innerWidth = fullSize.width
                                                innerHeight = fullSize.height
                                            }
                                            if (selectedState == -1 && lastSelectedState != -1) {
                                                if (hasListenerMethod()) {
                                                    setListener(
                                                        widgetHost!!,
                                                        item.widgetId,
                                                        item.widgetView
                                                    )
                                                }
                                            }

                                            updateWidgetSizeIfNeeded(
                                                item,
                                                fullSize.width,
                                                fullSize.height
                                            )
                                        },
                                        modifier = modifier
                                    )

                                    Box (modifier = Modifier.background(Color.Transparent).matchParentSize().clickable {onWidgetClick(item)})
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasListenerMethod() : Boolean
    {
        return setListenerMethod != null
    }

    private fun setListener(host : AppWidgetHost, widgetId : Int, view : AppWidgetHostView)
    {
        try {
            setListenerMethod!!.invoke(host, widgetId, view)
        } catch (_ : Exception){
            // best effort
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun Fully(selectedState : Int,
              lastSelectedState : Int,
              fullSize: IntSize,
              sharedTransitionScope: SharedTransitionScope,
              animatedVisibilityScope: AnimatedVisibilityScope?) {
        with (sharedTransitionScope)
        {
            val properState = if (selectedState != -1) selectedState else lastSelectedState
            var modifier = Modifier.fillMaxSize()

            if (hasListenerMethod())
            {
                modifier = modifier.sharedElement(
                    rememberSharedContentState(key = "widget_$properState"),
                    animatedVisibilityScope = animatedVisibilityScope!!
                )
            }

            AndroidView (
                factory = {
                    val widgetItem = if (hasListenerMethod()) {
                        startNewWidget(properState)
                    } else {
                        _widgetGridList.first {it.widgetId == selectedState}
                    }

                    widgetItem.frame.apply {
                        innerWidth = fullSize.width
                        innerHeight = fullSize.height
                    }
                },
                update = {
                    val widgetItem = it.tag as WidgetItem

                    if (selectedState != -1 && lastSelectedState == -1) {
                        if (hasListenerMethod()) {
                            setListener(widgetHost!!, properState, widgetItem.widgetView)
                        }
                    }

                    it.innerWidth = fullSize.width
                    it.innerHeight = fullSize.height

                    updateWidgetSizeIfNeeded(widgetItem, fullSize.width, fullSize.height)
                },
                modifier = modifier
            )
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun Content(containerView: View)
    {
        val lazyGridState = rememberLazyGridState()

        var selectedState by remember { mutableIntStateOf (-1) }
        var lastSelectedState by remember { mutableIntStateOf (-1) }

        var fullSize by remember { mutableStateOf(IntSize(0, 0)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = {
                    addWidget()
                },
                modifier = Modifier
                    .width(120.dp)
                    .height(60.dp)
                    .padding(8.dp)
            ) {
                Text("Add")
            }

            Button(
                onClick = {
                    lastSelectedState = selectedState
                    selectedState = -1
                },
                modifier = Modifier
                    .width(120.dp)
                    .height(60.dp)
                    .padding(8.dp)
            ) {
                Text("Min")
            }

            Button(
                onClick = {
                    clearWidgets()
                },
                modifier = Modifier
                    .width(120.dp)
                    .height(60.dp)
                    .padding(8.dp)
            )
            {
                Text("Clear")
            }

            SharedTransitionLayout (
                modifier = Modifier.fillMaxSize().onGloballyPositioned { fullSize = it.size }
            ){
                val content = @Composable { targetState : Int, animatedContent : AnimatedVisibilityScope? ->

                    if (targetState != -1) {
                        Fully(
                            selectedState,
                            lastSelectedState,
                            fullSize,
                            animatedVisibilityScope = animatedContent,
                            sharedTransitionScope = this@SharedTransitionLayout,
                        )
                    } else {
                        Grid(selectedState,
                            lastSelectedState,
                            fullSize,
                            onWidgetClick = {
                                lastSelectedState = selectedState
                                selectedState = it.widgetId
                            },
                            containerView,
                            lazyGridState,
                            animatedVisibilityScope = animatedContent,
                            sharedTransitionScope = this@SharedTransitionLayout)
                    }
                }

                if (hasListenerMethod()) {
                    AnimatedContent(
                        selectedState,
                        label = "basic_transition"
                    ) { targetState ->
                        content(targetState, this@AnimatedContent)
                    }
                } else {
                    content(selectedState, null)
                }
            }
        }
    }
}