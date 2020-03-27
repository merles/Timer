package com.example.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import com.example.timer.util.NotificationUtil
import com.example.timer.util.PrefUtil

import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

/**
 * Main class of the application from here the timer from background and foreground is controlled
 *@author: merles
 */
class MainActivity : AppCompatActivity() {

    enum class TimerState{
        /**
         * This enumeration is used in all the process to control the state of the timer
         */
        Stopped, Paused, Running

    }
    companion object {
        @RequiresApi(Build.VERSION_CODES.KITKAT)
        fun setAlarm(context: Context, nowSeconds : Long, secondsRemaining : Long) : Long {
            /**
             * This function creates an alarm to notify when the timer expires
             */
            val wakeUpTime = (nowSeconds + secondsRemaining) * 1000
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context,TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context,0,intent,0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP,wakeUpTime,pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds,context)
            return wakeUpTime
        }

        fun removeAlarm(context: Context){
            val intent = Intent(context,TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context,0,intent,0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0,context)

        }
        val nowSeconds : Long
            get() = Calendar.getInstance().timeInMillis/1000

    }

    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds = 0L
    private var timerState = TimerState.Stopped

    private var secondsRemaining = 0L



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setIcon(R.drawable.ic_timer_black_24dp)
        supportActionBar?.title = "TIMER "

        updateButtons()

        fab_start.setOnClickListener{ v ->
            startTimer()
            timerState = TimerState.Running
            updateButtons()

        }
        fab_pause.setOnClickListener{v ->
            timer.cancel()
            timerState = TimerState.Paused
            updateButtons()
        }
        fab_stop.setOnClickListener{v ->
            timer.cancel()
            onTimerFinished()
        }

    }

    override fun onResume() {
        /**
         * Actions performed when it is recovered from the notification bar or app switcher menu
         * the timer is initiated again, the alarm is removed and the notification hidden.
         */
        super.onResume()
        initTimer()
        NotificationUtil.hideTimerNotification(this)
        removeAlarm(this)

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onPause() {
        /**
         * onPause is called when the app is minimized or is switched from the app switcher menu
         */
        super.onPause()
        if( timerState == TimerState.Running){
            timer.cancel()
            val wakeUpTime = setAlarm(this, nowSeconds,secondsRemaining)
            NotificationUtil.showTimerRunning(this,wakeUpTime)
            }
        else if(timerState == TimerState.Paused){
            NotificationUtil.showTimerPaused(this)
         }
        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds,this)
        PrefUtil.setSecondsRemaining(secondsRemaining,this)
        PrefUtil.setTimerState(timerState,this)
    }

    private fun initTimer(){
        timerState = PrefUtil.getTimerState(this)
        if (timerState == TimerState.Stopped){
            setNewTimerLength()
        }
        else{
            setPreviousTimerLength()
        }
        secondsRemaining = if (timerState == TimerState.Running || timerState == TimerState.Paused)
            PrefUtil.getSecondsRemaining(this)
        else
            timerLengthSeconds

        val alarmSetTime = PrefUtil.getAlarmSetTime(this)
        if (alarmSetTime > 0){
            secondsRemaining -= nowSeconds - alarmSetTime
        }

        if (secondsRemaining <= 0)
            onTimerFinished()
        else if(timerState == TimerState.Running)
            startTimer()

        updateButtons()
        updateCountDownUI()
    }

    private fun onTimerFinished(){
        timerState = TimerState.Stopped

        setNewTimerLength()

        progressbar_countdown.progress = 0

        PrefUtil.setSecondsRemaining(timerLengthSeconds,this)
        secondsRemaining = timerLengthSeconds

        updateButtons()
        updateCountDownUI()

    }

    private fun startTimer(){
        timerState = TimerState.Running

        timer = object : CountDownTimer(secondsRemaining*1000, 1000){
            override fun onFinish() = onTimerFinished()
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = millisUntilFinished / 1000
                updateCountDownUI()
            }
        }.start()
    }

    private fun setNewTimerLength(){
        val lengthInMinutes = PrefUtil.getTimerLength(this)
        timerLengthSeconds = (lengthInMinutes * 60L)
        progressbar_countdown.max = timerLengthSeconds.toInt()
    }

    private fun setPreviousTimerLength(){
        timerLengthSeconds = PrefUtil.getPreviousTimerLengthSeconds(this)
        progressbar_countdown.max = timerLengthSeconds.toInt()
    }

    private fun updateCountDownUI(){
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinuteUntilFinished = secondsRemaining - minutesUntilFinished * 60
        val secondsStr = secondsInMinuteUntilFinished.toString()
        textView_count_down.text =
            "$minutesUntilFinished:" + if (secondsStr.length == 2) secondsStr
            else "0$secondsStr"
        progressbar_countdown.progress = (timerLengthSeconds - secondsRemaining).toInt()
    }

    private fun updateButtons(){
        when(timerState){
            TimerState.Running -> {
                fab_start.isEnabled = false
                fab_pause.isEnabled = true
                fab_stop.isEnabled = true
            }
            TimerState.Stopped -> {
                fab_start.isEnabled = true
                fab_pause.isEnabled = false
                fab_stop.isEnabled = false
            }
            TimerState.Paused -> {
                fab_start.isEnabled = true
                fab_pause.isEnabled = false
                fab_stop.isEnabled = true
            }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
