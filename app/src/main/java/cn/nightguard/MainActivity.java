package cn.nightguard;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.graphics.Typeface;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};
    private Store store;
    private LinearLayout root, section, preview;
    private TextView countdown, clock, phase, appSummary, badge, interceptionDetail, interceptionState;
    private BlueUi.NightBar bar;
    private BlueUi.Meter meter;
    private TextView todayUsed, todayDetail, focusLink;
    private boolean clockNight;
    private String target, reasonDraft = "", phraseDraft = "";
    private EditText reasonInput, phraseInput;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timer = new Runnable() { public void run() { if (countdown != null) refreshCountdown(); refreshClock(); handler.postDelayed(this, 1000); } };

    private int dp(float value) { return BlueUi.dp(this, value); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this);
        if (state != null) { reasonDraft = state.getString("reasonDraft", ""); phraseDraft = state.getString("phraseDraft", ""); }
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null; render(); }
    @Override public void onResume() { super.onResume(); render(); handler.removeCallbacks(timer); handler.post(timer); TodayWidget.update(this, store); }
    @Override public void onSaveInstanceState(Bundle state) { captureDraft(); state.putString("reasonDraft", reasonDraft); state.putString("phraseDraft", phraseDraft); super.onSaveInstanceState(state); }
    @Override public void onPause() { captureDraft(); handler.removeCallbacks(timer); super.onPause(); }
    @Override public void onDestroy() { loader.shutdownNow(); handler.removeCallbacks(timer); super.onDestroy(); }

    private void captureDraft() { if (reasonInput != null) reasonDraft = reasonInput.getText().toString(); if (phraseInput != null) phraseDraft = phraseInput.getText().toString(); }

    // ---- small builders ---------------------------------------------------------------------

    private TextView text(String value, int size) { TextView t = BlueUi.label(this, value, size, size >= 17 ? BlueUi.INK : BlueUi.MUTED, size >= 17); section.addView(t); BlueUi.gap(section, 10); return t; }
    private void button(String value, boolean primary, Runnable action) { BlueUi.gap(section, 10); section.addView(BlueUi.button(this, value, primary, action), new LinearLayout.LayoutParams(-1, -2)); }
    private void card() { card(BlueUi.CARD); }
    private void card(int color) {
        section = BlueUi.column(this); section.setPadding(dp(18), dp(18), dp(18), dp(18)); section.setBackground(BlueUi.background(this, color, 0, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(14); root.addView(section, lp);
    }
    private EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setTextColor(BlueUi.INK); e.setHintTextColor(BlueUi.MUTED);
        e.setPadding(dp(14), dp(14), dp(14), dp(14)); e.setBackground(BlueUi.background(this, BlueUi.PAGE, BlueUi.LINE, 10)); section.addView(e); return e;
    }
    private LinearLayout heading(String title, String linkText, Runnable action) {
        LinearLayout row = BlueUi.row(this); row.addView(BlueUi.label(this, title, 17, BlueUi.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (linkText != null) row.addView(BlueUi.link(this, linkText, action));
        section.addView(row); return row;
    }

    private boolean guardEnabled() { String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return enabled != null && enabled.contains(getPackageName() + "/"); }
    private boolean backgroundConfirmed() { return store.p.getBoolean("backgroundConfirmed", false); }

    // ---- page -------------------------------------------------------------------------------

    private void render() {
        BlueUi.use(this); Policy.Schedule schedule = store.schedule(); clockNight = store.night(); store.selected();
        captureDraft(); reasonInput = null; phraseInput = null; countdown = null; todayUsed = null; focusLink = null;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); BlueUi.page(this, scroll);
        root = BlueUi.column(this); root.setPadding(dp(22), dp(24), dp(22), dp(24)); scroll.addView(root); section = root;

        LinearLayout header = BlueUi.row(this); LinearLayout titles = BlueUi.column(this);
        titles.addView(BlueUi.label(this, "息屏", 26, BlueUi.INK, true)); BlueUi.gap(titles, 7);
        titles.addView(BlueUi.label(this, greeting(), 12, BlueUi.MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1)); badge = BlueUi.pill(this, "", BlueUi.BLUE, BlueUi.PALE); header.addView(badge); root.addView(header);

        target = getIntent().getStringExtra("target"); if (target == null) target = store.p.getString("request", null);
        // A request only makes sense while the app is actually stopped (night, focus or its daily limit).
        if (target != null && (Apps.safe(this, target) || store.blockReason(target, store.selected()) == null)) { store.cancelRequest(); getIntent().removeExtra("target"); target = null; }
        if (target != null) renderUnlock();

        renderSetup();
        if (!clockNight) renderToday();
        renderSchedule(schedule);
        renderRest(schedule);
        if (clockNight) renderToday();
        renderNights();

        card(); heading("限制清单", "管理应用  ›", () -> startActivity(new Intent(this, AppPickerActivity.class)));
        appSummary = BlueUi.label(this, "正在读取已选应用…", 13, BlueUi.MUTED, false); section.addView(appSummary); BlueUi.gap(section, 14); preview = BlueUi.row(this); section.addView(preview);
        if (store.p.contains("pending")) { BlueUi.gap(section, 12); text("名单调整已保存，减少的限制将在 " + schedule.endText() + " 后生效。", 12); }
        loader.execute(() -> { try { List<AppCatalog.Entry> entries = AppCatalog.load(this); runOnUiThread(() -> { if (!isDestroyed()) showPreview(entries); }); }
            catch (RuntimeException e) { runOnUiThread(() -> { if (!isDestroyed()) appSummary.setText("点击管理应用，检查限制清单"); }); } });

        card(); text("守护状态", 17);
        statusRow("shield", "应用拦截", interceptionText(), GuardService.connected ? "已连接" : "待检查", this::accessibilitySettings);
        BlueUi.divider(section);
        statusRow("bell", "提醒与倒计时", "睡前15分钟提醒 · 夜间倒计时 · 晨间小结", GuardNotification.permitted(this) ? "已允许" : "未开启", this::notificationSettings);
        BlueUi.divider(section);
        statusRow("clock", "华为后台运行", backgroundConfirmed() ? "已由你确认，系统仍可能限制后台" : "允许自启动、关联启动及后台活动", backgroundConfirmed() ? "已确认" : "去设置", this::backgroundHelp);
        BlueUi.divider(section);
        statusRow("lock", "临时解锁规则", "写明原因 · 冷静10分钟起 · 限时5分钟", "查看", this::unlockInfo);

        LinearLayout bottom = BlueUi.row(this);
        bottom.addView(BlueUi.link(this, "规则与说明", this::rules), new LinearLayout.LayoutParams(0, dp(52), 1));
        bottom.addView(BlueUi.link(this, "解锁记录", this::history), new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bottom);
        refreshClock();
    }

    private String greeting() {
        int h = LocalTime.now().getHour();
        if (clockNight) return "夜深了，手机交给我，你去睡吧";
        if (h < 11) return "早上好，昨晚睡得怎么样？";
        if (h < 18) return "白天正常使用，夜里我来守着";
        return "晚上好，记得早点准备休息";
    }

    private String interceptionText() { return GuardService.connected ? "服务已连接，按清单拦截，包括分屏和小窗" : guardEnabled() ? "权限已开启，等待服务连接" : "开启后才能限制应用"; }

    /** A short checklist that disappears once everything needed is in place. */
    private void renderSetup() {
        boolean a = guardEnabled(), b = !store.desired().isEmpty(), c = backgroundConfirmed();
        if (a && b && c) return;
        int done = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0);
        card(BlueUi.PALE); heading("还差 " + (3 - done) + " 步就能开始守护", null, null); BlueUi.gap(section, 6);
        setupStep(1, a, "开启应用拦截", "在系统无障碍设置中打开“息屏”", this::accessibilitySettings);
        setupStep(2, b, "选择夜里要限制的应用", "短视频、游戏、社交……浏览器已帮你识别", () -> startActivity(new Intent(this, AppPickerActivity.class)));
        setupStep(3, c, "允许后台运行", "华为手机需要手动允许，否则夜里可能失效", this::backgroundHelp);
    }

    private void setupStep(int n, boolean done, String title, String detail, Runnable action) {
        LinearLayout row = BlueUi.row(this); row.setPadding(0, dp(10), 0, dp(10)); row.setBackground(BlueUi.ripple(this, android.graphics.Color.TRANSPARENT, 10));
        View mark;
        if (done) mark = new BlueUi.Glyph(this, "check", BlueUi.BLUE);
        else { TextView num = BlueUi.label(this, String.valueOf(n), 13, BlueUi.ON_BLUE, true); num.setGravity(Gravity.CENTER); num.setBackground(BlueUi.background(this, BlueUi.BLUE, 0, 12)); mark = num; }
        row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout labels = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(12); row.addView(labels, lp);
        TextView t = BlueUi.label(this, title, 15, done ? BlueUi.MUTED : BlueUi.INK, !done); if (done) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        labels.addView(t); BlueUi.gap(labels, 4); labels.addView(BlueUi.label(this, detail, 12, BlueUi.MUTED, false));
        if (!done) { row.addView(new BlueUi.Glyph(this, "next", BlueUi.MUTED), new LinearLayout.LayoutParams(dp(17), dp(17))); row.setOnClickListener(v -> action.run()); }
        section.addView(row);
    }

    private void renderSchedule(Policy.Schedule schedule) {
        card(BlueUi.HERO);
        LinearLayout head = BlueUi.row(this); head.addView(BlueUi.label(this, "每日限制时段", 13, BlueUi.BLUE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = BlueUi.link(this, "修改", this::editSchedule); edit.setContentDescription("修改限制时段"); head.addView(edit);
        section.addView(head); BlueUi.gap(section, 12);
        TextView range = BlueUi.label(this, schedule.startText() + "  –  " + schedule.endText(), 32, BlueUi.INK, true);
        range.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); range.setFontFeatureSettings("tnum"); section.addView(range); BlueUi.gap(section, 6);
        int hours = schedule.minutes() / 60, minutes = schedule.minutes() % 60;
        section.addView(BlueUi.label(this, "每晚 " + hours + " 小时" + (minutes > 0 ? minutes + " 分钟" : "") + (schedule.start > schedule.end ? " · 跨越午夜" : ""), 12, BlueUi.MUTED, false));
        BlueUi.gap(section, 16);
        bar = new BlueUi.NightBar(this); section.addView(bar, new LinearLayout.LayoutParams(-1, dp(16))); BlueUi.gap(section, 12);
        LinearLayout counter = BlueUi.row(this); phase = BlueUi.label(this, "", 12, BlueUi.MUTED, false); counter.addView(phase, new LinearLayout.LayoutParams(0, -2, 1));
        clock = BlueUi.label(this, "", 17, BlueUi.INK, true); clock.setFontFeatureSettings("tnum"); counter.addView(clock); section.addView(counter);
        Policy.Schedule desired = store.desiredSchedule();
        if (!desired.equals(schedule)) { BlueUi.gap(section, 12); section.addView(BlueUi.label(this, "已改为 " + desired.text() + "，今晚 " + schedule.endText() + " 后生效", 12, BlueUi.BLUE, false)); }
    }

    /** Today's screen time against the goal, refreshed every second while the page is open. */
    private void renderToday() {
        card(); heading("今天", "用机记录  ›", () -> startActivity(new Intent(this, HistoryActivity.class)));
        if (store.recordingSince() == null) {
            section.addView(BlueUi.label(this, "开启应用拦截后，会从那一刻开始记录每天的亮屏时间。", 12, BlueUi.MUTED, false)); todayUsed = null; return;
        }
        BlueUi.gap(section, 8);
        LinearLayout big = BlueUi.row(this); big.setGravity(Gravity.BOTTOM);
        big.addView(BlueUi.label(this, "已用 ", 14, BlueUi.MUTED, false));
        todayUsed = BlueUi.label(this, "", 26, BlueUi.INK, true); todayUsed.setFontFeatureSettings("tnum"); big.addView(todayUsed);
        section.addView(big); BlueUi.gap(section, 12);
        meter = new BlueUi.Meter(this); section.addView(meter, new LinearLayout.LayoutParams(-1, dp(10))); BlueUi.gap(section, 10);
        todayDetail = BlueUi.label(this, "", 12, BlueUi.MUTED, false); section.addView(todayDetail);
        Map<String, Long> apps = store.appTimes(LocalDate.now());
        apps.entrySet().stream().max(Map.Entry.comparingByValue()).filter(e -> e.getValue() >= 60000).ifPresent(top -> {
            BlueUi.gap(section, 6); section.addView(BlueUi.label(this, "用得最多：" + Apps.label(this, top.getKey()) + " " + Policy.duration(top.getValue()), 12, BlueUi.MUTED, false)); });
        if (!store.hasGoal()) meter.setVisibility(View.GONE);
        BlueUi.gap(section, 6); LinearLayout row = BlueUi.row(this);
        row.addView(BlueUi.link(this, store.hasGoal() ? "调整目标" : "设定每日目标", this::chooseGoal));
        focusLink = BlueUi.link(this, "", this::chooseFocus); row.addView(focusLink);
        section.addView(row);
        refreshToday();
    }

    /** Focus: the restricted list is stopped for a while, by choice; it cannot be ended early. */
    private void chooseFocus() {
        if (store.focusing()) { Toast.makeText(this, "专注还剩 " + Policy.duration(store.focusRemainingMs()) + "，结束后自动解除", Toast.LENGTH_SHORT).show(); return; }
        if (store.desired().isEmpty()) { Toast.makeText(this, "先在限制清单里选几个容易分心的应用", Toast.LENGTH_LONG).show(); startActivity(new Intent(this, AppPickerActivity.class)); return; }
        int[] minutes = {25, 45, 60, 90}; String[] names = new String[minutes.length];
        for (int i = 0; i < minutes.length; i++) names[i] = Policy.duration(minutes[i] * 60000L);
        new AlertDialog.Builder(this).setTitle("专注多久？").setItems(names, (d, which) -> new AlertDialog.Builder(this)
                .setTitle("专注 " + names[which])
                .setMessage("这段时间里，限制清单中的 " + store.desired().size() + " 个应用都会被拦下，时间到自动解除，不能提前结束。电话、闹钟等始终可用。")
                .setPositiveButton("开始专注", (d2, w2) -> { store.startFocus(minutes[which]); Toast.makeText(this, "开始专注，加油", Toast.LENGTH_SHORT).show(); render(); })
                .setNegativeButton("再想想", null).show()).show();
    }

    private void refreshToday() {
        if (todayUsed == null) return;
        if (focusLink != null) { boolean on = store.focusing(); focusLink.setText(on ? "专注中 · 还剩 " + Policy.duration(store.focusRemainingMs()) : "专注一下"); }
        Store.Day t = store.today(); int goal = store.goalMinutes(); long goalMs = goal * 60000L;
        todayUsed.setText(Policy.duration(t.useMs)); meter.set(t.useMs, goalMs);
        String picks = "拿起 " + t.pickups + " 次";
        if (goal <= 0) { todayDetail.setText(picks + " · 设个目标，每周少用一点点"); return; }
        String status = t.useMs < goalMs ? "目标 " + Policy.duration(goalMs) + " · 还剩 " + Policy.duration(goalMs - t.useMs) : "已超出目标 " + Policy.duration(t.useMs - goalMs);
        String pace = goal > store.goalTarget() ? "\n每周自动减少，最终目标 " + Policy.duration(store.goalTarget() * 60000L) : "";
        todayDetail.setText(status + " · " + picks + pace);
    }

    private void chooseGoal() {
        int[] targets = {60, 90, 120, 150, 180, 240, 300}; String[] names = new String[targets.length + (store.hasGoal() ? 1 : 0)];
        for (int i = 0; i < targets.length; i++) names[i] = "每天 " + Policy.duration(targets[i] * 60000L);
        if (store.hasGoal()) names[targets.length] = "不设目标";
        double avg = store.recentAverageMs(7);
        new AlertDialog.Builder(this).setTitle(Double.isNaN(avg) ? "最终想把每天用机时间控制在？" : "最近日均 " + Policy.duration((long) avg) + "，最终想控制在？")
                .setItems(names, (d, which) -> {
                    if (which == targets.length) { store.clearGoal(); render(); return; }
                    int target = targets[which];
                    String[] paces = {"温和：每周少 10 分钟（推荐）", "积极：每周少 20 分钟", "直接：从今天起就按目标"};
                    int[] steps = {10, 20, 0};
                    new AlertDialog.Builder(this).setTitle("怎么减少？").setItems(paces, (d2, w2) -> {
                        int base = store.setGoal(target, steps[w2]);
                        Toast.makeText(this, base > target ? "从每天 " + Policy.duration(base * 60000L) + " 开始，每周减少 " + steps[w2] + " 分钟，直到 " + Policy.duration(target * 60000L) : "目标：每天 " + Policy.duration(target * 60000L), Toast.LENGTH_LONG).show();
                        render();
                    }).show();
                }).show();
    }

    /** How long the phone was put down during the last night (or tonight so far), with a timeline. */
    private void renderRest(Policy.Schedule schedule) {
        LocalDate key = clockNight ? Policy.nightKey(ZonedDateTime.now(), schedule) : store.recentNights(1).get(0).date;
        Store.NightUse use = store.rest(key);
        if (use == null) return;
        Policy.Rest r = use.rest;
        card(); heading(clockNight ? "今晚到现在" : "昨晚", null, null); BlueUi.gap(section, 10);
        LinearLayout big = BlueUi.row(this); big.setGravity(Gravity.BOTTOM);
        big.addView(BlueUi.label(this, "放下手机 ", 14, BlueUi.MUTED, false));
        TextView hours = BlueUi.label(this, Policy.duration(r.restMs()), 26, BlueUi.INK, true); hours.setFontFeatureSettings("tnum"); big.addView(hours);
        section.addView(big); BlueUi.gap(section, 8);
        String detail = r.pickups == 0 ? (use.ongoing() ? "到现在还没有拿起过手机" : "整晚都没有拿起手机")
                : "拿起 " + r.pickups + " 次 · 共用了 " + Policy.duration(r.usedMs) + " · 最长连续放下 " + Policy.duration(r.longestRestMs);
        section.addView(BlueUi.label(this, detail, 12, BlueUi.MUTED, false)); BlueUi.gap(section, 14);
        BlueUi.UseTimeline line = new BlueUi.UseTimeline(this); line.set(use); line.setContentDescription(detail);
        section.addView(line, new LinearLayout.LayoutParams(-1, dp(14))); BlueUi.gap(section, 6);
        LinearLayout ends = BlueUi.row(this);
        ends.addView(BlueUi.label(this, schedule.startText(), 11, BlueUi.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView legend = BlueUi.label(this, "■ 使用", 11, BlueUi.STAR, false); ends.addView(legend); ends.addView(BlueUi.label(this, "   " + schedule.endText(), 11, BlueUi.MUTED, false));
        section.addView(ends);
        if (use.from > use.start) { BlueUi.gap(section, 8); section.addView(BlueUi.label(this, "灰色部分是开始记录之前的时间", 11, BlueUi.MUTED, false)); }
    }

    /** Last seven nights as stars: full = no unlock, half = unlocked, dot = not guarded. */
    private void renderNights() {
        List<Store.Night> nights = store.recentNights(7);
        boolean any = false; for (Store.Night n : nights) any |= n.recorded;
        if (!any && !clockNight) return;
        card(); int streak = store.calmStreak();
        heading("最近 7 晚", null, null); BlueUi.gap(section, 4);
        String sub = streak >= 2 ? "已连续 " + streak + " 晚一次都没解锁" : clockNight ? "今晚已拦下 " + store.blocksTonight() + " 次" : "一次都不解锁，就能点亮一颗星";
        section.addView(BlueUi.label(this, sub, 12, BlueUi.MUTED, false)); BlueUi.gap(section, 14);
        LinearLayout row = BlueUi.row(this); row.setWeightSum(7);
        for (Store.Night n : nights) {
            LinearLayout col = BlueUi.column(this); col.setGravity(Gravity.CENTER_HORIZONTAL);
            String kind = !n.recorded || !n.guarded ? "dot" : n.unlocks == 0 ? "star-fill" : "star-half";
            int color = kind.equals("dot") ? BlueUi.LINE : BlueUi.STAR;
            col.addView(new BlueUi.Glyph(this, kind, color), new LinearLayout.LayoutParams(dp(26), dp(26))); BlueUi.gap(col, 6);
            col.addView(BlueUi.label(this, WEEK[n.date.getDayOfWeek().getValue() - 1], 11, BlueUi.MUTED, false));
            col.setContentDescription(n.date.getMonthValue() + "月" + n.date.getDayOfMonth() + "日，" + (!n.recorded || !n.guarded ? "未守护" : n.unlocks == 0 ? "安稳，拦下" + n.blocks + "次" : "解锁" + n.unlocks + "次"));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        }
        section.addView(row);
    }

    private void showPreview(List<AppCatalog.Entry> entries) {
        Set<String> selected = store.selected(); List<AppCatalog.Entry> chosen = new ArrayList<>(); int browserCount = 0;
        for (AppCatalog.Entry e : entries) if (selected.contains(e.pkg)) { chosen.add(e); if (e.isBrowser()) browserCount++; }
        appSummary.setText(chosen.size() + " 个应用纳入限制" + (browserCount > 0 ? " · 含 " + browserCount + " 个浏览器" : ""));
        preview.removeAllViews();
        if (chosen.isEmpty()) { preview.addView(BlueUi.link(this, "添加娱乐、社交或游戏应用  →", () -> startActivity(new Intent(this, AppPickerActivity.class)))); return; }
        int slots = Math.max(1, Math.min(5, (getResources().getConfiguration().screenWidthDp - 130) / 50));
        for (int i = 0; i < Math.min(chosen.size(), slots); i++) {
            AppCatalog.Entry e = chosen.get(i); ImageView icon = new ImageView(this); icon.setImageDrawable(e.icon != null ? e.icon : AppCatalog.icon(this, e.pkg));
            icon.setContentDescription(e.label); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38)); lp.rightMargin = dp(12); preview.addView(icon, lp);
        }
        if (chosen.size() > slots) preview.addView(BlueUi.label(this, "+" + (chosen.size() - slots), 14, BlueUi.MUTED, true));
    }

    private void statusRow(String icon, String title, String detail, String state, Runnable action) {
        LinearLayout row = BlueUi.row(this); row.setPadding(0, dp(14), 0, dp(14)); row.setMinimumHeight(dp(76)); row.setBackground(BlueUi.ripple(this, android.graphics.Color.TRANSPARENT, 10));
        row.addView(new BlueUi.Glyph(this, icon, BlueUi.BLUE), new LinearLayout.LayoutParams(dp(21), dp(21)));
        LinearLayout labels = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(13), 0, dp(6), 0); row.addView(labels, lp);
        labels.addView(BlueUi.label(this, title, 16, BlueUi.INK, true)); BlueUi.gap(labels, 6); TextView detailView = BlueUi.label(this, detail, 12, BlueUi.MUTED, false); labels.addView(detailView);
        TextView stateView = BlueUi.label(this, state, 12, BlueUi.BLUE, true); row.addView(stateView);
        if (title.equals("应用拦截")) { interceptionDetail = detailView; interceptionState = stateView; }
        row.addView(new BlueUi.Glyph(this, "next", BlueUi.MUTED), new LinearLayout.LayoutParams(dp(17), dp(17))); row.setOnClickListener(v -> action.run()); section.addView(row);
    }

    private void refreshClock() {
        if (clock == null) return;
        ZonedDateTime now = ZonedDateTime.now(); Policy.Schedule s = store.schedule(); boolean night = Policy.night(now.toLocalTime(), s);
        if (night != clockNight) { render(); return; }
        long seconds = Math.max(0, Duration.between(now, Policy.nextBoundary(now, s)).getSeconds());
        phase.setText(night ? "距离 " + s.endText() + " 解除" : "距离今晚 " + s.startText() + " 开始");
        clock.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        bar.set(Policy.progress(now, s));
        refreshToday();
        if (interceptionState != null) { interceptionState.setText(GuardService.connected ? "已连接" : "待检查"); interceptionDetail.setText(interceptionText()); }
        badge.setText(GuardService.connected ? (night ? "守护中" : "等待夜间") : "尚未就绪");
    }

    // ---- schedule editor --------------------------------------------------------------------

    private void editSchedule() {
        Policy.Schedule current = store.desiredSchedule();
        TimePickerDialog start = new TimePickerDialog(this, (v, h, m) -> {
            int startMin = h * 60 + m;
            TimePickerDialog end = new TimePickerDialog(this, (v2, h2, m2) -> saveSchedule(startMin, h2 * 60 + m2), current.end / 60, current.end % 60, true);
            end.setTitle("几点解除限制？"); end.show();
        }, current.start / 60, current.start % 60, true);
        start.setTitle("几点开始限制？"); start.show();
    }

    private void saveSchedule(int start, int end) {
        int length = Math.floorMod(end - start, 1440);
        if (!Policy.Schedule.valid(start, end) || length < 60) { Toast.makeText(this, "限制时段至少要1小时", Toast.LENGTH_LONG).show(); return; }
        if (length > 16 * 60) { Toast.makeText(this, "限制时段不能超过16小时，白天也要正常用手机", Toast.LENGTH_LONG).show(); return; }
        Policy.Schedule next = new Policy.Schedule(start, end);
        boolean now = store.saveSchedule(next);
        Toast.makeText(this, now ? "已改为每天 " + next.text() : "夜里不能马上放宽规则，新时段将在今晚 " + store.schedule().endText() + " 后生效", Toast.LENGTH_LONG).show();
        render();
    }

    // ---- dialogs ----------------------------------------------------------------------------

    private void accessibilitySettings() {
        new AlertDialog.Builder(this).setTitle("开启应用拦截").setMessage("在系统无障碍设置中开启“息屏”。\n\n系统会提示它可以“查看屏幕内容”。息屏只读取屏幕上每个窗口属于哪个应用，用来发现分屏和小窗里的受限应用；不读取文字、网页、聊天或密码，也没有联网权限。")
                .setPositiveButton("前往设置", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("取消", null).show();
    }
    private void backgroundHelp() {
        new AlertDialog.Builder(this).setTitle("检查华为后台运行").setMessage("在手机设置 → 应用和服务 → 应用启动管理中找到息屏，关闭自动管理，并允许自启动、关联启动和后台活动。\n\n不同系统入口可能不同；本应用不能自动确认这些选项。")
                .setPositiveButton("打开应用设置", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))))
                .setNeutralButton("我已检查", (d, w) -> { store.p.edit().putBoolean("backgroundConfirmed", true).apply(); render(); }).setNegativeButton("取消", null).show();
    }
    private void unlockInfo() {
        new AlertDialog.Builder(this).setTitle("临时解锁规则").setMessage("在被拦截的页面点“确实需要用一下”。写下具体原因，冷静一段时间，再手动输入验证句，就能开放这一个应用5分钟。\n\n冷静期每晚第一次10分钟，之后每多解锁一次多等5分钟，最多30分钟。重启会取消授权和未完成的申请。")
                .setPositiveButton("知道了", null).show();
    }
    private void rules() {
        Policy.Schedule s = store.schedule();
        new AlertDialog.Builder(this).setTitle("规则与说明").setMessage("每天按手机本地时间 " + s.text() + " 执行，可以在首页修改。夜里放宽规则（减少应用、改时段）要等到 " + s.endText() + " 后生效，加强规则立即生效。\n\n识别出的浏览器会默认勾选，认错了可以在应用列表取消，或在右侧菜单改分类。电话、闹钟、桌面和系统设置始终可用。\n\n应用内网页需要限制它所在的应用。普通安装不能阻止卸载、强行停止、撤销权限或更改时间。\n\n所有数据只存在手机里，不上传。")
                .setPositiveButton("关闭", null).show();
    }
    private void history() {
        List<String> lines = store.history(); StringBuilder b = new StringBuilder();
        for (String line : lines) { String[] p = line.split("\t", 3); if (p.length < 3) continue; b.append(p[0]).append(" · ").append(Apps.label(this, p[1])).append("\n").append(p[2]).append("\n\n"); }
        new AlertDialog.Builder(this).setTitle("解锁记录").setMessage(b.length() == 0 ? "还没有临时解锁过，继续保持。" : b.toString().trim()).setPositiveButton("关闭", null).show();
    }
    private void notificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 41) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "通知未开启，可在系统应用通知设置中允许", Toast.LENGTH_LONG).show();
            render();
        }
    }

    // ---- temporary unlock -------------------------------------------------------------------

    private void renderUnlock() {
        card(); text("申请临时使用", 18);
        String name = Apps.label(this, target);
        if (!target.equals(store.p.getString("request", ""))) {
            long minutes = store.waitForNextRequest() / 60000; int used = store.unlocksTonight();
            text(name + " · 冷静 " + minutes + " 分钟后可开放5分钟", 14);
            if (used > 0) text("今晚已经解锁过 " + used + " 次，所以这次要多等一会儿。", 12);
            EditText reason = input("写下具体要做什么（至少10字）"); reasonInput = reason; reason.setText(reasonDraft);
            reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); reason.setMinLines(2);
            button("开始 " + minutes + " 分钟冷静期", true, () -> {
                String problem = Policy.reasonProblem(reason.getText().toString());
                if (problem != null) { reason.setError(problem); return; }
                store.begin(target, reason.getText().toString()); reasonDraft = ""; reasonInput = null; render();
            });
            button("算了，去睡觉", false, this::giveUp);
        } else {
            countdown = text("", 18); refreshCountdown();
            TextView why = BlueUi.label(this, "你写的原因：“" + store.p.getString("reason", "") + "”\n冷静期里再读一遍，还觉得必须现在做吗？", 13, BlueUi.MUTED, false);
            why.setPadding(dp(12), dp(10), dp(12), dp(10)); why.setBackground(BlueUi.background(this, BlueUi.PAGE, 0, 10)); section.addView(why); BlueUi.gap(section, 14);
            text("冷静期结束后，手动输入完整验证句：", 14);
            TextView challenge = text(store.p.getString("phrase", ""), 18); challenge.setTextIsSelectable(false);
            EditText phrase = input("完整输入上面的句子和数字"); phraseInput = phrase; phrase.setText(phraseDraft); phrase.setLongClickable(false);
            phrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            button("完成验证，临时开放", true, () -> {
                if (store.unlock(target, phrase.getText().toString())) {
                    String pkg = target; getIntent().removeExtra("target"); target = null; reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null; render();
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg); if (launch != null) startActivity(launch);
                } else Toast.makeText(this, store.requestRemainingMs() > 0 ? "冷静期还没结束" : "验证句不一致，请逐字输入", Toast.LENGTH_LONG).show();
            });
            button("取消申请，继续休息", false, this::giveUp);
        }
    }

    private void giveUp() {
        store.cancelRequest(); getIntent().removeExtra("target"); target = null; reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null;
        Toast.makeText(this, "好的，晚安", Toast.LENGTH_SHORT).show(); render();
    }

    private void refreshCountdown() {
        long remaining = store.requestRemainingMs();
        countdown.setText(remaining == 0 ? "冷静期已结束，请输入验证句" : "冷静期剩余 " + (remaining / 60000) + "分" + ((remaining / 1000) % 60) + "秒");
    }
}
