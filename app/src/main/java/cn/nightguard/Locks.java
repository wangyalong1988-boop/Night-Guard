package cn.nightguard;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;

/**
 * App lock: a daily time per app. When it is used up the app is locked until midnight. Shared by
 * the app lock tab, the picker, today's top apps and the history page so every entry point edits
 * a lock the same way.
 */
final class Locks {
    private Locks() {}

    /** One app's lock as shown in lists. */
    static final class Status {
        final String pkg; final long usedMs; final Integer today, desired;
        Status(String pkg, long usedMs, Integer today, Integer desired) { this.pkg = pkg; this.usedMs = usedMs; this.today = today; this.desired = desired; }
        boolean locked() { return Policy.limitReached(usedMs, today); }
        boolean warning() { return Policy.limitWarning(usedMs, today); }
        long leftMs() { return today == null ? 0 : Math.max(0, today * 60000L - usedMs); }
        /** Text for a change that waits until tomorrow, or null. */
        String pending() {
            if (Objects.equals(today, desired)) return null;
            return desired == null ? "明天起取消" : "明天起改为 " + Policy.duration(desired * 60000L);
        }
        int color() { return today == null ? BlueUi.SUBTLE : locked() ? BlueUi.DANGER : Policy.limitFraction(usedMs, today) >= 0.8 ? BlueUi.STAR : BlueUi.BLUE; }
    }

    /** Every app with a lock today or from tomorrow, most used first. */
    static List<Status> all(Store store) {
        Map<String, Integer> today = store.limits(), desired = store.desiredLimits(); Map<String, Long> used = store.appTimes(LocalDate.now());
        Set<String> pkgs = new TreeSet<>(today.keySet()); pkgs.addAll(desired.keySet());
        List<Status> r = new ArrayList<>();
        for (String pkg : pkgs) { Long u = used.get(pkg); r.add(new Status(pkg, u == null ? 0 : u, today.get(pkg), desired.get(pkg))); }
        r.sort((a, b) -> a.locked() != b.locked() ? (a.locked() ? -1 : 1) : Long.compare(b.usedMs, a.usedMs));
        return r;
    }

    static Status of(Store store, String pkg) {
        Long u = store.appTimes(LocalDate.now()).get(pkg);
        return new Status(pkg, u == null ? 0 : u, store.limits().get(pkg), store.desiredLimits().get(pkg));
    }

    /** Opens the sheet that sets, changes or removes one app's daily lock. */
    static void edit(Activity a, Store store, String pkg, Runnable done) {
        String name = Apps.label(a, pkg); Status s = of(store, pkg); Integer current = s.desired;
        String used = s.usedMs >= 60000 ? "今天已用 " + Policy.duration(s.usedMs) + "。" : "";
        BlueUi.Sheet sheet = new BlueUi.Sheet(a, current == null ? "给「" + name + "」加锁" : "「" + name + "」的应用锁",
                "每天最多用多久？用满就锁定，明天 00:00 自动解锁。" + used);

        int start = current != null ? current : 60;
        NumberPicker hours = picker(a, 0, Policy.LIMIT_MAX / 60, null), minutes = picker(a, 0, 11, fives());
        hours.setValue(start / 60); minutes.setValue((start % 60) / 5);
        LinearLayout pickers = BlueUi.row(a); pickers.setGravity(Gravity.CENTER); pickers.setPadding(0, BlueUi.dp(a, 4), 0, BlueUi.dp(a, 4));
        pickers.addView(hours, new LinearLayout.LayoutParams(BlueUi.dp(a, 72), BlueUi.dp(a, 150)));
        pickers.addView(BlueUi.label(a, "小时", BlueUi.BODY, BlueUi.MUTED, false));
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(BlueUi.dp(a, 72), BlueUi.dp(a, 150)); gapLp.leftMargin = BlueUi.dp(a, 24);
        pickers.addView(minutes, gapLp);
        pickers.addView(BlueUi.label(a, "分钟", BlueUi.BODY, BlueUi.MUTED, false));
        sheet.view(pickers);

        LinearLayout quick = BlueUi.row(a); quick.setGravity(Gravity.CENTER);
        int[] presets = {15, 30, 60, 120};
        for (int i = 0; i < presets.length; i++) {
            int m = presets[i]; TextView chip = BlueUi.label(a, Policy.duration(m * 60000L), BlueUi.FOOTNOTE, BlueUi.BLUE, true);
            chip.setGravity(Gravity.CENTER); chip.setMinHeight(BlueUi.dp(a, 38)); chip.setBackground(BlueUi.ripple(a, BlueUi.PALE, 19));
            chip.setOnClickListener(v -> { hours.setValue(m / 60); minutes.setValue((m % 60) / 5); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); if (i > 0) lp.leftMargin = BlueUi.dp(a, 8); quick.addView(chip, lp);
        }
        sheet.view(quick);

        sheet.button(current == null ? "加锁" : "保存", true, () -> save(a, store, pkg, name, Policy.limitMinutes(hours.getValue(), minutes.getValue() * 5), done));
        if (current != null) sheet.button("取消应用锁", false, () -> save(a, store, pkg, name, 0, done));
        TextView rule = BlueUi.label(a, "调短立即生效；调长或取消要到明天才生效，免得一时冲动。", BlueUi.CAPTION, BlueUi.MUTED, false);
        rule.setGravity(Gravity.CENTER); rule.setPadding(0, BlueUi.dp(a, 12), 0, 0); sheet.view(rule);
        sheet.show();
    }

    private static String[] fives() { String[] r = new String[12]; for (int i = 0; i < 12; i++) r[i] = String.format(Locale.ROOT, "%02d", i * 5); return r; }

    private static NumberPicker picker(Context c, int min, int max, String[] labels) {
        NumberPicker p = new NumberPicker(c); p.setMinValue(min); p.setMaxValue(max);
        if (labels != null) p.setDisplayedValues(labels);
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS); p.setTextColor(BlueUi.INK); p.setTextSize(BlueUi.dp(c, 22));
        return p;
    }

    private static void save(Activity a, Store store, String pkg, String name, int minutes, Runnable done) {
        Integer before = store.limits().get(pkg);
        boolean now = store.setLimit(pkg, minutes);
        String message;
        if (minutes == 0) message = now ? "已取消「" + name + "」的应用锁" : "应用锁明天起取消，今天仍是 " + Policy.duration(before * 60000L);
        else if (now) message = "已为「" + name + "」加锁：每天 " + Policy.duration(minutes * 60000L);
        else message = "放宽的时长明天生效，今天仍是 " + Policy.duration(before * 60000L);
        Toast.makeText(a, message, Toast.LENGTH_LONG).show();
        if (done != null) done.run();
    }
}
