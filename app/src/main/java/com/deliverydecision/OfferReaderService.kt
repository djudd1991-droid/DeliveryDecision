package com.deliverydecision

import android.accessibilityservice.AccessibilityService
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max

data class Offer(val pay:Double,val miles:Double,val minutes:Double?)
data class DashSummary(val earnings:Double,val accepted:Int?,val offered:Int?,val weekly:Double?,val onlineMinutes:Int?)

object OfferParser {
    private val money=Pattern.compile("\\$(\\d+(?:\\.\\d{1,2})?)")
    private val miles=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:mi|miles)",Pattern.CASE_INSENSITIVE)
    private val mins=Pattern.compile("(\\d{1,3})\\s*(?:min|mins|minutes)",Pattern.CASE_INSENSITIVE)

    fun parse(text:String):Offer? {
        val m=money.matcher(text); val d=miles.matcher(text)
        if(!m.find()||!d.find()) return null
        val pay=m.group(1)?.toDoubleOrNull()?:return null
        val dist=d.group(1)?.toDoubleOrNull()?:return null
        if(pay<=0||dist<=0||pay>500||dist>200) return null
        val tm=mins.matcher(text)
        val minutes=if(tm.find()) tm.group(1)?.toDoubleOrNull() else null
        return Offer(pay,dist,minutes)
    }
}

object DashSummaryParser {
    private val summary = Pattern.compile("Dash\\s+summary\\s*\\$\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val accepted = Pattern.compile("Offers\\s+accepted\\s*(\\d+)\\s*out\\s*of\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val weekly = Pattern.compile("Earnings\\s+this\\s+week\\s*\\$\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val onlineHrMin = Pattern.compile("Total\\s+online\\s+time\\s*(?:(\\d+)\\s*hr\\s*)?(?:(\\d+)\\s*min)?", Pattern.CASE_INSENSITIVE)

    fun parse(text:String):DashSummary? {
        val sm=summary.matcher(text)
        if(!sm.find()) return null
        val earnings=sm.group(1)?.toDoubleOrNull()?:return null
        if(earnings<0 || earnings>1000) return null

        val am=accepted.matcher(text)
        val acceptedCount=if(am.find()) am.group(1)?.toIntOrNull() else null
        val offeredCount=if(am.groupCount()>=2 && acceptedCount!=null) am.group(2)?.toIntOrNull() else null

        val wm=weekly.matcher(text)
        val weeklyAmount=if(wm.find()) wm.group(1)?.toDoubleOrNull() else null

        val tm=onlineHrMin.matcher(text)
        var minutes:Int?=null
        if(tm.find()){
            val hrs=tm.group(1)?.toIntOrNull()?:0
            val mins=tm.group(2)?.toIntOrNull()?:0
            if(hrs>0 || mins>0) minutes=hrs*60+mins
        }

        return DashSummary(earnings,acceptedCount,offeredCount,weeklyAmount,minutes)
    }
}

class OfferReaderService:AccessibilityService(){
    private val prefs by lazy { getSharedPreferences("dd",MODE_PRIVATE) }
    private val h=Handler(Looper.getMainLooper())
    private var overlay:TextView?=null
    private var shownKey=""
    private var lastSeen=0L
    private val hideRunnable=Runnable {
        if(System.currentTimeMillis()-lastSeen>5000L){ hide(); shownKey="" }
    }

    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        val pkg=event?.packageName?.toString()?:return
        if(pkg==packageName) return
        if(pkg!="com.doordash.driverapp") return

        val root=rootInActiveWindow?:return
        val text=collect(root)

        // Track which platform was actually visible during this Delivery Decision session.
        val platform="DoorDash"
        if(prefs.getBoolean("dash_active",false)){
            val old=prefs.getString("session_platform","") ?: ""
            val next=when {
                old.isBlank() -> platform
                old==platform -> old
                else -> "Multi"
            }
            prefs.edit().putString("session_platform",next).apply()
        }

        recordDasherStatus(text)

        // DoorDash's final "Dash summary" screen is our confirmed earnings source.
        if(pkg=="com.doordash.driverapp"){
            DashSummaryParser.parse(text)?.let {
                recordDoorDashSummary(it)
                hide()
                shownKey=""
                return
            }
        }

        val offer=OfferParser.parse(text)?:return
        lastSeen=System.currentTimeMillis()
        prefs.edit().putLong("last_offer_seen",lastSeen).apply()
        h.removeCallbacks(hideRunnable); h.postDelayed(hideRunnable,5500L)

        val key="$pkg|${offer.pay}|${offer.miles}|${offer.minutes}"
        if(key==shownKey && overlay!=null) return
        shownKey=key
        show(pkg,offer)
    }

    private fun recordDasherStatus(text:String){
        val edit=prefs.edit()
        var changed=false
        fun percent(label:String,key:String){
            val m=Pattern.compile(Pattern.quote(label)+"\\s*(\\d{1,3})%",Pattern.CASE_INSENSITIVE).matcher(text)
            if(m.find()){
                m.group(1)?.toIntOrNull()?.coerceIn(0,100)?.let{ edit.putInt(key,it); changed=true }
            }
        }
        percent("Acceptance rate","dd_acceptance")
        percent("Completion rate","dd_completion")
        percent("On-time rate","dd_ontime")
        percent("Quality rate","dd_quality")

        Pattern.compile("Overall\\s+rating\\s*(\\d{1,3})",Pattern.CASE_INSENSITIVE).matcher(text).let{m->
            if(m.find()) m.group(1)?.toIntOrNull()?.coerceIn(0,100)?.let{ edit.putInt("dd_overall",it); changed=true }
        }
        Pattern.compile("Customer\\s+rating\\s*(\\d(?:\\.\\d)?)",Pattern.CASE_INSENSITIVE).matcher(text).let{m->
            if(m.find()) m.group(1)?.toFloatOrNull()?.let{ edit.putFloat("dd_customer",it); changed=true }
        }
        Pattern.compile("Last\\s+30-day\\s+orders\\s*(\\d+)",Pattern.CASE_INSENSITIVE).matcher(text).let{m->
            if(m.find()) m.group(1)?.toIntOrNull()?.let{ edit.putInt("dd_30day_orders",it); changed=true }
        }
        Pattern.compile("(\\d+)\\s+orders\\s+completed",Pattern.CASE_INSENSITIVE).matcher(text).let{m->
            if(m.find()) m.group(1)?.toIntOrNull()?.let{ edit.putInt("dd_lifetime_orders",it); changed=true }
        }
        when {
            text.contains("Platinum",true) -> { edit.putString("dd_tier","Platinum"); changed=true }
            text.contains("Gold",true) -> { edit.putString("dd_tier","Gold"); changed=true }
            text.contains("Silver",true) -> { edit.putString("dd_tier","Silver"); changed=true }
        }
        if(changed) edit.putLong("dd_status_updated",System.currentTimeMillis()).apply()
    }

    private fun recordDoorDashSummary(s:DashSummary){
        val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
        val fingerprint="$day|${f2(s.earnings)}|${s.accepted ?: -1}|${f2(s.weekly ?: -1.0)}|${s.onlineMinutes ?: -1}"
        if(prefs.getString("last_dd_summary_fingerprint","")==fingerprint) return

        val active=prefs.getBoolean("dash_active",false)
        val edit=prefs.edit()
            .putString("last_dd_summary_fingerprint",fingerprint)
            .putFloat("last_confirmed_dash_earnings",s.earnings.toFloat())
            .putLong("last_confirmed_dash_time",System.currentTimeMillis())

        s.weekly?.let { edit.putFloat("doordash_weekly_crosscheck",it.toFloat()) }
        s.accepted?.let { edit.putInt("last_confirmed_deliveries",it) }

        if(active){
            val old=prefs.getFloat("session_confirmed_earnings",0f).toDouble()
            val delta=(s.earnings-old).coerceAtLeast(0.0)
            if(delta>0){
                edit.putFloat("today_earnings",(prefs.getFloat("today_earnings",0f)+delta.toFloat()))
            }
            edit.putFloat("session_confirmed_earnings",s.earnings.toFloat())
            edit.putInt("session_deliveries",s.accepted ?: prefs.getInt("session_deliveries",0))
            edit.putString("session_platform","DoorDash")
            s.onlineMinutes?.let { edit.putLong("session_confirmed_online_ms",it*60_000L) }
            edit.apply()
        } else {
            edit.apply()
            // If the DD dash was ended before Delivery Decision was stopped, attach the
            // summary to the most recent unpriced DoorDash history session.
            patchLatestHistorySession(s)
        }
    }

    private fun patchLatestHistorySession(s:DashSummary){
        val now=System.currentTimeMillis()
        val lines=prefs.getString("dash_history","").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        for(i in lines.indices.reversed()){
            val x=lines[i].split("|").toMutableList()
            if(x.size<3) continue
            val end=x[1].toLongOrNull()?:continue
            if(now-end>6*60*60*1000L) break
            val platform=if(x.size>=4) x[3] else "Delivery"
            val existing=if(x.size>=5) x[4].toDoubleOrNull()?:0.0 else 0.0
            if((platform.equals("DoorDash",true) || platform.equals("Delivery",true)) && existing<=0.0){
                while(x.size<6) x.add("0")
                x[3]="DoorDash"
                x[4]=s.earnings.toString()
                x[5]=(s.accepted?:0).toString()
                lines[i]=x.joinToString("|")
                prefs.edit()
                    .putString("dash_history",lines.joinToString("\n",postfix="\n"))
                    .putFloat("today_earnings",prefs.getFloat("today_earnings",0f)+s.earnings.toFloat())
                    .apply()
                return
            }
        }
    }

    private fun collect(node:AccessibilityNodeInfo):String{
        val sb=StringBuilder()
        fun walk(n:AccessibilityNodeInfo?){
            if(n==null)return
            n.text?.let{sb.append(it).append(' ')}
            n.contentDescription?.let{sb.append(it).append(' ')}
            for(i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node); return sb.toString()
    }

    private fun show(pkg:String,o:Offer){
        val mpg=prefs.getFloat("mpg",20f).toDouble().coerceAtLeast(1.0)
        val gas=prefs.getFloat("gas",3.99f).toDouble()
        val min=prefs.getFloat("min_dpm",1.5f).toDouble()
        val pref=prefs.getFloat("preferred_dpm",2f).toDouble()
        val target=prefs.getFloat("target_hour",25f).toDouble()
        val dpm=o.pay/max(o.miles,.1)
        val fuel=o.miles/mpg*gas
        val after=o.pay-fuel
        val hourly=o.minutes?.takeIf{it in 5.0..180.0}?.let{o.pay/(it/60.0)}
        val decision=when{
            dpm>=pref && (hourly==null || hourly>=target) -> "TAKE"
            dpm>=min -> "CONSIDER"
            else -> "PASS"
        }
        val color=when(decision){
            "TAKE"->Color.rgb(140,255,0)
            "CONSIDER"->Color.rgb(255,214,0)
            else->Color.rgb(255,65,54)
        }
        val app="DoorDash"
        val hour=hourly?.let{"  •  ~$${f2(it)}/hr"}.orEmpty()
        val text="●  $decision   $app\n$${f2(o.pay)}  •  ${f1(o.miles)} mi  •  $${f2(dpm)}/mi$hour\nFuel ~$${f2(fuel)}  •  After fuel ~$${f2(after)}"

        h.post{
            if(overlay==null){
                overlay=TextView(this).apply{
                    textSize=15f; setTextColor(Color.WHITE); setPadding(22,14,22,14)
                }
                val lp=WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply{ gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; y=110 }
                getSystemService(WindowManager::class.java).addView(overlay,lp)
            }
            overlay?.background=GradientDrawable().apply{
                setColor(Color.argb(246,2,7,2)); setStroke(3,color); cornerRadius=24f
            }
            overlay?.text=text
        }
    }

    private fun hide(){
        h.post{
            overlay?.let{runCatching{getSystemService(WindowManager::class.java).removeView(it)}}
            overlay=null
        }
    }

    private fun f1(v:Double)=String.format(Locale.US,"%.1f",v)
    private fun f2(v:Double)=String.format(Locale.US,"%.2f",v)
    override fun onInterrupt()=Unit
}
