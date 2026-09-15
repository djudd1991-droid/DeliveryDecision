package com.deliverydecision

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import java.text.SimpleDateFormat
import java.util.*

class MileageService : Service(), LocationListener {
    private val prefs by lazy { getSharedPreferences("dd", MODE_PRIVATE) }
    private lateinit var lm: LocationManager
    private var last: Location? = null
    private var lastMovement = 0L
    private val h = Handler(Looper.getMainLooper())
    private fun autoStopMs():Long = prefs.getInt("auto_stop_min",30).coerceIn(20,120).toLong()*60L*1000L

    override fun onCreate() {
        super.onCreate()
        lm=getSystemService(LocationManager::class.java)
        if(Build.VERSION.SDK_INT>=26){
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("mileage","Mileage tracking",NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP") stopDash(false) else startDash()
        return START_STICKY
    }

    private fun startDash() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ stopSelf(); return }
        resetPeriods()
        if(!prefs.getBoolean("dash_active",false)){
            prefs.edit()
                .putBoolean("dash_active",true)
                .putLong("dash_start",System.currentTimeMillis())
                .putFloat("session_miles",0f)
                .putFloat("session_confirmed_earnings",0f)
                .putInt("session_deliveries",0)
                .putLong("session_confirmed_online_ms",0L)
                .putString("session_platform","")
                .putBoolean("auto_stopped",false)
                .apply()
        }
        last=null
        lastMovement=System.currentTimeMillis()
        startForeground(77,Notification.Builder(this,"mileage")
            .setContentTitle("Delivery Decision")
            .setContentText("Dash mileage tracking is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build())
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2500L,5f,this) }
        runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000L,10f,this) }
        h.removeCallbacks(checkIdle); h.postDelayed(checkIdle,60000L)
    }

    private val checkIdle=object:Runnable{
        override fun run(){
            if(!prefs.getBoolean("dash_active",false)) return
            val now=System.currentTimeMillis()
            val lastOffer=prefs.getLong("last_offer_seen",now)
            val limit=autoStopMs()
            if(now-lastMovement>limit && now-lastOffer>limit) stopDash(true)
            else h.postDelayed(this,60000L)
        }
    }

    private fun stopDash(auto:Boolean){
        if(!prefs.getBoolean("dash_active",false)){ stopSelf(); return }
        val end=System.currentTimeMillis()
        val start=prefs.getLong("dash_start",end)
        val duration=(end-start).coerceAtLeast(0L)
        val miles=prefs.getFloat("session_miles",0f)
        val earnings=prefs.getFloat("session_confirmed_earnings",0f)
        val deliveries=prefs.getInt("session_deliveries",0)
        val platform=prefs.getString("session_platform","").orEmpty().ifBlank {
            prefs.getString("current_platform","Delivery") ?: "Delivery"
        }

        // Don't clutter History with accidental one-second / zero-mile sessions.
        val junk = duration < 60_000L && miles < 0.1f && earnings <= 0.0f
        val raw=prefs.getString("dash_history","").orEmpty()
        val line="$start|$end|$miles|$platform|$earnings|$deliveries\n"

        val edit=prefs.edit()
            .putBoolean("dash_active",false)
            .putBoolean("auto_stopped",auto)
            .putLong("dash_end",end)
            .putLong("last_session_ms",duration)

        if(!junk) edit.putString("dash_history",(raw+line).takeLast(24000))
        edit.apply()

        runCatching{lm.removeUpdates(this)}
        h.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(loc:Location){
        if(!prefs.getBoolean("dash_active",false) || loc.accuracy>80f) return
        resetPeriods()
        last?.let { prev->
            val meters=prev.distanceTo(loc)
            val miles=meters/1609.344
            val dt=((loc.time-prev.time).coerceAtLeast(1L))/1000.0
            val mph=miles/(dt/3600.0)
            if(miles in 0.002..0.40 && mph<100){
                addMiles(miles.toFloat())
                lastMovement=System.currentTimeMillis()
            }
        }
        last=loc
    }

    private fun addMiles(m:Float){
        prefs.edit()
            .putFloat("today_miles",prefs.getFloat("today_miles",0f)+m)
            .putFloat("week_miles",prefs.getFloat("week_miles",0f)+m)
            .putFloat("month_miles",prefs.getFloat("month_miles",0f)+m)
            .putFloat("session_miles",prefs.getFloat("session_miles",0f)+m)
            .apply()
    }

    private fun resetPeriods(){
        val now=Calendar.getInstance()
        val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now.time)
        val month=SimpleDateFormat("yyyy-MM",Locale.US).format(now.time)
        val week="${now.get(Calendar.YEAR)}-${now.get(Calendar.WEEK_OF_YEAR)}"
        val e=prefs.edit()
        if(prefs.getString("day_key","")!=day){
            e.putString("day_key",day).putFloat("today_miles",0f).putFloat("today_earnings",0f)
        }
        if(prefs.getString("week_key","")!=week) e.putString("week_key",week).putFloat("week_miles",0f)
        if(prefs.getString("month_key","")!=month) e.putString("month_key",month).putFloat("month_miles",0f)
        e.apply()
    }

    override fun onBind(intent:Intent?)=null
}
