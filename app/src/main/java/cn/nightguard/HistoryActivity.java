package cn.nightguard;

import android.app.Activity;
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

    private void card(String title) { BlueUi.sectionHeader(root, title, null, null); section = BlueUi.group(root, true); }

    private void render() {
        BlueUi.use(this);
        ScrollView scroll = new ScrollView(this); BlueUi.page(this, scroll);
        LinearLayout page = BlueUi.column(this); scroll.addView(page);
        page.addView(BlueUi.navBar(this, "用机记录", "少一点点，也是进步"));
        root = BlueUi.column(this); root.setPadding(dp(20), dp(10), dp(20), dp(32)); page.addView(root);
        root.addView(BlueUi.segmented(this, new String[]{"近 7 天", "近 30 天"}, span == 7 ? 0 : 1, i -> { span = i == 0 ? 7 : 30; render(); }), new LinearLayout.LayoutParams(-1, -2));

        screenTime(); apps(); nights();
        BlueUi.footnote(root, "只统计守护服务运行期间：从解锁屏幕到屏幕熄灭算作使用，来通知亮屏不算。数据只存在这台手机里。");
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

        if (n > 0) {
            double avg = (double) sum / n; Integer change = nBefore >= 3 ? Policy.change((double) sumBefore / nBefore, avg) : null;
            BlueUi.gap(root, 16);
            BlueUi.tiles(root,
                    BlueUi.statTile(this, "日均亮屏", Policy.duration((long) avg), change == null ? "还没有可比较的数据" : change <= 0 ? "比之前少 " + (-change) + "%" : "比之前多 " + change + "%"),
                    BlueUi.statTile(this, "日均拿起", Math.round((double) picks / n) + " 次", "从解锁开始算"),
                    BlueUi.statTile(this, "最少的一天", best != null ? Policy.duration(best.useMs) : "—", best != null ? best.date.getMonthValue() + "月" + best.date.getDayOfMonth() + "日" : ""));
        }
        card("每天亮屏时间");
        if (n == 0) { section.addView(BlueUi.label(this, "记录满一天后，这里会显示每天的用机时间。", 12, BlueUi.MUTED, false)); BlueUi.gap(section, 12); }
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
        card("按应用");
        if (rows.isEmpty()) { section.addView(BlueUi.label(this, "用一会儿手机后，这里会按应用列出用时。", 12, BlueUi.MUTED, false)); return; }
        section.addView(BlueUi.label(this, "近 " + span + " 天日均用时。点应用可以加应用锁：每天用满就锁到明天。", 12, BlueUi.MUTED, false));
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
            col.addView(BlueUi.label(this, "今天 " + Policy.duration(now == null ? 0 : now) + (limit != null ? " · 应用锁 每天 " + Policy.duration(limit * 60000L) : " · 点击加锁"), 11, limit != null ? BlueUi.BLUE : BlueUi.MUTED, false));
            row.setContentDescription(name + "，日均" + Policy.duration(avg) + (limit != null ? "，应用锁每天" + Policy.duration(limit * 60000L) : ""));
            row.setOnClickListener(v -> Locks.edit(this, store, pkg, this::render));
            section.addView(row);
        }
    }

    private void nights() {
        LocalDate[] dates = new LocalDate[span]; double[] values = store.restHistory(span, dates); String[] labels = new String[span];
        long sum = 0; int n = 0;
        for (int i = 0; i < span; i++) { labels[i] = dayLabel(dates[i], i, span); if (!Double.isNaN(values[i])) { sum += (long) values[i]; n++; } }
        card("夜里放下手机");
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
