package com.kroky.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Зберігає кількість кроків по днях у SharedPreferences. */
final class StepStore {
    static final String PREFS = "steps";
    private static final String KEY_LAST_SENSOR = "last_sensor";
    private static final String KEY_GOAL = "goal";
    private static final String DAY_PREFIX = "d_";
    static final int DEFAULT_GOAL = 10000;

    private StepStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String dayKey(Calendar day) {
        return DAY_PREFIX + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime());
    }

    static int stepsOn(Context c, Calendar day) {
        return prefs(c).getInt(dayKey(day), 0);
    }

    static int today(Context c) {
        return stepsOn(c, Calendar.getInstance());
    }

    static int goal(Context c) {
        return prefs(c).getInt(KEY_GOAL, DEFAULT_GOAL);
    }

    static void setGoal(Context c, int goal) {
        prefs(c).edit().putInt(KEY_GOAL, goal).apply();
    }

    /**
     * Приймає показник датчика (кроки від увімкнення телефону) і додає різницю до сьогоднішнього дня.
     * Повертає кількість кроків за сьогодні.
     */
    static synchronized int onSensorValue(Context c, long value) {
        SharedPreferences p = prefs(c);
        long last = p.getLong(KEY_LAST_SENSOR, -1);
        long delta;
        if (last < 0) {
            delta = 0;            // перший запуск: починаємо відлік з цього моменту
        } else if (value < last) {
            delta = value;        // телефон перезавантажували, лічильник почався з нуля
        } else {
            delta = value - last;
        }
        String key = dayKey(Calendar.getInstance());
        int total = p.getInt(key, 0) + (int) delta;
        p.edit().putLong(KEY_LAST_SENSOR, value).putInt(key, total).apply();
        return total;
    }
}
