package com.appy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.appy.DictObj.Dict
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val APPWIDGET_HOST_ID = 1433
const val OPTION_APPWIDGET_APPY_APP = "appWidgetAppyApp"

class AppsFragment : MyFragment() {
    class WidgetHost(context: Context?) : AppWidgetHost(context, APPWIDGET_HOST_ID);

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

    class WidgetItem (val widgetId: Int,
                      val widgetUnique : Int,
                      val frame : ScaleLayout,
                      val widgetView : AppWidgetHostView,
                      var title : String? = null,
                      icons : Dict? = null,
                      var name : String? = null)
    {
        var icons : Dict? = icons
            set(value) {
                field = value
                val menu = Utils.chooseIcon(value, "menu", 48, 48)
                if (menu != null) {
                    menuIcon = BitmapFactory.decodeByteArray(menu, 0, menu.size)
                }
                val shortcut = Utils.chooseIcon(value, "shortcut", 128, 128)
                if (shortcut != null) {
                    shortcutIcon = BitmapFactory.decodeByteArray(shortcut, 0, shortcut.size)
                }
            }
        var menuIcon : Bitmap? = null
            private set
        var shortcutIcon : Bitmap? = null
    }

    private var widgetHost: WidgetHost? = null
    private var appyInfo: AppWidgetProviderInfo? = null
    private var attachedAndBound = false;

    private val _widgetGridList = mutableStateListOf<WidgetItem>()
    private val selectedState = mutableIntStateOf (-1)
    private val lastSelectedState = mutableIntStateOf (-1)
    private val doStateAnimation = mutableStateOf(true)
    private val updateTitle = mutableStateOf(false)
    private val prevUpdateTitle = mutableStateOf(false)

    private val setListenerMethod = Reflection.getMethods(AppWidgetHost::class.java).find { it.name == "setListener"}

    override fun onResume() {
        widgetHost?.startListening()
        super.onResume()
    }

    override fun onPause() {
        widgetHost?.stopListening()
        super.onPause()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAttach(context : Context) {
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

        super.onAttach(context)
    }

    override fun onAttachedAndBound() {
        attachedAndBound = true
        loadWidgets()

        if (fragmentArg != null) {
            handleShortcutRequest()
        }
    }

    override fun onArgument() {
        if (attachedAndBound) {
            handleShortcutRequest()
        }
    }

    fun onAppPropsChange(widgetId: Int, androidWidgetId: Int, data: Dict) {
        val index = _widgetGridList.indexOfFirst { it.widgetId == androidWidgetId }
        if (index != -1) {
            if (data.hasKey("title")) {
                _widgetGridList[index].title = data.getString("title")
            }
            if (data.hasKey("icons")) {
                _widgetGridList[index].icons = data.getDict("icons")
            }

            _widgetGridList[index] = _widgetGridList[index]
            prevUpdateTitle.value = false
            updateTitle.value = true
        }
    }

    fun onWidgetChosen(widgetId: Int, androidWidgetId: Int, name: String?) {
        val index = _widgetGridList.indexOfFirst { it.widgetId == androidWidgetId }
        if (index != -1) {
            _widgetGridList[index].name = name
            _widgetGridList[index] = _widgetGridList[index]
            prevUpdateTitle.value = false
            updateTitle.value = true
        }
    }

    override fun onActivityResult(data: Intent) {
        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) {
            Log.e("APPY", "no widget id on bindapp permission request")
            return
        }

        addNewWidgetToList(widgetId)
    }

    private fun startNewWidget(widgetId : Int, widgetUnique : Int) : WidgetItem
    {
        val frame = ScaleLayout(requireActivity())
        val widgetView = widgetHost!!.createView(requireActivity().applicationContext, widgetId, appyInfo)

        //frame.setBackgroundResource(R.drawable.drawable_outline_success_btn)
        //widgetView.setBackgroundResource(R.drawable.drawable_outline_danger_btn)

        val item = WidgetItem(widgetId, widgetUnique, frame, widgetView)

        item.frame.tag = item

        item.frame.addView(widgetView)

        return item
    }

    private fun removeWidget(widgetId : Int) {
        val item = _widgetGridList.find {it.widgetId == widgetId}
        if (item != null) {
            _widgetGridList.remove(item)
            saveWidgets()
        }
        if (selectedState.intValue == widgetId) {
            lastSelectedState.intValue = selectedState.intValue
            selectedState.intValue = -1
            doStateAnimation.value = true
        }
        widgetHost!!.deleteAppWidgetId(widgetId)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (widgetId in widgetHost!!.appWidgetIds) {
                widgetHost!!.deleteAppWidgetId(widgetId)
            }
        }

        _widgetGridList.clear()
        lastSelectedState.intValue = -1;
        selectedState.intValue = -1;
        doStateAnimation.value = true
        saveWidgets()
    }

    private fun addWidget()
    {
        val widgetId = widgetHost!!.allocateAppWidgetId()
        val options = Bundle()

        if (!AppWidgetManager.getInstance(requireActivity())
                .bindAppWidgetIdIfAllowed(widgetId, appyInfo!!.provider, options)
        ) {
            requestPermission(widgetId, appyInfo!!.provider, options)
        } else {
            addNewWidgetToList(widgetId)
        }
    }

    private fun addNewWidgetToList(widgetId : Int) {
        val widgetUnique = Random.nextInt(0, Int.MAX_VALUE)
        _widgetGridList.add(startNewWidget(widgetId, widgetUnique))
        selectWidget(widgetId, false)
        saveWidgets()
    }

    private fun selectWidget(widgetId : Int, animate : Boolean = true) {
        lastSelectedState.intValue = selectedState.intValue
        selectedState.intValue = widgetId
        doStateAnimation.value = animate

        val prevId = lastSelectedState.intValue

        if (widgetId == -1) {
            val item = _widgetGridList.find {prevId == it.widgetId }
            if (item != null && item.name == null) {
                removeWidget(prevId)
            }
        }
    }

    private fun handleShortcutRequest() {
        val launchedWidgetId = fragmentArg?.getInt(Constants.FRAGMENT_ARG_WIDGET_ID, -1) ?: -1
        val launchedWidgetUnique = fragmentArg?.getInt(Constants.FRAGMENT_ARG_WIDGET_UNIQUE, -1) ?: -1

        if (launchedWidgetId != -1 && launchedWidgetUnique != -1) {
            if (_widgetGridList.find {it.widgetId == launchedWidgetId && it.widgetUnique == launchedWidgetUnique} != null) {
                selectWidget(launchedWidgetId, true)
            }
        }
    }

    private fun saveWidgets()
    {
        val widgets = DictObj.List()
        for (item in _widgetGridList) {
            widgets.add(Dict().apply {
                put("id", item.widgetId)
                put("unique", item.widgetUnique)
            })
        }

        val store = StoreData.Factory.create(requireActivity(), "app_fragment_widgets")
        store.put("widgets", widgets)
        store.apply()
    }

    private fun loadWidgets()
    {
        val store = StoreData.Factory.create(requireActivity(), "app_fragment_widgets")
        val widgetsStored = store.getList("widgets")

        lastSelectedState.intValue = -1;
        selectedState.intValue = -1;
        doStateAnimation.value = false

        if (widgetsStored == null) {
            _widgetGridList.clear()
            return
        }

        val widgets = mutableListOf<WidgetItem>()
        val props = widgetService.getAllWidgetAppProps(true, true)

        for (i in 0..< widgetsStored.size()) {
            val widget = widgetsStored.getDict(i)
            val widgetId = widget.getInt("id", -1)
            val widgetUnique = widget.getInt("unique", -1)
            if (widgetId == -1 || widgetUnique == -1 || !props.hasKey(widgetId.toString()))
            {
                continue
            }

            val widgetProps = props.getDict(widgetId.toString())
            val name = widgetProps.getString("name")

            if (name == null) {
                removeWidget(widgetId)
            }

            val item = startNewWidget(widgetId, widgetUnique)
            item.title = widgetProps.getString("title")
            item.icons = widgetProps.getDict("icons")
            item.name = name
            widgets.add(item)
        }

        _widgetGridList.apply {
            clear()
            addAll(widgets)
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

        val options = Bundle()
        options.putBoolean(OPTION_APPWIDGET_APPY_APP, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            widgetItem.widgetView.updateAppWidgetSize(options, sizes)
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

        setHasOptionsMenu(true)
        setMenuVisibility(false)
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.apps_toolbar_actions, menu)
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean {
        if (item.itemId == R.id.action_minimize) {
            selectWidget(-1)
            return true
        }
        else if (item.itemId == R.id.action_delete) {
            val widgetId = selectedState.intValue
            if (widgetId != -1) {
                val title = selectedWidgetTitle().ifEmpty { "app" }
                Utils.showConfirmationDialog(requireActivity(), "Delete App", "Delete $title?", android.R.drawable.ic_dialog_alert,
                    null, null
                ) {
                    removeWidget(widgetId)
                }
            }
        }
        else if (item.itemId == R.id.action_shortcut) {
            val widgetId = selectedState.intValue
            if (widgetId != -1) {
                val widgetItem = _widgetGridList.find { it.widgetId == widgetId }
                if (widgetItem != null) {
                    makeShortcut(widgetItem)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun makeShortcut(widgetItem : WidgetItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(requireActivity(), "Shortcuts are not supported", Toast.LENGTH_SHORT).show()
            return
        }

        val applicationContext = requireActivity().applicationContext
        val shortcutManager = getSystemService(applicationContext, ShortcutManager::class.java)

        if (shortcutManager!!.isRequestPinShortcutSupported) {
            val intent = Intent(applicationContext, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            .putExtra(Constants.FRAGMENT_NAME_EXTRA, "apps")
                            .putExtra(Constants.FRAGMENT_ARG_WIDGET_ID, widgetItem.widgetId)
                            .putExtra(Constants.FRAGMENT_ARG_WIDGET_UNIQUE, widgetItem.widgetUnique)

            val icon = if (widgetItem.shortcutIcon != null)
                            android.graphics.drawable.Icon.createWithAdaptiveBitmap(widgetItem.shortcutIcon!!)
                        else
                            android.graphics.drawable.Icon.createWithResource(applicationContext, R.drawable.app_default_shortcut)

            val pinShortcutInfo = ShortcutInfo.Builder(applicationContext, "appy_app_${widgetItem.widgetId}_${widgetItem.widgetUnique}_${Random.nextInt(0, Int.MAX_VALUE)}")
                                    .setIntent(intent)
                                    .setShortLabel(widgetTitle(widgetItem))
                                    .setIcon(icon)
                                    .build()

            val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo)

            val successCallback = PendingIntent.getBroadcast(applicationContext, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE)

            shortcutManager.requestPinShortcut(pinShortcutInfo,
                successCallback.intentSender)
        }
    }

    fun setTitle(title : String) {
        (activity as MainActivity?)?.supportActionBar?.title = title
    }

    fun widgetTitle(item : WidgetItem?) : String {
        if (item == null) {
            return ""
        }
        return item.title ?: item.name?.replaceFirstChar(Char::titlecase) ?: ""
    }

    fun selectedWidgetTitle() : String {
        return widgetTitle(_widgetGridList.find {it.widgetId == selectedState.intValue})
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
        if (selectedState == -1 && lastSelectedState != -1) {
            setTitle("Apps")
            setMenuVisibility(false)
        }

        val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
            if (from != to) {
                _widgetGridList.add(to.index, _widgetGridList.removeAt(from.index))
                saveWidgets()
            }

            ViewCompat.performHapticFeedback(
                containerView,
                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK
            )
        }

        with (sharedTransitionScope) {
            Box (modifier = Modifier.fillMaxSize()) {
                LargeFloatingActionButton(onClick = {
                    addWidget()
                },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .zIndex(10f)) {
                    Icon(Icons.Filled.Add, "New widget")
                }
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

                            Card(
                                onClick = {},
                                colors = CardColors(
                                    containerColor = Color.DarkGray,
                                    contentColor = Color.DarkGray,
                                    disabledContainerColor = Color.DarkGray,
                                    disabledContentColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(5.dp)
                                    .aspectRatio(0.8f)
                                    .draggableHandle(
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {

                                        if (item.menuIcon != null) {
                                            Image(
                                                bitmap = item.menuIcon!!.asImageBitmap(),
                                                contentDescription = "Icon",
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                                contentDescription = "Icon",
                                                colorFilter = ColorFilter.tint(Color.White),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        BasicText(
                                            text = widgetTitle(item),
                                            color = { Color.White },
                                            style = TextStyle(textAlign = TextAlign.Center),
                                            modifier = Modifier
                                                .padding(
                                                    horizontal = 0.dp,
                                                    vertical = 5.dp
                                                )
                                                .weight(2f),
                                            autoSize = TextAutoSize.StepBased(
                                                minFontSize = 10.sp,
                                                maxFontSize = 60.sp,
                                                stepSize = 10.sp
                                            )
                                        )

                                        Box(modifier = Modifier.weight(1f))
                                    }

                                    val aspect = fullSize.width.toFloat() / fullSize.height.toFloat()

                                    Box(
                                        modifier = Modifier
                                            .wrapContentWidth()
                                            .fillMaxHeight()
                                            .aspectRatio(aspect)
                                            .weight(3f)
                                    ) {
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

                                        Box(modifier = Modifier
                                            .background(Color.Transparent)
                                            .matchParentSize()
                                            .clickable { onWidgetClick(item) })
                                    }
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
              doStateAnimation : Boolean,
              updateTitle : Boolean,
              prevUpdateTitle : Boolean,
              fullSize: IntSize,
              sharedTransitionScope: SharedTransitionScope,
              animatedVisibilityScope: AnimatedVisibilityScope?) {
        with (sharedTransitionScope)
        {
            val properState = if (selectedState != -1) selectedState else lastSelectedState
            var modifier = Modifier.fillMaxSize()

            if (doStateAnimation && hasListenerMethod()) {
                modifier = modifier.sharedElement(
                    rememberSharedContentState(key = "widget_$properState"),
                    animatedVisibilityScope = animatedVisibilityScope!!
                )
            }

            val title = selectedWidgetTitle()

            if (!prevUpdateTitle && updateTitle) {
                setTitle(title)
            }

            if (selectedState != -1 && lastSelectedState == -1) {
                setTitle(title)
                setMenuVisibility(true)
            }

            AndroidView (
                factory = {
                    val widgetItem = if (hasListenerMethod()) {
                        val gridWidgetItem = _widgetGridList.first {it.widgetId == selectedState}
                        startNewWidget(gridWidgetItem.widgetId, gridWidgetItem.widgetUnique)
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

        val selectedStateInt by selectedState
        val lastSelectedStateInt by lastSelectedState
        val doStateAnimationBoolean by doStateAnimation
        val updateTitleBoolean by updateTitle
        val prevUpdateTitleBoolean by prevUpdateTitle

        var fullSize by remember { mutableStateOf(IntSize(1, 1)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { fullSize = it.size }
            ){
                val content = @Composable { targetState : Int, animatedContent : AnimatedVisibilityScope? ->

                    if (targetState != -1) {
                        Fully(
                            selectedStateInt,
                            lastSelectedStateInt,
                            doStateAnimationBoolean,
                            updateTitleBoolean,
                            prevUpdateTitleBoolean,
                            fullSize,
                            animatedVisibilityScope = animatedContent,
                            sharedTransitionScope = this@SharedTransitionLayout,
                        )
                    } else {
                        Grid(selectedStateInt,
                            lastSelectedStateInt,
                            fullSize,
                            onWidgetClick = {
                                selectWidget(it.widgetId)
                            },
                            containerView,
                            lazyGridState,
                            animatedVisibilityScope = animatedContent,
                            sharedTransitionScope = this@SharedTransitionLayout)
                    }

                    prevUpdateTitle.value = updateTitle.value
                }

                if (hasListenerMethod()) {
                    AnimatedContent(
                        selectedStateInt,
                        label = "basic_transition"
                    ) { targetState ->
                        content(targetState, this@AnimatedContent)
                    }
                } else {
                    content(selectedStateInt, null)
                }
            }
        }
    }
}