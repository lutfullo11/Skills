package ru.zhivo.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.zhivo.app.core.Learning
import ru.zhivo.app.core.Profile
import java.time.ZoneId

object Reminders {
    fun schedule(context:Context,p:Profile){
        val alarm=context.getSystemService(AlarmManager::class.java)
        val pi=PendingIntent.getBroadcast(context,10,Intent(context,ReminderReceiver::class.java).setAction("ru.zhivo.REMIND"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pi)
        if(p.reminder){
            val next=Learning.nextReminder(System.currentTimeMillis(),ZoneId.systemDefault(),p.reminderHour,p.reminderMinute)
            alarm.setWindow(AlarmManager.RTC_WAKEUP,next,15*60_000L,pi)
        }
    }
}
class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val app=context.applicationContext as ZhivoApplication
        val p=app.store.state.value.profile
        if(!p.reminder)return
        Reminders.schedule(context,p)
        if(intent.action!="ru.zhivo.REMIND")return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("study","Занятия «Живо»",NotificationManager.IMPORTANCE_DEFAULT))
        if(!manager.areNotificationsEnabled())return
        val open=PendingIntent.getActivity(context,11,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(12,NotificationCompat.Builder(context,"study").setSmallIcon(ru.zhivo.app.R.drawable.ic_zhivo).setContentTitle("Немного русского сегодня")
            .setContentText("Продолжите занятие или повторите знакомые слова.").setContentIntent(open).setAutoCancel(true).build())
    }
}
