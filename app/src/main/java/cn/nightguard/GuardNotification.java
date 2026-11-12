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
    private static final int ID = 101, WIND_DOWN_ID = 102, MORNING_ID = 103, GOAL_ID = 104, LONG_ID = 105, WEEK_ID = 106, LIMIT_ID = 107;
    static final long LONG_SESSION_MS = 30 * 60 * 1000L;
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
        NotificationChannel tips = new NotificationChannel(TIPS, "温和提醒", NotificationManager.IMPORTANCE_DEFAULT);
        tips.setDescription("睡前15分钟、应用锁还剩5分钟、每日目标用到八成和用满时、连续看屏幕30分钟时各提醒一次"); tips.enableVibration(false);
        NotificationChannel morning = new NotificationChannel(MORNING, "小结与周报", NotificationManager.IMPORTANCE_LOW);
        morning.setDescription("早上第一次用手机时的昨晚小结，以及每周一的用机周报"); morning.setSound(null, null);
        m.createNotificationChannels(List.of(countdown, tips, morning));
        return m;
    }

    private PendingIntent open() {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void refresh(Store store, Policy.Schedule s, ZonedDateTime now) {
        if (!permitted(context)) return;
        usage(store);
        if (Policy.night(now.toLocalTime(), s)) { countdown(s, now); return; }
        if (lastBoundary != -1) cancel();
        long minutes = Policy.minutesUntilStart(now, s);
        if (minutes > 0 && minutes <= Policy.WIND_DOWN_MINUTES && store.once("tip:wind:" + Policy.nextStart(now, s).toLocalDate()))
            post(WIND_DOWN_ID, TIPS, "还有 " + minutes + " 分钟进入夜间守护", "把想做的事收个尾，该准备睡觉了。", Notification.CATEGORY_REMINDER);
        morning(store, s, now);
        weekly(store, now);
    }

    /** Monday from 9:00, once: last week against the week before, nights, and the app used most. */
    private void weekly(Store store, ZonedDateTime now) {
        if (now.getDayOfWeek() != java.time.DayOfWeek.MONDAY || now.getHour() < 9) return;
        List<Store.Day> days = store.days(15); // two full weeks before today, plus today
        long last = 0, before = 0; int nLast = 0, nBefore = 0;
        for (int i = 0; i < 14; i++) { Store.Day d = days.get(i); if (!d.recorded) continue; if (i >= 7) { last += d.useMs; nLast++; } else { before += d.useMs; nBefore++; } }
        if (nLast < 4 || !store.once("tip:week:" + now.toLocalDate())) return;
        double avg = (double) last / nLast; Integer change = nBefore >= 4 ? Policy.change((double) before / nBefore, avg) : null;
        String title = change == null ? "上周日均用机 " + Policy.duration((long) avg)
                : change <= 0 ? "上周日均 " + Policy.duration((long) avg) + "，比前一周少 " + (-change) + "%" : "上周日均 " + Policy.duration((long) avg) + "，比前一周多 " + change + "%";
        StringBuilder text = new StringBuilder();
        java.time.LocalDate[] dates = new java.time.LocalDate[7]; double[] rest = store.restHistory(7, dates); long sum = 0; int n = 0;
        for (double r : rest) if (!Double.isNaN(r)) { sum += (long) r; n++; }
        if (n > 0) text.append("夜里平均放下手机 ").append(Policy.duration(sum / n)).append("。");
        java.util.Map<String, Long> apps = new java.util.HashMap<>();
        for (int i = 1; i <= 7; i++) for (java.util.Map.Entry<String, Long> e : store.appTimes(now.toLocalDate().minusDays(i)).entrySet()) apps.merge(e.getKey(), e.getValue(), Long::sum);
        apps.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).ifPresent(top ->
                text.append("用得最多的是").append(Apps.label(context, top.getKey())).append("，一周 ").append(Policy.duration(top.getValue())).append("。"));
        text.append(change != null && change <= 0 ? "继续保持！" : "这周试试每天少用一点点。");
        post(WEEK_ID, MORNING, title, text.toString(), Notification.CATEGORY_STATUS);
    }

    /** Gentle nudges by day: at 80% and 100% of the goal, and after 30 minutes of looking at the screen. */
    private void usage(Store store) {
        long now = System.currentTimeMillis();
        long session = store.currentSessionMs(now), start = store.currentSessionStart();
        if (session >= LONG_SESSION_MS && store.p.getLong("longReminded", -1) != start) {
            store.p.edit().putLong("longReminded", start).apply();
            post(LONG_ID, TIPS, "已经连续看了 " + session / 60000 + " 分钟屏幕", "放下手机，看看远处，站起来走两步吧。", Notification.CATEGORY_REMINDER);
        }
        int goal = store.goalMinutes();
        if (goal <= 0) return;
        long used = store.today().useMs, goalMs = goal * 60000L; String day = java.time.LocalDate.now().toString();
        if (used >= goalMs && store.once("tip:goal100:" + day))
            post(GOAL_ID, TIPS, "今天的 " + Policy.duration(goalMs) + " 已经用完了", "剩下的时间试试把手机放远一点，明天的数字会更好看。", Notification.CATEGORY_REMINDER);
        else if (used >= goalMs * 0.8 && used < goalMs && store.once("tip:goal80:" + day))
            post(GOAL_ID, TIPS, "今天已用 " + Policy.duration(used), "离 " + Policy.duration(goalMs) + " 的目标还剩 " + Policy.duration(goalMs - used) + "。", Notification.CATEGORY_REMINDER);
    }

    /** An app lock is about to close: once per app and day. */
    void limitSoon(Store store, String pkg, String name, long leftMs) {
        if (!permitted(context) || !store.once("tip:limit:" + pkg + ":" + java.time.LocalDate.now())) return;
        long minutes = Math.max(1, (leftMs + 59999) / 60000);
        post(LIMIT_ID, TIPS, "「" + name + "」今天还剩 " + minutes + " 分钟", "时间到就会锁定，明天 00:00 自动解锁。先把手头的看完、收个尾吧。", Notification.CATEGORY_REMINDER);
    }

    private void countdown(Policy.Schedule s, ZonedDateTime now) {
        long boundary = Policy.nightEnd(now, s).toInstant().toEpochMilli(), elapsed = android.os.SystemClock.elapsedRealtime();
        if (boundary == lastBoundary && elapsed - lastPost < 60000) return;
        Notification n = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_guard).setColor(0xff4f5fd8)
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
        Notification n = new Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_guard).setColor(0xff4f5fd8)
                .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setCategory(category).setContentIntent(open()).build();
        try { channels().notify(id, n); } catch (SecurityException ignored) { }
    }

    void cancel() { lastBoundary = -1; context.getSystemService(NotificationManager.class).cancel(ID); }
}
