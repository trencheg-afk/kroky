package com.kroky.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

/** Фонова служба: слухає датчик кроків і показує постійне сповіщення. */
public class StepService extends Service implements SensorEventListener {
    private static final String CHANNEL = "steps";
    private static final int NOTIF_ID = 1;

    private SensorManager sensorManager;
    private boolean listening;

    /** Запускає службу, якщо є дозвіл і датчик. Повертає false, якщо запуск неможливий. */
    static boolean start(Context c) {
        if (!hasPermission(c) || !hasSensor(c)) return false;
        try {
            c.startForegroundService(new Intent(c, StepService.class));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasSensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification(StepStore.today(this));
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!listening) {
            Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (sensor == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (listening) sensorManager.unregisterListener(this);
        listening = false;
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int today = StepStore.onSensorValue(this, (long) event.values[0]);
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(today));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(int steps) {
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_walk)
                .setContentTitle(getString(R.string.notif_title, steps))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
