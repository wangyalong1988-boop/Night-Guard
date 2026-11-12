package cn.nightguard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.ZonedDateTime;

final class GuardNotification {
    private static final String CHANNEL = "guard_countdown";
    private static final int ID = 101;
    private final Context context;
    private long lastBoundary = -1, lastPost = -60000;
    GuardNotification(Context context) { this.context = context; }
    static boolean permitted(Context c) {
        return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    void refresh() {
        if (!permitted(context)) return;
        ZonedDateTime now = ZonedDateTime.now();
        if (!Policy.night(now.toLocalTime())) { if(lastBoundary != -1) cancel(); return; }
        long boundary = Policy.nextBoundary(now).toInstant().toEpochMilli();
        long elapsed = android.os.SystemClock.elapsedRealtime();
        if (boundary == lastBoundary && elapsed - lastPost < 60000) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "守护倒计时", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("仅在01:00–08:00显示距离解除限制的倒计时");
        channel.setSound(null, null); channel.enableVibration(false);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(cn.nightguard.R.drawable.ic_guard)
                .setColor(BlueUi.BLUE)
                .setContentTitle("夜间守护中 · 距离解除限制")
                .setContentText("08:00 自动解除 · 娱乐应用与浏览器受限")
                .setWhen(boundary).setUsesChronometer(true).setChronometerCountDown(true)
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open).build();
        try { manager.notify(ID, n); lastBoundary = boundary; lastPost = elapsed; }
        catch (SecurityException ignored) { /* Permission may have been revoked concurrently. */ }
    }
    void cancel() { lastBoundary = -1; context.getSystemService(NotificationManager.class).cancel(ID); }
}
