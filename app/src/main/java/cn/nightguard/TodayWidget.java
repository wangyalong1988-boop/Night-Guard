package cn.nightguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

/** Home-screen widget: today's screen time, progress to the goal and pickups. Refreshed by the guard service. */
public class TodayWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) { update(c, new Store(c)); }

    static void update(Context c, Store store) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        int[] ids = manager.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
        if (ids == null || ids.length == 0) return;
        Store.Day today = store.today(); int goal = store.goalMinutes();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_today);
        v.setTextViewText(R.id.widget_used, Policy.duration(today.useMs));
        if (store.night()) v.setTextViewText(R.id.widget_detail, "夜间守护中 · " + store.schedule().endText() + " 解除");
        else if (store.focusing()) v.setTextViewText(R.id.widget_detail, "专注中 · 还剩 " + Policy.duration(store.focusRemainingMs()));
        else if (goal > 0) {
            long left = goal * 60000L - today.useMs;
            v.setTextViewText(R.id.widget_detail, left > 0 ? "还剩 " + Policy.duration(left) + " · 拿起 " + today.pickups + " 次" : "已超出 " + Policy.duration(-left) + " · 拿起 " + today.pickups + " 次");
        } else v.setTextViewText(R.id.widget_detail, "拿起 " + today.pickups + " 次");
        if (goal > 0) { v.setViewVisibility(R.id.widget_progress, View.VISIBLE); v.setProgressBar(R.id.widget_progress, 1000, (int) Math.min(1000, today.useMs * 1000 / (goal * 60000L)), false); }
        else v.setViewVisibility(R.id.widget_progress, View.GONE);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, v);
    }
}
