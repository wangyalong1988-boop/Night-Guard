package cn.nightguard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Three quiet notifications: a countdown during the night, a nudge shortly before it starts,
 * and a one-line summary of the night after it ends (posted when the phone is first used).
 */
final class GuardNotification {
    private static final String CHANNEL = "guard_countdown", TIPS = "guard_tips", MORNING = "guard_morning";
    private static final int ID = 101, WIND_DOWN_ID = 102, MORNING_ID = 103;
    private final Context context;
    private long lastBoundary = -1, lastPost = -60000;

    GuardNotification(Context context) { this.context = context; }

    static boolean permitted(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE)
                && (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && manager.areNotificationsEnabled();
    }

    private NotificationManager channels() {
        NotificationManager m = context.getSystemService(NotificationManager.class);
        NotificationChannel countdown = new NotificationChannel(CHANNEL, "夜间倒计时", NotificationManager.IMPORTANCE_LOW);
        countdown.setDescription("限制时段内显示距离解除的倒计时"); countdown.setSound(null, null); countdown.enableVibration(false);
        NotificationChannel tips = new NotificationChannel(TIPS, "睡前提醒", NotificationManager.IMPORTANCE_DEFAULT);
        tips.setDescription("限制开始前15分钟提醒一次"); tips.enableVibration(false);
        NotificationChannel morning = new NotificationChannel(MORNING, "晨间小结", NotificationManager.IMPORTANCE_LOW);
        morning.setDescription("早上第一次使用手机时，告诉你昨晚的守护结果"); morning.setSound(null, null);
        m.createNotificationChannels(List.of(countdown, tips, morning));
        return m;
    }

    private PendingIntent open() {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void refresh(Store store, Policy.Schedule s, ZonedDateTime now) {
        if (!permitted(context)) return;
        if (Policy.night(now.toLocalTime(), s)) { countdown(s, now); return; }
        if (lastBoundary != -1) cancel();
        long minutes = Policy.minutesUntilStart(now, s);
        if (minutes > 0 && minutes <= Policy.WIND_DOWN_MINUTES && store.once("tip:wind:" + Policy.nextStart(now, s).toLocalDate()))
            post(WIND_DOWN_ID, TIPS, "还有 " + minutes + " 分钟进入夜间守护", "把想做的事收个尾，该准备睡觉了。", Notification.CATEGORY_REMINDER);
        morning(store, s, now);
    }

    private void countdown(Policy.Schedule s, ZonedDateTime now) {
        long boundary = Policy.nightEnd(now, s).toInstant().toEpochMilli(), elapsed = android.os.SystemClock.elapsedRealtime();
        if (boundary == lastBoundary && elapsed - lastPost < 60000) return;
        Notification n = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_guard).setColor(0xff396fa8)
                .setContentTitle("夜间守护中 · 距离解除").setContentText(s.endText() + " 自动解除 · 已选应用暂停使用")
                .setWhen(boundary).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open()).build();
        try { channels().notify(ID, n); lastBoundary = boundary; lastPost = elapsed; }
        catch (SecurityException ignored) { /* Permission may have been revoked concurrently. */ }
    }

    /** After a guarded night, the first check within 4 hours of its end posts one short summary. */
    private void morning(Store store, Policy.Schedule s, ZonedDateTime now) {
        ZonedDateTime end = Policy.nightEnd(now, s);
        if (now.isBefore(end) || java.time.Duration.between(end, now).toHours() >= 4) return;
        List<Store.Night> last = store.recentNights(1);
        if (last.isEmpty()) return;
        Store.Night night = last.get(0);
        if (!night.guarded || !store.once("tip:morning:" + night.date)) return;
        context.getSystemService(NotificationManager.class).cancel(WIND_DOWN_ID);
        String title, text;
        if (night.unlocks == 0) {
            int streak = store.calmStreak();
            title = streak > 1 ? "早安 · 已连续 " + streak + " 晚安稳入睡" : "早安 · 昨晚安稳度过";
            text = night.blocks > 0 ? "夜里拦下了 " + night.blocks + " 次打开，一次也没有解锁。" : "整晚都没有打开受限应用，给你点个赞。";
        } else {
            title = "早安 · 昨晚的守护结果";
            text = "拦下 " + night.blocks + " 次，临时解锁 " + night.unlocks + " 次。今晚再试试一次都不解锁？";
        }
        Store.NightUse use = store.rest(night.date);
        if (use != null) text = "昨晚放下手机 " + Policy.duration(use.rest.restMs()) + (use.rest.pickups > 0 ? "，拿起 " + use.rest.pickups + " 次" : "，一次都没拿起") + "。" + text;
        post(MORNING_ID, MORNING, title, text, Notification.CATEGORY_STATUS);
    }

    private void post(int id, String channel, String title, String text, String category) {
        Notification n = new Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_guard).setColor(0xff396fa8)
                .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setCategory(category).setContentIntent(open()).build();
        try { channels().notify(id, n); } catch (SecurityException ignored) { }
    }

    void cancel() { lastBoundary = -1; context.getSystemService(NotificationManager.class).cancel(ID); }
}
