package com.kroky.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final double STEP_LENGTH_M = 0.7;
    private static final double KCAL_PER_STEP = 0.04;
    private static final Locale UK = new Locale("uk");

    private TextView dateView, stepsView, goalView, statsView, warningView;
    private ProgressBar progress;
    private LinearLayout history;

    // Поле, а не локальна змінна: SharedPreferences тримає слухача лише слабким посиланням.
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (prefs, key) -> refresh();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dateView = findViewById(R.id.date);
        stepsView = findViewById(R.id.steps);
        goalView = findViewById(R.id.goal);
        statsView = findViewById(R.id.stats);
        warningView = findViewById(R.id.warning);
        progress = findViewById(R.id.progress);
        history = findViewById(R.id.history);
        findViewById(R.id.changeGoal).setOnClickListener(v -> showGoalDialog());

        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        StepStore.prefs(this).registerOnSharedPreferenceChangeListener(listener);
        StepService.start(this);
        refresh();
    }

    @Override
    protected void onPause() {
        StepStore.prefs(this).unregisterOnSharedPreferenceChangeListener(listener);
        super.onPause();
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29 && !StepService.hasPermission(this)) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        StepService.start(this);
        refresh();
    }

    private void refresh() {
        Calendar now = Calendar.getInstance();
        int steps = StepStore.stepsOn(this, now);
        int goal = StepStore.goal(this);

        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", UK).format(now.getTime()));
        stepsView.setText(String.valueOf(steps));
        progress.setMax(goal);
        progress.setProgress(Math.min(steps, goal));
        goalView.setText(getString(R.string.goal_format, goal, (int) (steps * 100L / goal)));
        statsView.setText(getString(R.string.stats_format,
                steps * STEP_LENGTH_M / 1000.0, (int) Math.round(steps * KCAL_PER_STEP)));

        if (!StepService.hasSensor(this)) {
            showWarning(R.string.no_sensor);
        } else if (!StepService.hasPermission(this)) {
            showWarning(R.string.no_permission);
        } else {
            warningView.setVisibility(View.GONE);
        }

        fillHistory(goal);
    }

    private void showWarning(int textRes) {
        warningView.setText(textRes);
        warningView.setVisibility(View.VISIBLE);
    }

    private void fillHistory(int goal) {
        history.removeAllViews();
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, d MMM", UK);
        int pad = dp(6);
        for (int i = 0; i < 7; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, -i);
            int steps = StepStore.stepsOn(this, day);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad, 0, pad);

            TextView label = new TextView(this);
            label.setText(fmt.format(day.getTime()));
            label.setTextSize(15);
            label.setTextColor(0xFF333333);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3));

            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(goal);
            bar.setProgress(Math.min(steps, goal));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(8), 4);
            barLp.setMargins(dp(8), 0, dp(8), 0);
            row.addView(bar, barLp);

            TextView count = new TextView(this);
            count.setText(String.valueOf(steps));
            count.setTextSize(15);
            count.setTextColor(0xFF333333);
            count.setGravity(Gravity.END);
            row.addView(count, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));

            history.addView(row);
        }
    }

    private void showGoalDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(StepStore.goal(this)));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.goal_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    try {
                        int goal = Integer.parseInt(input.getText().toString().trim());
                        if (goal >= 100 && goal <= 200000) {
                            StepStore.setGoal(this, goal);
                        }
                    } catch (NumberFormatException ignored) {
                        // порожнє або завелике число: ціль не змінюємо
                    }
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
