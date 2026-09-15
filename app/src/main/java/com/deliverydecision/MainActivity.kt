package com.deliverydecision

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("dd", MODE_PRIVATE) }
    private val ui = Handler(Looper.getMainLooper())

    private val green = Color.rgb(180,255,0)
    private val brightGreen = Color.rgb(195,255,20)
    private val red = Color.rgb(255,55,45)
    private val dim = Color.rgb(195,205,195)
    private val cardFill = Color.argb(236,2,10,6)
    private val cardStroke = Color.rgb(118,180,35)

    private lateinit var pageHost: FrameLayout
    private lateinit var nav: LinearLayout
    private var currentTab = "Home"

    private var liveEarnings: TextView? = null
    private var liveMiles: TextView? = null
    private var liveTime: TextView? = null
    private var liveStatus: TextView? = null
    private var liveAuto: TextView? = null
    private var activeMetric: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(2,8,2)
        window.navigationBarColor = Color.rgb(2,8,2)
        sanitizeHistoryDurations()
        buildShell()
        showTab("Home")
        ui.post(refresh)
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (currentTab == "Home") updateHomeLive()
            ui.postDelayed(this, 1000L)
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(1,5,1))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(4), dp(2), dp(5))
            background = rounded(Color.rgb(2,8,4), cardStroke, 1, 13f)
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(66)))
        setContentView(root)
    }

    private fun showTab(tab: String) {
        currentTab = tab
        pageHost.removeAllViews()
        pageHost.addView(pageBackground())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(14))
        }
        scroll.addView(content)
        pageHost.addView(scroll, FrameLayout.LayoutParams(-1,-1))

        when (tab) {
            "Home" -> buildHome(content)
            "History" -> buildHistory(content)
            "Reports" -> buildReports(content)
            "Accounts" -> buildAccounts(content)
            "Dasher Status" -> buildDasherStatus(content)
            "Settings" -> buildSettings(content)
            "Taxes" -> buildTaxes(content)
        }
        rebuildNav()
    }

    private fun pageBackground(): View {
        val frame = FrameLayout(this)
        val bg = ImageView(this).apply {
            setImageResource(R.drawable.money_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        frame.addView(bg, FrameLayout.LayoutParams(-1,-1))
        frame.addView(View(this).apply {
            setBackgroundColor(Color.argb(22,0,0,0))
        }, FrameLayout.LayoutParams(-1,-1))
        return frame
    }

    // Reference mockup uses the DD/TAKE emblem as the persistent page header.
    private fun buildHeader(parent: LinearLayout) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(7))
        }
        val halo = FrameLayout(this).apply {
            background = rounded(Color.argb(205,0,18,2), brightGreen, 2, 42f)
            setPadding(dp(4),dp(4),dp(4),dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.BLACK, brightGreen, 1, 34f)
        }
        halo.addView(icon, FrameLayout.LayoutParams(dp(84),dp(84),Gravity.CENTER))
        wrap.addView(halo, LinearLayout.LayoutParams(dp(94),dp(94)))
        parent.addView(wrap)
    }

    private fun buildHome(p: LinearLayout) {
        buildHeader(p)

        val status = card()
        val sr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        liveStatus = txt("",13f,green,true)
        sr.addView(liveStatus, LinearLayout.LayoutParams(0,-2,1f))
        liveTime = txt("00:00:00",17f,green,true)
        sr.addView(liveTime)
        status.addView(sr)
        p.addView(status)
        gap(p,8)

        val summary = card()
        summary.addView(title("TODAY'S SUMMARY"))
        summary.addView(div())

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val e = mockMetric("EARNINGS", "$0.00", "")
        liveEarnings = e.second
        val m = mockMetric("MILES", "0.0", "${f1(prefs.getFloat("mpg",20f).toDouble())} mpg")
        liveMiles = m.second
        val a = mockMetric("ACTIVE TIME", "00:00", "of 00:00")
        activeMetric = a.second
        metrics.addView(e.first, LinearLayout.LayoutParams(0,-2,1f))
        metrics.addView(m.first, LinearLayout.LayoutParams(0,-2,1f))
        metrics.addView(a.first, LinearLayout.LayoutParams(0,-2,1f))
        summary.addView(metrics)

        val dashRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dashBtn = neonButton(if (prefs.getBoolean("dash_active",false)) "END DASH" else "START DASH")
        dashBtn.setOnClickListener {
            if (prefs.getBoolean("dash_active",false)) startService(Intent(this,MileageService::class.java).setAction("STOP"))
            else startDash()
        }
        dashRow.addView(dashBtn, LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd = dp(7) })
        val stop = outlineButton("■", green)
        stop.setOnClickListener { startService(Intent(this,MileageService::class.java).setAction("STOP")) }
        dashRow.addView(stop, LinearLayout.LayoutParams(dp(54),dp(48)))
        summary.addView(dashRow, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(8) })
        p.addView(summary)
        gap(p,6)

        val addRecord = outlineButton("+  ADD DASH RECORD", green)
        addRecord.setOnClickListener { showManualRecordDialog() }
        p.addView(addRecord, LinearLayout.LayoutParams(-1, dp(46)))
        gap(p,8)

        val statusCard = card().apply {
            setOnClickListener { showTab("Dasher Status") }
        }
        val statusRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        statusRow.addView(txt("D",22f,red,true).apply { gravity=Gravity.CENTER }, LinearLayout.LayoutParams(dp(44),dp(44)))
        val stText=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        stText.addView(txt("DoorDash",14f,Color.WHITE,true))
        stText.addView(txt("Dasher Status",10.5f,dim,false))
        statusRow.addView(stText,LinearLayout.LayoutParams(0,-2,1f))
        val tier=prefs.getString("dd_tier","Platinum") ?: "Platinum"
        val overall=prefs.getInt("dd_overall",90)
        statusRow.addView(txt("$tier\n$overall / 100   ›",12f,green,true).apply { gravity=Gravity.CENTER })
        statusCard.addView(statusRow)
        p.addView(statusCard)
        gap(p,8)

        val dd = driverLaunch("DOORDASH","OPEN", red)
        dd.setOnClickListener { launchDriverApp(listOf("com.doordash.driverapp"),"DoorDash") }
        p.addView(dd, LinearLayout.LayoutParams(-1,dp(54)))
        gap(p,8)

        val offer = card()
        val or = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ot.addView(title("OFFER READER"))
        ot.addView(txt("Analyze offers and get take / pass recommendations.",11f,dim,false))
        or.addView(ot, LinearLayout.LayoutParams(0,-2,1f))
        val sw = Switch(this).apply {
            isChecked = isAccessibilityEnabled()
            buttonTintList = null
            setOnCheckedChangeListener { _, checked ->
                if (checked != isAccessibilityEnabled()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        or.addView(sw)
        offer.addView(or)
        offer.addView(div())
        val rules = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val r1 = tinyRule("◷","Min $/mile","$${f2(prefs.getFloat("min_dpm",1.5f).toDouble())}")
        val r2 = tinyRule("⌂","Preferred $/mile","$${f2(prefs.getFloat("preferred_dpm",2f).toDouble())}")
        val r3 = tinyRule("◷","Min $/hour","$${f2(prefs.getFloat("target_hour",25f).toDouble())}")
        rules.addView(r1,LinearLayout.LayoutParams(0,-2,1f))
        rules.addView(r2,LinearLayout.LayoutParams(0,-2,1f))
        rules.addView(r3,LinearLayout.LayoutParams(0,-2,1f))
        offer.addView(rules)
        p.addView(offer)
        gap(p,8)

        val auto = card()
        val ar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(title("AUTO-STOP TIMER"))
        left.addView(txt("No offers or movement for ${prefs.getInt("auto_stop_min",30)} minutes",11f,dim,false))
        ar.addView(left,LinearLayout.LayoutParams(0,-2,1f))
        liveAuto = txt("${prefs.getInt("auto_stop_min",30)}:00",18f,green,true)
        ar.addView(liveAuto)
        auto.addView(ar)
        p.addView(auto)

        updateHomeLive()
    }

    private fun updateHomeLive() {
        val active = prefs.getBoolean("dash_active",false)
        val start = prefs.getLong("dash_start",0L)
        val ms = if (active && start > 0) System.currentTimeMillis()-start else prefs.getLong("last_session_ms",0L)
        liveStatus?.text = if (active) "●  DASH ACTIVE" else "○  DASH NOT ACTIVE"
        liveStatus?.setTextColor(if(active) green else dim)
        liveTime?.text = formatDuration(ms)
        liveEarnings?.text = "$${f2(prefs.getFloat("today_earnings",0f).toDouble())}"
        liveMiles?.text = f1(prefs.getFloat("today_miles",0f).toDouble())
        activeMetric?.text = hhmm(ms)
        liveAuto?.text = "${prefs.getInt("auto_stop_min",30)}:00"
    }

    private fun buildHistory(p: LinearLayout) {
        buildHeader(p)

        var selected = "ALL"
        val filterCard = card()
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val buttons = linkedMapOf<String,Button>()

        fun paint() {
            buttons.forEach { (name,b) ->
                b.background = if (name==selected) rounded(green,green,0,8f) else rounded(Color.rgb(8,11,8),Color.rgb(65,75,65),1,8f)
                b.setTextColor(if(name==selected) Color.BLACK else Color.WHITE)
                b.setTypeface(b.typeface, if(name==selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        fun deleteLine(line: String) {
            val lines = prefs.getString("dash_history","").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
            if (lines.remove(line)) prefs.edit().putString("dash_history", if(lines.isEmpty()) "" else lines.joinToString("\n", postfix="\n")).apply()
        }

        fun showSession(line:String) {
            val x=line.split("|")
            if(x.size<3) return
            val st=x[0].toLongOrNull()?:0L
            val en=x[1].toLongOrNull()?:st
            val mi=x[2].toDoubleOrNull()?:0.0
            val platform=if(x.size>=4) x[3] else "Delivery"
            val earn=if(x.size>=5) x[4].toDoubleOrNull()?:0.0 else 0.0
            AlertDialog.Builder(this)
                .setTitle("$platform session")
                .setMessage("${SimpleDateFormat("EEE, MMM d • h:mm a",Locale.US).format(Date(st))}\n${f1(mi)} miles • ${hhmm(en-st)}\nEarnings: $${f2(earn)}")
                .setPositiveButton("CLOSE",null)
                .setNegativeButton("DELETE SESSION") { _,_ ->
                    deleteLine(line)
                    showTab("History")
                }.show()
        }

        fun render() {
            host.removeAllViews()
            val raw = prefs.getString("dash_history","").orEmpty().lines().filter { it.isNotBlank() }.takeLast(80).reversed()
            val filtered = raw.filter { line ->
                val x=line.split("|")
                val platform=if(x.size>=4) x[3].uppercase(Locale.US) else "UNKNOWN"
                selected=="ALL" || (selected=="DOORDASH" && platform=="DOORDASH")
            }
            if(filtered.isEmpty()) {
                val c=card()
                c.addView(txt("No $selected sessions yet.",14f,dim,false))
                host.addView(c)
                return
            }

            val cal = Calendar.getInstance()
            val startThisWeek = Calendar.getInstance().apply {
                firstDayOfWeek=Calendar.SUNDAY
                set(Calendar.DAY_OF_WEEK,Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
            }.timeInMillis
            var addedThis=false
            var addedLast=false

            filtered.forEach { line ->
                val x=line.split("|")
                if(x.size<3) return@forEach
                val st=x[0].toLongOrNull()?:0L
                val en=x[1].toLongOrNull()?:st
                val mi=x[2].toDoubleOrNull()?:0.0
                val platform=if(x.size>=4) x[3] else "Delivery"
                val earn=if(x.size>=5) x[4].toDoubleOrNull()?:0.0 else 0.0

                if(st>=startThisWeek && !addedThis) {
                    host.addView(sectionLabel("THIS WEEK"))
                    addedThis=true
                } else if(st<startThisWeek && !addedLast) {
                    host.addView(sectionLabel("LAST WEEK"))
                    addedLast=true
                }

                val c = card()
                val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
                val logo=txt(if(platform.equals("DoorDash",true)) "D" else "OLD",12f,if(platform.equals("DoorDash",true)) red else dim,true).apply {
                    gravity=Gravity.CENTER
                }
                row.addView(logo,LinearLayout.LayoutParams(dp(42),dp(42)))
                val center=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                center.addView(txt(SimpleDateFormat("EEE, MMM d",Locale.US).format(Date(st)),14f,Color.WHITE,true))
                center.addView(txt("${SimpleDateFormat("h:mm a",Locale.US).format(Date(st))} - ${SimpleDateFormat("h:mm a",Locale.US).format(Date(en))}",11f,Color.WHITE,false))
                center.addView(txt("${f1(mi)} mi  •  ${hhmm(en-st)} dash",10.5f,dim,false))
                row.addView(center,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(txt(if(earn>0) "$${f2(earn)}" else "${f1(mi)} mi",14f,green,true))
                row.addView(txt("  ›",23f,Color.WHITE,false))
                c.addView(row)
                c.setOnClickListener { showSession(line) }
                c.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle("Delete this session?")
                        .setMessage("This removes it from History and Reports.")
                        .setPositiveButton("DELETE"){_,_->deleteLine(line);showTab("History")}
                        .setNegativeButton("CANCEL",null).show()
                    true
                }
                host.addView(c)
                gap(host,7)
            }
        }

        listOf("ALL","DOORDASH").forEachIndexed { i,name ->
            val b=darkButton(name)
            b.setOnClickListener { selected=name; paint(); render() }
            buttons[name]=b
            filterRow.addView(b,LinearLayout.LayoutParams(0,dp(42),1f).apply { if(i>0) marginStart=dp(5) })
        }
        filterCard.addView(filterRow)
        p.addView(filterCard)
        gap(p,8)
        p.addView(host)
        paint()
        render()
    }

    private fun buildReports(p: LinearLayout) {
        buildHeader(p)

        var selected = prefs.getString("report_period","DAY") ?: "DAY"
        var offset = prefs.getInt("report_offset_$selected",0)

        val buttons=linkedMapOf<String,Button>()
        val periodCard=card()
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val host=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }

        fun paint() {
            buttons.forEach { (name,b) ->
                b.background=if(name==selected) rounded(green,green,0,8f) else rounded(Color.rgb(8,11,8),Color.rgb(65,75,65),1,8f)
                b.setTextColor(if(name==selected) Color.BLACK else Color.WHITE)
            }
        }

        fun bounds(name:String, off:Int):Triple<Long,Long,String> {
            val c=Calendar.getInstance()
            when(name) {
                "DAY" -> {
                    c.add(Calendar.DAY_OF_YEAR,off)
                    c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0)
                    val start=c.timeInMillis
                    val label=SimpleDateFormat("MMM d, yyyy",Locale.US).format(c.time)
                    c.add(Calendar.DAY_OF_YEAR,1)
                    return Triple(start,c.timeInMillis,label)
                }
                "WEEK" -> {
                    c.firstDayOfWeek=Calendar.SUNDAY
                    c.set(Calendar.DAY_OF_WEEK,Calendar.SUNDAY)
                    c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0)
                    c.add(Calendar.WEEK_OF_YEAR,off)
                    val start=c.timeInMillis
                    val s=SimpleDateFormat("MMM d",Locale.US).format(c.time)
                    c.add(Calendar.DAY_OF_YEAR,6)
                    val e=SimpleDateFormat("MMM d",Locale.US).format(c.time)
                    c.add(Calendar.DAY_OF_YEAR,1)
                    return Triple(start,c.timeInMillis,"$s – $e")
                }
                "MONTH" -> {
                    c.set(Calendar.DAY_OF_MONTH,1)
                    c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0)
                    c.add(Calendar.MONTH,off)
                    val start=c.timeInMillis
                    val label=SimpleDateFormat("MMMM yyyy",Locale.US).format(c.time).uppercase(Locale.US)
                    c.add(Calendar.MONTH,1)
                    return Triple(start,c.timeInMillis,label)
                }
                else -> {
                    c.set(Calendar.DAY_OF_YEAR,1)
                    c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0)
                    c.add(Calendar.YEAR,off)
                    val start=c.timeInMillis
                    val label=SimpleDateFormat("yyyy",Locale.US).format(c.time)
                    c.add(Calendar.YEAR,1)
                    return Triple(start,c.timeInMillis,label)
                }
            }
        }

        fun render() {
            host.removeAllViews()
            val (startMs,endMs,label)=bounds(selected,offset)
            val history=prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}
            var miles=0.0
            var ms=0L
            var earnings=0.0
            var deliveries=0

            history.forEach { line ->
                val x=line.split("|")
                if(x.size>=3) {
                    val st=x[0].toLongOrNull()?:0L
                    val en=x[1].toLongOrNull()?:st
                    if(en>=startMs && st<endMs) {
                        miles += x[2].toDoubleOrNull()?:0.0
                        ms += validSessionDuration(st,en)
                        if(x.size>=5) earnings += x[4].toDoubleOrNull()?:0.0
                        if(x.size>=6) deliveries += x[5].toIntOrNull()?:0
                    }
                }
            }

            val hours=ms/3600000.0
            val dpm=if(miles>0) earnings/miles else 0.0
            val dph=if(hours>0) earnings/hours else 0.0

            val dateCard=card()
            val dr=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
            val prev=txt("‹",30f,Color.WHITE,true).apply {
                gravity=Gravity.CENTER
                setPadding(dp(8),dp(6),dp(8),dp(6))
                setOnClickListener {
                    offset--
                    prefs.edit().putInt("report_offset_$selected",offset).apply()
                    render()
                }
            }
            val next=txt("›",30f,Color.WHITE,true).apply {
                gravity=Gravity.CENTER
                setPadding(dp(8),dp(6),dp(8),dp(6))
                setOnClickListener {
                    offset++
                    prefs.edit().putInt("report_offset_$selected",offset).apply()
                    render()
                }
            }
            dr.addView(prev,LinearLayout.LayoutParams(dp(48),dp(48)))
            dr.addView(txt(label,15f,Color.WHITE,true).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(0,dp(48),1f))
            dr.addView(next,LinearLayout.LayoutParams(dp(48),dp(48)))
            dateCard.addView(dr)
            host.addView(dateCard)
            gap(host,7)

            val totals=card()
            val row1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            row1.addView(statTile("EARNINGS","$${f2(earnings)}"),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginEnd=dp(4)})
            row1.addView(statTile("MILES",f1(miles)),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginStart=dp(2);marginEnd=dp(2)})
            row1.addView(statTile("HOURS",f2(hours)),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginStart=dp(4)})
            totals.addView(row1)
            val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            row2.addView(statTile("$/MILE","$${f2(dpm)}"),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginEnd=dp(4)})
            row2.addView(statTile("$/HOUR","$${f2(dph)}"),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginStart=dp(2);marginEnd=dp(2)})
            row2.addView(statTile("DELIVERIES",deliveries.toString()),LinearLayout.LayoutParams(0,dp(82),1f).apply{marginStart=dp(4)})
            totals.addView(row2,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
            host.addView(totals)
            gap(host,7)

            val graph=card()
            graph.addView(title("EARNINGS OVER TIME"))
            graph.addView(div())
            val bars=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.BOTTOM }
            val dayFmt=SimpleDateFormat("yyyy-MM-dd",Locale.US)
            val labelFmt=SimpleDateFormat("EEE",Locale.US)
            val daily=linkedMapOf<String,Double>()
            val labels=linkedMapOf<String,String>()
            val base=Calendar.getInstance().apply { timeInMillis=startMs }
            val days=when(selected) {
                "DAY" -> 1
                "WEEK" -> 7
                "MONTH" -> minOf(base.getActualMaximum(Calendar.DAY_OF_MONTH),7)
                else -> 7
            }
            for(i in 0 until days){
                val c=base.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR,i)
                val key=dayFmt.format(c.time)
                daily[key]=0.0
                labels[key]=if(selected=="DAY") "Day" else labelFmt.format(c.time)
            }
            history.forEach { line ->
                val x=line.split("|")
                if(x.size>=5){
                    val st=x[0].toLongOrNull()?:0L
                    val key=dayFmt.format(Date(st))
                    if(daily.containsKey(key)) daily[key]=(daily[key]?:0.0)+(x[4].toDoubleOrNull()?:0.0)
                }
            }
            val maxDaily=(daily.values.maxOrNull()?:0.0).coerceAtLeast(1.0)
            daily.forEach { (key,value) ->
                val col=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
                col.addView(txt(if(value>0) "$${f2(value)}" else "",8f,dim,false).apply{gravity=Gravity.CENTER})
                val bar=View(this).apply { background=rounded(green,green,0,2f) }
                val bh=if(value<=0) dp(2) else (dp(82)*(value/maxDaily)).toInt().coerceAtLeast(dp(8))
                col.addView(bar,LinearLayout.LayoutParams(dp(22),bh))
                col.addView(txt(labels[key].orEmpty(),9f,Color.WHITE,false).apply{gravity=Gravity.CENTER})
                bars.addView(col,LinearLayout.LayoutParams(0,dp(122),1f))
            }
            graph.addView(bars)
            host.addView(graph)
            gap(host,7)

            val mpg=prefs.getFloat("mpg",20f).toDouble().coerceAtLeast(1.0)
            val gas=prefs.getFloat("gas",3.99f).toDouble()
            val maint=prefs.getFloat("maintenance_per_mile",0.12f).toDouble()
            val costs=card()
            costs.addView(title("COST ESTIMATES"))
            costs.addView(div())
            costs.addView(valueRow("Gas (${f1(mpg)} mpg)","$${f2(miles/mpg*gas)}"))
            costs.addView(valueRow("Est. Maintenance","$${f2(miles*maint)}"))
            host.addView(costs)
        }

        listOf("DAY","WEEK","MONTH","YEAR").forEachIndexed { i,name ->
            val b=darkButton(name)
            b.setOnClickListener {
                selected=name
                offset=prefs.getInt("report_offset_$selected",0)
                prefs.edit().putString("report_period",selected).apply()
                paint()
                render()
            }
            buttons[name]=b
            row.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply { if(i>0) marginStart=dp(4) })
        }
        periodCard.addView(row)
        p.addView(periodCard)
        gap(p,7)
        p.addView(host)
        paint()
        render()
    }

    private fun buildAccounts(p: LinearLayout) {
        buildHeader(p)
        p.addView(txt("Accounts",22f,Color.WHITE,true))
        gap(p,8)

        val dd=card()
        dd.setOnClickListener { launchDriverApp(listOf("com.doordash.driverapp"),"DoorDash") }
        val ddr=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        ddr.addView(txt("D",24f,red,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
        val ddt=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        ddt.addView(txt("DoorDash",15f,Color.WHITE,true))
        ddt.addView(txt("Dasher Status & Profile",10.5f,dim,false))
        ddr.addView(ddt,LinearLayout.LayoutParams(0,-2,1f))
        ddr.addView(txt("›",24f,Color.WHITE,true))
        dd.addView(ddr)
        p.addView(dd)
        gap(p,8)

        val status=card()
        status.setOnClickListener { showTab("Dasher Status") }
        val sr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        sr.addView(txt("☆",24f,green,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
        val st=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        st.addView(txt("Dasher Status",14f,Color.WHITE,true))
        st.addView(txt("${prefs.getString("dd_tier","Platinum")} • ${prefs.getInt("dd_overall",90)}/100",10.5f,dim,false))
        sr.addView(st,LinearLayout.LayoutParams(0,-2,1f))
        sr.addView(txt("›",24f,Color.WHITE,true))
        status.addView(sr)
        p.addView(status)
        gap(p,8)

        val info=card()
        info.addView(title("ACCOUNT INFO"))
        info.addView(div())
        info.addView(valueRow("Reader","DoorDash only"))
        info.addView(valueRow("Offer reader",if(isAccessibilityEnabled()) "Active" else "Needs access"))
        info.addView(valueRow("Ratings sync","Updates when Ratings is viewed"))
        p.addView(info)
        gap(p,8)

        val settings=card()
        settings.setOnClickListener { showTab("Settings") }
        settings.addView(settingRow("App Settings","Reader & notification settings") { showTab("Settings") })
        p.addView(settings)
    }

    private fun buildDasherStatus(p: LinearLayout) {
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val back=txt("‹",30f,green,true).apply {
            gravity=Gravity.CENTER
            setOnClickListener { showTab("Accounts") }
        }
        top.addView(back,LinearLayout.LayoutParams(dp(46),dp(46)))
        top.addView(txt("Dasher Status",18f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        val refresh=txt("↻",24f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER
            setOnClickListener {
                launchDriverApp(listOf("com.doordash.driverapp"),"DoorDash")
                Toast.makeText(this@MainActivity,"Open Ratings in DoorDash to refresh status.",Toast.LENGTH_LONG).show()
            }
        }
        top.addView(refresh,LinearLayout.LayoutParams(dp(46),dp(46)))
        p.addView(top)
        gap(p,6)

        val tier=prefs.getString("dd_tier","Platinum") ?: "Platinum"
        val overall=prefs.getInt("dd_overall",90)
        val hero=card()
        val hr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        hr.addView(txt("◆",34f,Color.rgb(35,105,255),true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(58),dp(58)))
        val ht=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        ht.addView(txt(tier,22f,Color.WHITE,true))
        ht.addView(txt("Overall rating  $overall / 100  ⓘ",11.5f,Color.WHITE,false))
        ht.addView(txt("Keep up the great work!",10f,dim,false))
        hr.addView(ht,LinearLayout.LayoutParams(0,-2,1f))
        hero.addView(hr)

        val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100
            this.progress=overall.coerceIn(0,100)
            progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(40,140,255))
            progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(70,75,70))
        }
        hero.addView(progress,LinearLayout.LayoutParams(-1,dp(12)).apply{topMargin=dp(10)})
        val levels=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("Silver\\n55 pts","Gold\\n75 pts","Platinum\\n85 pts").forEach{
            levels.addView(txt(it,10f,Color.WHITE,false).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f))
        }
        hero.addView(levels)

        val above=(overall-85).coerceAtLeast(0)
        val note=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(8),dp(10),dp(8))
            background=rounded(Color.rgb(16,55,20),Color.rgb(45,125,40),1,8f)
        }
        note.addView(txt("🏆  You're $above points above Platinum minimum",11.5f,green,true))
        note.addView(txt("Maintain your ratings to keep your Platinum status and top benefits.",9.5f,Color.WHITE,false))
        hero.addView(note,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        p.addView(hero)
        gap(p,10)

        p.addView(title("Your rating factors"))
        gap(p,6)
        val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        fun pair(left:View,right:View){
            val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            r.addView(left,LinearLayout.LayoutParams(0,dp(112),1f).apply{marginEnd=dp(4)})
            r.addView(right,LinearLayout.LayoutParams(0,dp(112),1f).apply{marginStart=dp(4)})
            grid.addView(r,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
        }
        pair(
            ratingTile("Acceptance rate","${prefs.getInt("dd_acceptance",66)}%","24 of 30 points","High"),
            ratingTile("Completion rate","${prefs.getInt("dd_completion",100)}%","15 of 15 points","Very high")
        )
        pair(
            ratingTile("On-time rate","${prefs.getInt("dd_ontime",100)}%","30 of 30 points","Very high"),
            ratingTile("Quality rate","${prefs.getInt("dd_quality",100)}%","10 of 10 points","Very high")
        )
        pair(
            ratingTile("Customer rating",f1(prefs.getFloat("dd_customer",4.8f).toDouble())+" ★","7 of 10 points","High"),
            ratingTile("Last 30-day orders",prefs.getInt("dd_30day_orders",152).toString(),"4 of 5 points","High")
        )
        p.addView(grid)

        val extra=card()
        extra.addView(title("ADDITIONAL INFO"))
        extra.addView(div())
        extra.addView(valueRow("Total deliveries",prefs.getInt("dd_lifetime_orders",417).toString()))
        extra.addView(valueRow("Last 30-day orders",prefs.getInt("dd_30day_orders",152).toString()))
        extra.addView(txt("Status values update locally when Delivery Decision can read the DoorDash Ratings/Profile screen.",10f,dim,false).apply{setPadding(0,dp(6),0,0)})
        p.addView(extra)
    }

    private fun ratingTile(label:String,value:String,points:String,state:String):View {
        return LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9),dp(8),dp(9),dp(8))
            background=rounded(Color.rgb(3,12,8),cardStroke,1,9f)
            addView(txt(label,10f,Color.WHITE,false))
            addView(txt(value,20f,Color.WHITE,true))
            addView(txt(points,9.5f,dim,false))
            addView(txt("■  $state",10f,green,true).apply{setPadding(0,dp(4),0,0)})
        }
    }

    private fun buildSettings(p: LinearLayout) {
        buildHeader(p)

        val vehicle=card()
        vehicle.addView(title("VEHICLE & COSTS"))
        vehicle.addView(div())
        vehicle.addView(settingRow("Vehicle MPG", f1(prefs.getFloat("mpg",20f).toDouble())) { editFloat("Vehicle MPG","mpg",20f) })
        vehicle.addView(settingRow("Gas Price / Gallon", "$${f2(prefs.getFloat("gas",3.99f).toDouble())}") { editFloat("Gas Price / Gallon","gas",3.99f) })
        vehicle.addView(settingRow("Maintenance / Mile", "$${f2(prefs.getFloat("maintenance_per_mile",0.12f).toDouble())}") { editFloat("Maintenance / Mile","maintenance_per_mile",0.12f) })
        p.addView(vehicle)
        gap(p,8)

        val targets=card()
        targets.addView(title("EARNINGS TARGETS"))
        targets.addView(div())
        targets.addView(settingRow("Minimum $ / Mile", "$${f2(prefs.getFloat("min_dpm",1.5f).toDouble())}") { editFloat("Minimum $ / Mile","min_dpm",1.5f) })
        targets.addView(settingRow("Preferred $ / Mile", "$${f2(prefs.getFloat("preferred_dpm",2f).toDouble())}") { editFloat("Preferred $ / Mile","preferred_dpm",2f) })
        targets.addView(settingRow("Minimum $ / Hour", "$${f2(prefs.getFloat("target_hour",25f).toDouble())}") { editFloat("Minimum $ / Hour","target_hour",25f) })
        p.addView(targets)
        gap(p,8)

        val auto=card()
        auto.addView(title("AUTO-STOP"))
        auto.addView(div())
        auto.addView(settingRow("Auto-stop after inactivity","${prefs.getInt("auto_stop_min",30)} minutes") {
            editInt("Auto-stop after inactivity","auto_stop_min",30,20,120)
        })
        auto.addView(txt("Dash will auto-end after no offers and no movement for the selected time.",11f,dim,false).apply{setPadding(0,dp(6),0,0)})
        p.addView(auto)
        gap(p,8)

        val notices=card()
        notices.addView(title("NOTIFICATIONS"))
        notices.addView(div())
        notices.addView(toggleRow("Offer Alerts","offer_alerts",true))
        notices.addView(toggleRow("Auto-stop Warning","autostop_warning",true))
        p.addView(notices)
    }

    private fun buildTaxes(p: LinearLayout) {
        buildHeader(p)

        val selectedYear=prefs.getInt("tax_year",Calendar.getInstance().get(Calendar.YEAR))

        val ex=card()
        ex.addView(title("EXPORT RECORDS"))
        ex.addView(div())
        ex.addView(txt("Export your earnings, mileage, deliveries and dash-time records for taxes.",11.5f,Color.WHITE,false))

        val csv=neonButton("EXPORT CSV")
        csv.setOnClickListener { exportTaxCsv() }
        ex.addView(csv,LinearLayout.LayoutParams(-1,dp(45)).apply{topMargin=dp(8)})

        val pdf=outlineButton("EXPORT PDF (SUMMARY)",green)
        pdf.setOnClickListener { exportTaxSummaryPdf(selectedYear) }
        ex.addView(pdf,LinearLayout.LayoutParams(-1,dp(45)).apply{topMargin=dp(6)})
        p.addView(ex)
        gap(p,8)

        val yearMiles=calculateMilesForYear(selectedYear)
        val log=card()
        log.addView(title("MILEAGE LOG"))
        log.addView(div())
        log.addView(settingRow("Total Miles ($selectedYear)",f1(yearMiles)) { showMileageRecords(selectedYear) })
        log.addView(settingRow("Deductible Miles",f1(yearMiles)) { showMileageRecords(selectedYear) })
        p.addView(log)
        gap(p,8)

        val taxYear=card()
        taxYear.addView(settingRow("TAX YEAR",selectedYear.toString()) { chooseTaxYear(selectedYear) })
        p.addView(taxYear)
        gap(p,8)

        val sum=card()
        sum.addView(title("TAX SUMMARY ($selectedYear)"))
        sum.addView(div())
        sum.addView(valueRow("Earnings","$${f2(calculateEarningsForYear(selectedYear))}"))
        sum.addView(valueRow("Miles",f1(yearMiles)))
        sum.addView(valueRow("Deliveries",calculateDeliveriesForYear(selectedYear).toString()))
        sum.addView(valueRow("Dash Hours",f2(calculateDashHoursForYear(selectedYear))))
        p.addView(sum)
    }

    private fun chooseTaxYear(current:Int) {
        val now=Calendar.getInstance().get(Calendar.YEAR)
        val years=(now downTo now-8).map { it.toString() }.toTypedArray()
        val checked=years.indexOf(current.toString()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Select tax year")
            .setSingleChoiceItems(years,checked){ d,which ->
                prefs.edit().putInt("tax_year",years[which].toInt()).apply()
                d.dismiss()
                showTab("Taxes")
            }
            .setNegativeButton("CANCEL",null)
            .show()
    }

    private fun showMileageRecords(year:Int) {
        val c=Calendar.getInstance().apply {
            set(Calendar.YEAR,year);set(Calendar.DAY_OF_YEAR,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        val start=c.timeInMillis
        c.add(Calendar.YEAR,1)
        val end=c.timeInMillis
        val rows=prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.mapNotNull { line ->
            val x=line.split("|")
            if(x.size<3) return@mapNotNull null
            val st=x[0].toLongOrNull()?:return@mapNotNull null
            if(st<start || st>=end) return@mapNotNull null
            val miles=x[2].toDoubleOrNull()?:0.0
            val platform=if(x.size>=4)x[3] else "Delivery"
            val earnings=if(x.size>=5)x[4].toDoubleOrNull()?:0.0 else 0.0
            "${SimpleDateFormat("MMM d",Locale.US).format(Date(st))} • $platform • ${f1(miles)} mi • $${f2(earnings)}"
        }
        AlertDialog.Builder(this)
            .setTitle("Mileage records $year")
            .setMessage(if(rows.isEmpty()) "No records for $year." else rows.joinToString("\n"))
            .setPositiveButton("OK",null)
            .show()
    }

    private fun calculateMilesForYear(year:Int):Double {
        val (start,end)=yearBounds(year)
        var total=0.0
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=3){
                val st=x[0].toLongOrNull()?:0L
                if(st in start until end) total+=x[2].toDoubleOrNull()?:0.0
            }
        }
        return total
    }

    private fun calculateEarningsForYear(year:Int):Double {
        val (start,end)=yearBounds(year)
        var total=0.0
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=5){
                val st=x[0].toLongOrNull()?:0L
                if(st in start until end) total+=x[4].toDoubleOrNull()?:0.0
            }
        }
        return total
    }

    private fun calculateDeliveriesForYear(year:Int):Int {
        val (start,end)=yearBounds(year)
        var total=0
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=6){
                val st=x[0].toLongOrNull()?:0L
                if(st in start until end) total+=x[5].toIntOrNull()?:0
            }
        }
        return total
    }

    private fun calculateDashHoursForYear(year:Int):Double {
        val (start,end)=yearBounds(year)
        var totalMs=0L
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=2){
                val st=x[0].toLongOrNull()?:0L
                val en=x[1].toLongOrNull()?:st
                if(st in start until end) totalMs+=validSessionDuration(st,en)
            }
        }
        return totalMs/3600000.0
    }

    private fun yearBounds(year:Int):Pair<Long,Long> {
        val c=Calendar.getInstance().apply {
            set(Calendar.YEAR,year);set(Calendar.DAY_OF_YEAR,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        val start=c.timeInMillis
        c.add(Calendar.YEAR,1)
        return Pair(start,c.timeInMillis)
    }

    private fun exportTaxSummaryPdf(year:Int) {
        val name="DeliveryDecision_TaxSummary_$year.pdf"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="application/pdf"
            putExtra(Intent.EXTRA_TITLE,name)
            putExtra("dd_pdf_year",year)
        },902)
        prefs.edit().putInt("pending_pdf_year",year).apply()
    }

    private fun rebuildNav() {
        nav.removeAllViews()
        listOf(
            "Home" to "⌂",
            "History" to "▣",
            "Reports" to "▥",
            "Accounts" to "♙",
            "Settings" to "⚙",
            "Taxes" to "▤"
        ).forEach { (name,ico) ->
            val item=TextView(this).apply {
                text="$ico\n$name"
                textSize=9.2f
                gravity=Gravity.CENTER
                val selectedNav = name==currentTab || (name=="Accounts" && currentTab=="Dasher Status")
                setTextColor(if(selectedNav) green else Color.WHITE)
                if(selectedNav) setTypeface(typeface,Typeface.BOLD)
                setOnClickListener { showTab(name) }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,-1,1f))
        }
    }

    private fun driverLaunch(name:String, action:String, color:Int):View {
        val b=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            background=rounded(Color.argb(235,2,8,4),color,2,9f)
            setPadding(dp(8),0,dp(8),0)
        }
        b.addView(txt(if(name.contains("DOOR")) "D" else "UE",13f,color,true).apply{
            gravity=Gravity.CENTER
            setPadding(0,0,dp(10),0)
        },LinearLayout.LayoutParams(dp(42),-1))
        b.addView(txt("$action\n$name",11f,Color.WHITE,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-1,1f))
        return b
    }

    private fun accountMockRow(mark:String,name:String,status:String,button:String,color:Int,onClick:()->Unit):View {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(3),0,dp(3)) }
        row.addView(txt(mark,16f,color,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(45),dp(42)))
        val text=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        text.addView(txt(name,13.5f,Color.WHITE,false))
        text.addView(txt(status,11f,if(status.contains("Active")) green else dim,false))
        row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
        val btn=outlineButton(button,color)
        btn.setOnClickListener { onClick() }
        row.addView(btn,LinearLayout.LayoutParams(dp(94),dp(42)))
        return row
    }

    private fun infoBox(icon:String,message:String):View {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(10))
            background=rounded(Color.rgb(3,12,8),Color.rgb(45,70,55),1,8f)
        }
        row.addView(txt(icon,18f,Color.CYAN,false),LinearLayout.LayoutParams(dp(36),-2))
        row.addView(txt(message,11f,Color.WHITE,false),LinearLayout.LayoutParams(0,-2,1f))
        return row
    }

    private fun settingRow(label:String,value:String,onClick:()->Unit):View {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,dp(6),0,dp(6))
            setOnClickListener { onClick() }
        }
        row.addView(txt(label,12.5f,Color.WHITE,false),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(txt(value,12.5f,green,false))
        row.addView(txt("  ›",17f,green,false))
        return row
    }

    private fun toggleRow(label:String,key:String,default:Boolean):View {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,dp(4)) }
        row.addView(txt(label,12.5f,Color.WHITE,false),LinearLayout.LayoutParams(0,-2,1f))
        val sw=Switch(this).apply {
            isChecked=prefs.getBoolean(key,default)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean(key,checked).apply() }
        }
        row.addView(sw)
        return row
    }

    private fun editFloat(title:String,key:String,default:Float) {
        val input=EditText(this).apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getFloat(key,default).toString())
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("SAVE"){_,_->
                input.text.toString().toFloatOrNull()?.let { prefs.edit().putFloat(key,it.coerceAtLeast(0f)).apply() }
                showTab("Settings")
            }.setNegativeButton("CANCEL",null).show()
    }

    private fun editInt(title:String,key:String,default:Int,min:Int,max:Int) {
        val input=EditText(this).apply {
            inputType=InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt(key,default).toString())
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("SAVE"){_,_->
                input.text.toString().toIntOrNull()?.let { prefs.edit().putInt(key,it.coerceIn(min,max)).apply() }
                showTab("Settings")
            }.setNegativeButton("CANCEL",null).show()
    }

    private fun mockMetric(label:String,value:String,sub:String):Pair<LinearLayout,TextView> {
        val b=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(2),dp(2),dp(2),dp(2)) }
        b.addView(txt(label,9.5f,Color.WHITE,false))
        val v=txt(value,18f,green,true).apply{gravity=Gravity.CENTER}
        b.addView(v)
        if(sub.isNotBlank()) b.addView(txt(sub,9.5f,dim,false).apply{gravity=Gravity.CENTER})
        return Pair(b,v)
    }

    private fun tinyRule(icon:String,label:String,value:String):View {
        val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
        b.addView(txt("$icon  $label",9.5f,Color.WHITE,false).apply{gravity=Gravity.CENTER})
        b.addView(txt(value,10.5f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
        return b
    }

    private fun statTile(label:String,value:String):View {
        return LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            background=rounded(Color.rgb(3,12,8),cardStroke,1,8f)
            addView(txt(label,10.5f,Color.WHITE,false).apply{gravity=Gravity.CENTER})
            addView(txt(value,17f,green,true).apply{gravity=Gravity.CENTER})
        }
    }

    private fun sectionLabel(s:String):View {
        return txt(s,12f,Color.WHITE,true).apply{setPadding(dp(4),dp(2),0,dp(5))}
    }


    private fun showManualRecordDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }

        val platform = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("DoorDash")
            )
        }

        val earnings = EditText(this).apply {
            hint = "Earnings (example 27.25)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val miles = EditText(this).apply {
            hint = "Miles (example 21.8)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val deliveries = EditText(this).apply {
            hint = "Deliveries (example 3)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val dashHours = EditText(this).apply {
            hint = "Dash hours (example 1)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val dashMinutes = EditText(this).apply {
            hint = "Dash minutes (example 37)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        box.addView(txt("PLATFORM", 11f, dim, true))
        box.addView(platform)
        box.addView(earnings)
        box.addView(miles)
        box.addView(deliveries)
        box.addView(dashHours)
        box.addView(dashMinutes)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add delivery record")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val earn = earnings.text.toString().trim().toDoubleOrNull() ?: 0.0
                val mi = miles.text.toString().trim().toDoubleOrNull() ?: 0.0
                val del = deliveries.text.toString().trim().toIntOrNull() ?: 0
                val hrs = dashHours.text.toString().trim().toIntOrNull() ?: 0
                val mins = dashMinutes.text.toString().trim().toIntOrNull() ?: 0

                if (earn < 0 || mi < 0 || del < 0 || hrs < 0 || mins < 0) {
                    Toast.makeText(this, "Use zero or positive numbers.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                if (mins > 59) {
                    Toast.makeText(this, "Dash minutes must be 0-59.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                if (earn == 0.0 && mi == 0.0 && del == 0 && hrs == 0 && mins == 0) {
                    Toast.makeText(this, "Enter at least one value.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                saveManualRecord(platform.selectedItem.toString(), earn, mi, del, hrs, mins)
                dialog.dismiss()
                showTab("Home")
                Toast.makeText(this, "Record added", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun saveManualRecord(platform:String, earnings:Double, miles:Double, deliveries:Int, hours:Int, minutes:Int) {
        val end = System.currentTimeMillis()
        val durationMs = ((hours * 60L) + minutes) * 60_000L
        val start = end - durationMs
        val existing = prefs.getString("dash_history","").orEmpty()
        val line = "$start|$end|$miles|$platform|$earnings|$deliveries\n"

        prefs.edit()
            .putString("dash_history", (existing + line).takeLast(24000))
            .putFloat("today_miles", prefs.getFloat("today_miles",0f) + miles.toFloat())
            .putFloat("week_miles", prefs.getFloat("week_miles",0f) + miles.toFloat())
            .putFloat("month_miles", prefs.getFloat("month_miles",0f) + miles.toFloat())
            .putFloat("today_earnings", prefs.getFloat("today_earnings",0f) + earnings.toFloat())
            .apply()
    }

    private fun startDash() {
        val req=mutableListOf<String>()
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) req+=Manifest.permission.ACCESS_FINE_LOCATION
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) req+=Manifest.permission.POST_NOTIFICATIONS
        if(req.isNotEmpty()) {
            requestPermissions(req.toTypedArray(),77)
            Toast.makeText(this,"Allow location, then tap START DASH again.",Toast.LENGTH_LONG).show()
        } else {
            startForegroundService(Intent(this,MileageService::class.java).setAction("START"))
        }
    }

    private fun launchDriverApp(packages:List<String>,label:String) {
        prefs.edit().putString("current_platform","DoorDash").apply()
        for(pkg in packages) {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it) }.onSuccess { return }
            }
            val explicit=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
            val ri=packageManager.queryIntentActivities(explicit,0).firstOrNull()
            if(ri!=null) {
                val cn=ComponentName(ri.activityInfo.packageName,ri.activityInfo.name)
                runCatching {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onSuccess { return }
            }
        }
        Toast.makeText(this,"Could not open $label. Check that the driver app is installed and enabled.",Toast.LENGTH_LONG).show()
    }

    private fun calculateYearMiles():Double {
        val c=Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        val start=c.timeInMillis
        var total=0.0
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=3 && (x[1].toLongOrNull()?:0L)>=start) total+=x[2].toDoubleOrNull()?:0.0
        }
        return total
    }

    private fun calculateYearEarnings():Double {
        val c=Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        val start=c.timeInMillis
        var total=0.0
        prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}.forEach { line ->
            val x=line.split("|")
            if(x.size>=5 && (x[1].toLongOrNull()?:0L)>=start) total+=x[4].toDoubleOrNull()?:0.0
        }
        return total
    }

    private fun exportTaxCsv() {
        val name="DeliveryDecision_${SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())}.csv"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/csv"
            putExtra(Intent.EXTRA_TITLE,name)
        },901)
    }

    @Deprecated("framework")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        val uri=data?.data?:return

        if(requestCode==902){
            val year=prefs.getInt("pending_pdf_year",Calendar.getInstance().get(Calendar.YEAR))
            runCatching {
                val pdf=android.graphics.pdf.PdfDocument()
                val pageInfo=android.graphics.pdf.PdfDocument.PageInfo.Builder(612,792,1).create()
                val page=pdf.startPage(pageInfo)
                val c=page.canvas
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color=Color.BLACK
                paint.textSize=20f
                c.drawText("Delivery Decision Tax Summary",40f,60f,paint)
                paint.textSize=14f
                c.drawText("Tax Year: $year",40f,95f,paint)
                c.drawText("Earnings: $${f2(calculateEarningsForYear(year))}",40f,135f,paint)
                c.drawText("Miles: ${f1(calculateMilesForYear(year))}",40f,165f,paint)
                c.drawText("Deliveries: ${calculateDeliveriesForYear(year)}",40f,195f,paint)
                c.drawText("Dash Hours: ${f2(calculateDashHoursForYear(year))}",40f,225f,paint)
                c.drawText("Recordkeeping summary only — not tax advice.",40f,275f,paint)
                pdf.finishPage(page)
                contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                pdf.close()
            }.onSuccess {
                Toast.makeText(this,"PDF saved",Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this,"Could not save PDF",Toast.LENGTH_LONG).show()
            }
            return
        }

        if(requestCode!=901) return
        val history=prefs.getString("dash_history","").orEmpty()
        val csv=buildString {
            appendLine("Start,End,Platform,Miles,Earnings,Deliveries,DashHours")
            history.lines().filter{it.isNotBlank()}.forEach { line ->
                val x=line.split("|")
                if(x.size>=3) {
                    val fmt=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US)
                    val st=x[0].toLongOrNull()?:0L
                    val en=x[1].toLongOrNull()?:0L
                    val platform=if(x.size>=4)x[3] else "Delivery"
                    val earnings=if(x.size>=5)x[4] else "0"
                    val deliveries=if(x.size>=6)x[5] else "0"
                    val dashHours=validSessionDuration(st,en)/3600000.0
                    appendLine("${fmt.format(Date(st))},${fmt.format(Date(en))},$platform,${x[2]},$earnings,$deliveries,${f2(dashHours)}")
                }
            }
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess {
            Toast.makeText(this,"CSV saved",Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this,"Could not save CSV",Toast.LENGTH_LONG).show()
        }
    }

    private fun validSessionDuration(start:Long,end:Long):Long {
        if(start<=0L || end<start) return 0L
        val duration=end-start
        return if(duration<=24L*60L*60L*1000L) duration else 0L
    }

    private fun sanitizeHistoryDurations() {
        if(prefs.getBoolean("history_duration_sanitized_v040",false)) return
        val lines=prefs.getString("dash_history","").orEmpty().lines().filter{it.isNotBlank()}
        var changed=false
        val fixed=lines.map { line ->
            val x=line.split("|").toMutableList()
            if(x.size>=2){
                val st=x[0].toLongOrNull()?:0L
                val en=x[1].toLongOrNull()?:st
                if(st<=0L || en<st || en-st>24L*60L*60L*1000L){
                    if(st>0L) x[1]=st.toString()
                    changed=true
                }
            }
            x.joinToString("|")
        }
        val e=prefs.edit().putBoolean("history_duration_sanitized_v040",true)
        if(changed) e.putString("dash_history",if(fixed.isEmpty()) "" else fixed.joinToString("\n",postfix="\n"))
        e.apply()
    }

    private fun isAccessibilityEnabled():Boolean {
        val enabled=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false
        return enabled.contains("$packageName/.OfferReaderService",true) || enabled.contains("com.deliverydecision",true)
    }

    private fun card()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(dp(11),dp(9),dp(11),dp(9))
        background=rounded(cardFill,cardStroke,1,12f)
    }

    private fun title(s:String)=txt(s,15f,Color.WHITE,true)

    private fun div()=View(this).apply {
        setBackgroundColor(Color.rgb(58,82,50))
        layoutParams=LinearLayout.LayoutParams(-1,1).apply{topMargin=dp(5);bottomMargin=dp(5)}
    }

    private fun valueRow(label:String,value:String):View {
        val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(6),0,dp(6)) }
        r.addView(txt(label,12.5f,Color.WHITE,false),LinearLayout.LayoutParams(0,-2,1f))
        r.addView(txt(value,13f,green,false))
        return r
    }

    private fun neonButton(s:String)=Button(this).apply {
        text=s;textSize=13f;setTextColor(Color.BLACK);setTypeface(typeface,Typeface.BOLD)
        background=rounded(green,green,0,8f)
        setPadding(dp(4),0,dp(4),0)
    }

    private fun outlineButton(s:String,c:Int)=Button(this).apply {
        text=s;textSize=11f;setTextColor(c);setTypeface(typeface,Typeface.BOLD)
        background=rounded(Color.argb(240,2,7,4),c,2,8f)
        setPadding(dp(3),0,dp(3),0)
    }

    private fun darkButton(s:String)=Button(this).apply {
        text=s;textSize=10.5f;setTextColor(Color.WHITE)
        background=rounded(Color.rgb(8,11,8),Color.rgb(65,75,65),1,8f)
        setPadding(dp(2),0,dp(2),0)
    }

    private fun txt(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply {
        text=s;textSize=size;setTextColor(color)
        if(bold)setTypeface(typeface,Typeface.BOLD)
    }

    private fun rounded(fill:Int,stroke:Int,strokeW:Int,r:Float)=GradientDrawable().apply {
        setColor(fill)
        cornerRadius=dp(r.toInt()).toFloat()
        if(strokeW>0)setStroke(dp(strokeW),stroke)
    }

    private fun gap(p:LinearLayout,h:Int)=p.addView(Space(this),LinearLayout.LayoutParams(1,dp(h)))
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun f1(v:Double)=String.format(Locale.US,"%.1f",v)
    private fun f2(v:Double)=String.format(Locale.US,"%.2f",v)

    private fun formatDuration(ms:Long):String {
        val s=(ms.coerceAtLeast(0L))/1000
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60)
    }

    private fun hhmm(ms:Long):String {
        val s=(ms.coerceAtLeast(0L))/1000
        return String.format(Locale.US,"%02d:%02d",s/3600,(s%3600)/60)
    }
}
