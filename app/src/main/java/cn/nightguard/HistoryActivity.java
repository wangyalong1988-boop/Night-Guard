package cn.nightguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Daily screen time and nightly rest over 7 or 30 days, with a short comparison to the period before. */
public class HistoryActivity extends Activity {
    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};
    private Store store; private int span = 7;
    private int dp(float v) { return BlueUi.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this);
        if (state != null) span = state.getInt("span", 7);
    }
    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("span", span); }

    private LinearLayout root, section;

    private void card() {
        section = BlueUi.column(this); section.setPadding(dp(18), dp(18), dp(18), dp(18)); section.setBackground(BlueUi.background(this, BlueUi.CARD, 0, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(14); root.addView(section, lp);
    }

    private void render() {
        BlueUi.use(this);
        ScrollView scroll = new ScrollView(this); BlueUi.page(this, scroll);
        root = BlueUi.column(this); root.setPadding(dp(12), dp(12), dp(22), dp(28)); scroll.addView(root);
        LinearLayout header = BlueUi.row(this); header.addView(BlueUi.iconButton(this, "back", "返回", this::finish));
        LinearLayout titles = BlueUi.column(this); titles.addView(BlueUi.label(this, "用机记录", 23, BlueUi.INK, true)); BlueUi.gap(titles, 5);
        titles.addView(BlueUi.label(this, "少一点点，也是进步", 12, BlueUi.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);

        LinearLayout tabs = BlueUi.row(this); tabs.setPadding(dp(10), dp(14), 0, 0);
        for (int n : new int[]{7, 30}) {
            boolean on = n == span;
            TextView tab = BlueUi.label(this, n == 7 ? "近 7 天" : "近 30 天", 13, on ? BlueUi.ON_BLUE : BlueUi.MUTED, on);
            tab.setGravity(Gravity.CENTER); tab.setMinHeight(dp(36)); tab.setPadding(dp(16), 0, dp(16), 0);
            tab.setBackground(BlueUi.ripple(this, on ? BlueUi.BLUE : BlueUi.CARD, 10)); tab.setOnClickListener(v -> { span = n; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(8); tabs.addView(tab, lp);
        }
        root.addView(tabs);
        LinearLayout body = BlueUi.column(this); body.setPadding(dp(10), 0, 0, 0); root.addView(body); root = body;

        screenTime(); apps(); nights();
        TextView note = BlueUi.label(this, "只统计守护服务运行期间：从解锁屏幕到息屏算作使用，来通知亮屏不算。数据只存在这台手机里。", 11, BlueUi.MUTED, false);
        note.setPadding(dp(4), dp(16), dp(4), 0); root.addView(note);
    }

    private String dayLabel(LocalDate d, int i, int n) {
        if (n <= 7) return WEEK[d.getDayOfWeek().getValue() - 1];
        return i == n - 1 || (n - 1 - i) % 5 == 0 ? String.valueOf(d.getDayOfMonth()) : "";
    }

    private static String hours(double ms) {
        double h = ms / 3600000.0;
        return h == Math.floor(h) ? String.format(Locale.ROOT, "%.0fh", h) : String.format(Locale.ROOT, "%.1fh", h);
    }

    private void screenTime() {
        List<Store.Day> all = store.days(span * 2); List<Store.Day> days = all.subList(span, span * 2), before = all.subList(0, span);
        double[] values = new double[span], goals = new double[span]; String[] labels = new String[span]; LocalDate[] dates = new LocalDate[span];
        boolean anyGoal = false; long sum = 0, picks = 0; int n = 0; Store.Day best = null;
        for (int i = 0; i < span; i++) {
            Store.Day d = days.get(i); dates[i] = d.date; labels[i] = dayLabel(d.date, i, span);
            values[i] = d.recorded ? d.useMs : Double.NaN;
            int g = store.goalMinutes(d.date); goals[i] = g > 0 ? g * 60000.0 : Double.NaN; anyGoal |= g > 0;
            boolean finished = i < span - 1;
            if (d.recorded && finished) { sum += d.useMs; picks += d.pickups; n++; if (best == null || d.useMs < best.useMs) best = d; }
        }
        long sumBefore = 0; int nBefore = 0;
        for (Store.Day d : before) if (d.recorded) { sumBefore += d.useMs; nBefore++; }

        card();
        section.addView(BlueUi.label(this, "每天亮屏时间", 17, BlueUi.INK, true)); BlueUi.gap(section, 8);
        if (n == 0) section.addView(BlueUi.label(this, "记录满一天后，这里会显示每天的用机时间。", 12, BlueUi.MUTED, false));
        else {
            double avg = (double) sum / n; Integer change = nBefore >= 3 ? Policy.change((double) sumBefore / nBefore, avg) : null;
            LinearLayout big = BlueUi.row(this); big.setGravity(Gravity.BOTTOM);
            big.addView(BlueUi.label(this, "日均 ", 13, BlueUi.MUTED, false));
            big.addView(BlueUi.label(this, Policy.duration((long) avg), 24, BlueUi.INK, true));
            if (change != null) big.addView(BlueUi.label(this, "   " + (change <= 0 ? "比之前少 " + (-change) + "%" : "比之前多 " + change + "%"), 13, change <= 0 ? BlueUi.BLUE : BlueUi.MUTED, true));
            section.addView(big); BlueUi.gap(section, 6);
            section.addView(BlueUi.label(this, "每天拿起手机约 " + Math.round((double) picks / n) + " 次" + (best != null ? " · 最少的一天是 " + best.date.getMonthValue() + "月" + best.date.getDayOfMonth() + "日，" + Policy.duration(best.useMs) : ""), 12, BlueUi.MUTED, false));
        }
        BlueUi.gap(section, 16);
        BarChart chart = new BarChart(this);
        chart.set(values, labels, anyGoal ? goals : null, span - 1, new BarChart.Format() {
            public String value(double v) { return Policy.duration((long) v); }
            public String axis(double v) { return hours(v); }
            public String title(int i) { return (i == span - 1 ? "今天 " : "") + dates[i].getMonthValue() + "月" + dates[i].getDayOfMonth() + "日 周" + WEEK[dates[i].getDayOfWeek().getValue() - 1]; }
        });
        double ref = Policy.CNNIC_DAILY_MINUTES * 60000.0; chart.reference(ref, "全国网民平均");
        section.addView(chart, new LinearLayout.LayoutParams(-1, dp(200)));
        BlueUi.gap(section, 8);
        section.addView(BlueUi.label(this, "点柱子查看当天数值。" + (anyGoal ? "虚线是每天的目标，" : "") + "点线是全国网民日均上网约 "
                + Policy.duration((long) ref) + "（CNNIC 第57次报告，2025年12月，含电脑等所有设备）。", 11, BlueUi.MUTED, false));
        if (n > 0) {
            Integer vs = Policy.change(ref, (double) sum / n);
            if (vs != null) { BlueUi.gap(section, 6); section.addView(BlueUi.label(this, "你的日均比全国网民平均" + (vs <= 0 ? "少 " + (-vs) + "%" : "多 " + vs + "%"), 12, vs <= 0 ? BlueUi.BLUE : BlueUi.INK, true)); }
        }
    }

    /** Apps ranked by time over the period; tap one to set or change its daily limit. */
    private void apps() {
        Map<String, Long> totals = store.appTotals(span), today = store.appTimes(LocalDate.now()); Map<String, Integer> limits = store.desiredLimits();
        List<Map.Entry<String, Long>> rows = new ArrayList<>(totals.entrySet()); rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        card();
        section.addView(BlueUi.label(this, "按应用", 17, BlueUi.INK, true)); BlueUi.gap(section, 8);
        if (rows.isEmpty()) { section.addView(BlueUi.label(this, "用一会儿手机后，这里会按应用列出用时。", 12, BlueUi.MUTED, false)); return; }
        section.addView(BlueUi.label(this, "近 " + span + " 天日均用时。点应用设置每日限额：用满后白天也会拦下，放宽限额要到明天才生效。", 12, BlueUi.MUTED, false));
        BlueUi.gap(section, 10);
        long max = rows.get(0).getValue();
        for (int i = 0; i < Math.min(8, rows.size()); i++) {
            String pkg = rows.get(i).getKey(); long avg = rows.get(i).getValue() / span; Long now = today.get(pkg); Integer limit = limits.get(pkg); String name = Apps.label(this, pkg);
            LinearLayout row = BlueUi.row(this); row.setPadding(0, dp(10), 0, dp(10)); row.setBackground(BlueUi.ripple(this, android.graphics.Color.TRANSPARENT, 10));
            ImageView icon = new ImageView(this); icon.setImageDrawable(AppCatalog.icon(this, pkg)); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            LinearLayout col = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(12), 0, dp(8), 0); row.addView(col, lp);
            LinearLayout top = BlueUi.row(this); TextView label = BlueUi.label(this, name, 14, BlueUi.INK, true); label.setMaxLines(1); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(BlueUi.label(this, "日均 " + Policy.duration(avg), 12, BlueUi.INK, false)); col.addView(top); BlueUi.gap(col, 6);
            LinearLayout track = BlueUi.row(this); track.setBackground(BlueUi.background(this, BlueUi.LINE, 0, 3)); track.setWeightSum(1000);
            View fill = new View(this); fill.setBackground(BlueUi.background(this, BlueUi.BLUE, 0, 3));
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), Math.max(12, rows.get(i).getValue() * 1000 / Math.max(1, max)))); col.addView(track, new LinearLayout.LayoutParams(-1, dp(6))); BlueUi.gap(col, 6);
            col.addView(BlueUi.label(this, "今天 " + Policy.duration(now == null ? 0 : now) + (limit != null ? " · 限额 " + Policy.duration(limit * 60000L) : " · 点击设置限额"), 11, limit != null ? BlueUi.BLUE : BlueUi.MUTED, false));
            row.setContentDescription(name + "，日均" + Policy.duration(avg) + (limit != null ? "，限额" + Policy.duration(limit * 60000L) : ""));
            row.setOnClickListener(v -> chooseLimit(pkg, name, limit));
            section.addView(row);
        }
    }

    private void chooseLimit(String pkg, String name, Integer current) {
        int[] options = {0, 15, 30, 45, 60, 90, 120}; String[] names = new String[options.length];
        for (int i = 0; i < options.length; i++) names[i] = options[i] == 0 ? "不限" : "每天 " + Policy.duration(options[i] * 60000L);
        int checked = 0; for (int i = 0; i < options.length; i++) if (current != null && options[i] == current) checked = i;
        new AlertDialog.Builder(this).setTitle("“" + name + "”每天最多用多久？").setSingleChoiceItems(names, checked, (d, which) -> {
            d.dismiss();
            boolean now = store.setLimit(pkg, options[which]);
            Toast.makeText(this, now ? (options[which] == 0 ? "已取消限额" : "已设为每天 " + Policy.duration(options[which] * 60000L)) : "放宽的限额明天生效，今天仍按原来的", Toast.LENGTH_LONG).show();
            render();
        }).setNegativeButton("取消", null).show();
    }

    private void nights() {
        LocalDate[] dates = new LocalDate[span]; double[] values = store.restHistory(span, dates); String[] labels = new String[span];
        long sum = 0; int n = 0;
        for (int i = 0; i < span; i++) { labels[i] = dayLabel(dates[i], i, span); if (!Double.isNaN(values[i])) { sum += (long) values[i]; n++; } }
        card();
        section.addView(BlueUi.label(this, "夜里放下手机", 17, BlueUi.INK, true)); BlueUi.gap(section, 8);
        Policy.Schedule s = store.schedule();
        section.addView(BlueUi.label(this, n == 0 ? "过完一个完整的夜晚后，这里会显示 " + s.text() + " 之间放下手机的时长。" : "平均每晚放下手机 " + Policy.duration(sum / n) + "，限制时段共 " + Policy.duration(s.minutes() * 60000L), 12, BlueUi.MUTED, false));
        BlueUi.gap(section, 16);
        BarChart chart = new BarChart(this);
        chart.set(values, labels, null, -1, new BarChart.Format() {
            public String value(double v) { return Policy.duration((long) v); }
            public String axis(double v) { return hours(v); }
            public String title(int i) { return dates[i].getMonthValue() + "月" + dates[i].getDayOfMonth() + "日夜里"; }
        });
        section.addView(chart, new LinearLayout.LayoutParams(-1, dp(170)));
    }
}
