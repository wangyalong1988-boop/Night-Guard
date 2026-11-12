package cn.nightguard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Peace's home: four tabs with a bottom navigation bar.
 * 概览 today at a glance · 应用锁 daily time per app · 夜间 the night guard · 设置 status and info.
 */
public class MainActivity extends Activity {
    static final int TAB_TODAY = 0, TAB_LOCKS = 1, TAB_NIGHT = 2, TAB_SETTINGS = 3;
    private static final String[] TABS = {"概览", "应用锁", "夜间", "设置"};
    private static final String[] TAB_ICONS = {"ring", "hourglass", "moon", "gear"};
    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};

    private Store store; private int tab = TAB_TODAY, renderedTab = -1;
    private LinearLayout root; private ScrollView scroll;
    // Views refreshed every second while visible; null when the current tab has none.
    private TextView clock, phase, badge, todayUsed, todayGoal, todayDetail, focusTitle, focusDetail, requestDetail;
    private BlueUi.Item interception; private BlueUi.NightBar bar; private BlueUi.Ring ring; private Button focusButton;
    private boolean clockNight;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timer = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 1000); } };

    private int dp(float value) { return BlueUi.dp(this, value); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this);
        if (state != null) tab = state.getInt("tab", TAB_TODAY);
        route(getIntent());
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); route(intent); render(); }
    @Override public void onResume() { super.onResume(); render(); handler.removeCallbacks(timer); handler.post(timer); TodayWidget.update(this, store); }
    @Override public void onSaveInstanceState(Bundle state) { state.putInt("tab", tab); super.onSaveInstanceState(state); }
    @Override public void onPause() { handler.removeCallbacks(timer); super.onPause(); }
    @Override public void onDestroy() { loader.shutdownNow(); handler.removeCallbacks(timer); super.onDestroy(); }
    @Override public void onBackPressed() { if (tab != TAB_TODAY) select(TAB_TODAY); else super.onBackPressed(); }

    /** Older entry points sent unlock requests here; they now have their own page. */
    private void route(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra("tab")) { tab = intent.getIntExtra("tab", TAB_TODAY); intent.removeExtra("tab"); }
        String target = intent.getStringExtra("target");
        if (target != null) { intent.removeExtra("target"); startActivity(new Intent(this, UnlockActivity.class).putExtra("target", target)); }
    }

    private void select(int next) { if (next == tab) { if (scroll != null) scroll.smoothScrollTo(0, 0); return; } tab = next; render(); }

    // ---- frame --------------------------------------------------------------------------------

    private void render() {
        BlueUi.use(this); clockNight = store.night(); store.selected();
        clock = phase = badge = todayUsed = todayGoal = todayDetail = focusTitle = focusDetail = requestDetail = null;
        interception = null; bar = null; ring = null; focusButton = null;
        int keep = renderedTab == tab && scroll != null ? scroll.getScrollY() : 0; renderedTab = tab;

        LinearLayout outer = BlueUi.column(this);
        ScrollView view = new ScrollView(this); view.setFillViewport(true); view.setClipToPadding(false); scroll = view;
        outer.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = navigation(); outer.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        BlueUi.page(this, outer);
        outer.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(i.left, 0, i.right, 0); view.setPadding(0, i.top, 0, 0); nav.setPadding(0, 0, 0, i.bottom);
            return WindowInsets.CONSUMED;
        });
        root = BlueUi.column(this); root.setPadding(dp(20), dp(20), dp(20), dp(28)); view.addView(root);

        switch (tab) {
            case TAB_LOCKS: renderLocks(); break;
            case TAB_NIGHT: renderNight(); break;
            case TAB_SETTINGS: renderSettings(); break;
            default: renderToday();
        }
        if (keep > 0) view.post(() -> view.scrollTo(0, keep));
        refresh();
    }

    /** Bottom navigation: icon with a soft pill behind the selected one, and a label. */
    private LinearLayout navigation() {
        LinearLayout nav = BlueUi.column(this); nav.setBackgroundColor(BlueUi.CARD); BlueUi.divider(nav);
        LinearLayout items = BlueUi.row(this); items.setPadding(dp(6), dp(8), dp(6), dp(8)); nav.addView(items);
        for (int i = 0; i < TABS.length; i++) {
            int index = i; boolean on = i == tab;
            LinearLayout item = BlueUi.column(this); item.setGravity(Gravity.CENTER_HORIZONTAL); item.setPadding(0, dp(2), 0, dp(2));
            FrameLayout pill = new FrameLayout(this); if (on) pill.setBackground(BlueUi.background(this, BlueUi.PALE, 0, 16));
            pill.addView(new BlueUi.Glyph(this, TAB_ICONS[i], on ? BlueUi.BLUE : BlueUi.MUTED), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            item.addView(pill, new LinearLayout.LayoutParams(dp(58), dp(32))); BlueUi.gap(item, 4);
            item.addView(BlueUi.label(this, TABS[i], 11, on ? BlueUi.BLUE : BlueUi.MUTED, on));
            item.setContentDescription(TABS[i]); item.setSelected(on); item.setBackground(BlueUi.ripple(this, Color.TRANSPARENT, 16));
            item.setOnClickListener(v -> select(index));
            items.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return nav;
    }

    private TextView statusBadge() {
        boolean on = GuardService.connected;
        badge = BlueUi.pill(this, "", on ? BlueUi.GOOD : BlueUi.STAR, BlueUi.alpha(on ? BlueUi.GOOD : BlueUi.STAR, 0x24));
        return badge;
    }

    private boolean guardEnabled() { String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return enabled != null && enabled.contains(getPackageName() + "/"); }
    private boolean backgroundConfirmed() { return store.p.getBoolean("backgroundConfirmed", false); }
    private String interceptionText() { return GuardService.connected ? "已连接，分屏和小窗中同样有效" : guardEnabled() ? "权限已开启，等待服务连接" : "开启后才能拦截应用和计时"; }

    // ---- 概览 ----------------------------------------------------------------------------------

    private String greeting() {
        int h = LocalTime.now().getHour();
        if (clockNight) return "夜深了，手机交给我，你去睡吧";
        if (store.focusing()) return "专注中，别的事等会儿再说";
        if (h < 11) return "早上好，昨晚睡得怎么样？";
        if (h < 18) return "午后好，今天也别被手机牵着走";
        return "晚上好，记得早点准备休息";
    }

    private void renderToday() {
        root.addView(BlueUi.largeTitle(this, "Peace", greeting(), statusBadge()));
        renderRequest();
        renderSetup();

        BlueUi.sectionHeader(root, "今天", "用机记录", () -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout hero = BlueUi.group(root, true);
        if (store.recordingSince() == null) {
            hero.addView(BlueUi.empty(this, "phone", "还没有开始记录", "开启应用拦截后，Peace 会从那一刻开始记录每天的亮屏时间。"));
        } else {
            FrameLayout ringBox = new FrameLayout(this); ring = new BlueUi.Ring(this);
            ringBox.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout center = BlueUi.column(this); center.setGravity(Gravity.CENTER);
            center.addView(BlueUi.label(this, "已用", BlueUi.CAPTION, BlueUi.MUTED, false)); BlueUi.gap(center, 6);
            todayUsed = BlueUi.number(this, "", 26, BlueUi.INK); center.addView(todayUsed); BlueUi.gap(center, 6);
            todayGoal = BlueUi.label(this, "", BlueUi.CAPTION, BlueUi.MUTED, false); center.addView(todayGoal);
            ringBox.addView(center, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(176), dp(176)); rl.gravity = Gravity.CENTER_HORIZONTAL; rl.topMargin = dp(4);
            hero.addView(ringBox, rl); BlueUi.gap(hero, 16);
            todayDetail = BlueUi.label(this, "", BlueUi.FOOTNOTE, BlueUi.MUTED, false); todayDetail.setGravity(Gravity.CENTER); hero.addView(todayDetail, new LinearLayout.LayoutParams(-1, -2));
            BlueUi.gap(hero, 8);
            TextView goal = BlueUi.link(this, store.hasGoal() ? "调整每日目标" : "设定每日目标", this::chooseGoal);
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2); gl.gravity = Gravity.CENTER_HORIZONTAL; hero.addView(goal, gl);

            BlueUi.gap(root, 10);
            Store.Day t = store.today();
            BlueUi.tiles(root,
                    BlueUi.statTile(this, "拿起", t.pickups + " 次", "解锁屏幕算一次"),
                    BlueUi.statTile(this, clockNight ? "今晚拦下" : "今天拦下", store.blocksToday(clockNight) + " 次", "被 Peace 挡下的打开"),
                    BlueUi.statTile(this, "专注", store.focusMinutesToday() + " 分", "今天累计"));
        }

        renderTopApps();

        BlueUi.sectionHeader(root, "专注", null, null);
        LinearLayout focus = BlueUi.group(root, true); LinearLayout head = BlueUi.row(this);
        head.addView(BlueUi.iconTile(this, "target", BlueUi.BLUE), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout labels = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(14); head.addView(labels, lp);
        focusTitle = BlueUi.label(this, "", BlueUi.HEADLINE, BlueUi.INK, true); labels.addView(focusTitle); BlueUi.gap(labels, 5);
        focusDetail = BlueUi.label(this, "", BlueUi.CAPTION, BlueUi.MUTED, false); labels.addView(focusDetail);
        focus.addView(head); BlueUi.gap(focus, 14);
        focusButton = BlueUi.button(this, "开始专注", true, this::chooseFocus); focus.addView(focusButton, new LinearLayout.LayoutParams(-1, -2));

        Policy.Schedule s = store.schedule();
        BlueUi.sectionHeader(root, clockNight ? "今晚" : "夜间守护", null, null);
        LinearLayout night = BlueUi.group(root, false);
        BlueUi.Item it = BlueUi.item(night, BlueUi.iconTile(this, "moon", BlueUi.BLUE), 36, "每天 " + s.text(), "", "", () -> select(TAB_NIGHT));
        phase = it.subtitle; clock = it.value; clock.setFontFeatureSettings("tnum"); clock.setTextColor(BlueUi.INK);
    }

    private void renderRequest() {
        String pkg = store.p.getString("request", null);
        if (pkg == null) return;
        if (Apps.safe(this, pkg) || store.blockReason(pkg, store.selected()) == null) { store.cancelRequest(); return; }
        BlueUi.gap(root, 16);
        LinearLayout g = BlueUi.group(root, false, BlueUi.alpha(BlueUi.STAR, 0x26));
        BlueUi.Item it = BlueUi.item(g, BlueUi.iconTile(this, "lock", BlueUi.STAR), 36, "临时使用申请进行中", Apps.label(this, pkg), null,
                () -> startActivity(new Intent(this, UnlockActivity.class).putExtra("target", pkg)));
        requestDetail = it.subtitle;
    }

    /** A short checklist that disappears once everything needed is in place. */
    private void renderSetup() {
        boolean a = guardEnabled(), b = !store.desired().isEmpty() || !store.desiredLimits().isEmpty(), c = backgroundConfirmed();
        if (a && b && c) return;
        int done = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0);
        BlueUi.sectionHeader(root, "开始使用 · 还差 " + (3 - done) + " 步", null, null);
        LinearLayout g = BlueUi.group(root, false);
        setupStep(g, 1, a, "开启应用拦截", "在系统无障碍设置中打开“Peace”", this::accessibilitySettings); BlueUi.insetDivider(g, 56);
        setupStep(g, 2, b, "选择要管住的应用", "夜间限制清单，或给应用加一把应用锁", () -> select(TAB_LOCKS)); BlueUi.insetDivider(g, 56);
        setupStep(g, 3, c, "允许后台运行", "华为手机需要手动允许，否则可能失效", this::backgroundHelp);
    }

    private void setupStep(LinearLayout g, int n, boolean done, String title, String detail, Runnable action) {
        View mark;
        if (done) { FrameLayout f = new FrameLayout(this); f.setBackground(BlueUi.background(this, BlueUi.alpha(BlueUi.GOOD, 0x22), 0, 14)); f.addView(new BlueUi.Glyph(this, "check", BlueUi.GOOD), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)); mark = f; }
        else { TextView num = BlueUi.label(this, String.valueOf(n), BlueUi.FOOTNOTE, BlueUi.ON_BLUE, true); num.setGravity(Gravity.CENTER); num.setBackground(BlueUi.background(this, BlueUi.BLUE, 0, 14)); mark = num; }
        BlueUi.Item it = BlueUi.item(g, mark, 28, title, detail, null, done ? null : action);
        if (done) { it.title.setTextColor(BlueUi.MUTED); it.title.setPaintFlags(it.title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG); }
    }

    /** The three apps used most today, with their lock if any. Tapping one edits its lock. */
    private void renderTopApps() {
        Map<String, Long> apps = store.appTimes(LocalDate.now());
        List<Map.Entry<String, Long>> rows = new ArrayList<>(apps.entrySet()); rows.removeIf(e -> e.getValue() < 60000);
        rows.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        if (rows.isEmpty()) return;
        BlueUi.sectionHeader(root, "用得最多", "全部", () -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout g = BlueUi.group(root, false); long max = rows.get(0).getValue();
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            if (i > 0) BlueUi.insetDivider(g, 66);
            String pkg = rows.get(i).getKey(); Locks.Status s = Locks.of(store, pkg);
            appRow(g, pkg, s, s.today != null ? Policy.limitFraction(s.usedMs, s.today) : (double) rows.get(i).getValue() / max);
        }
    }

    /** An app with today's time, a progress line and its lock state. */
    private void appRow(LinearLayout g, String pkg, Locks.Status s, double fraction) {
        String name = Apps.label(this, pkg);
        LinearLayout r = BlueUi.row(this); r.setPadding(dp(16), dp(14), dp(16), dp(14)); r.setBackground(BlueUi.ripple(this, Color.TRANSPARENT, 0));
        r.addView(BlueUi.appIcon(this, AppCatalog.icon(this, pkg)), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout col = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(14); r.addView(col, lp);
        LinearLayout top = BlueUi.row(this);
        TextView title = BlueUi.label(this, name, BlueUi.BODY, BlueUi.INK, true); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        String state; int stateColor;
        if (s.today == null) { state = s.desired != null ? "明天起加锁" : Policy.duration(s.usedMs); stateColor = BlueUi.MUTED; }
        else if (s.locked()) { state = "已锁定"; stateColor = BlueUi.DANGER; }
        else { state = "剩 " + Policy.duration(s.leftMs()); stateColor = s.warning() ? BlueUi.STAR : BlueUi.MUTED; }
        if (s.locked()) top.addView(BlueUi.pill(this, state, BlueUi.DANGER, BlueUi.alpha(BlueUi.DANGER, 0x1f)));
        else top.addView(BlueUi.label(this, state, BlueUi.FOOTNOTE, stateColor, s.today != null));
        col.addView(top); BlueUi.gap(col, 9);
        BlueUi.Line line = new BlueUi.Line(this); line.set(fraction, s.today != null ? s.color() : BlueUi.BLUE); col.addView(line, new LinearLayout.LayoutParams(-1, dp(6))); BlueUi.gap(col, 8);
        String detail = s.today != null ? "已用 " + Policy.duration(s.usedMs) + " / 每天 " + Policy.duration(s.today * 60000L) : "今天 " + Policy.duration(s.usedMs) + " · 点击加锁";
        String pending = s.pending(); if (pending != null) detail += " · " + pending;
        col.addView(BlueUi.label(this, detail, BlueUi.CAPTION, pending != null ? BlueUi.BLUE : BlueUi.MUTED, false));
        r.setContentDescription(name + "，" + detail + (s.locked() ? "，已锁定" : ""));
        r.setOnClickListener(v -> Locks.edit(this, store, pkg, this::render));
        g.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- 应用锁 --------------------------------------------------------------------------------

    private void renderLocks() {
        root.addView(BlueUi.largeTitle(this, "应用锁", "每个应用每天能用多久，用满就锁到明天", null));
        List<Locks.Status> all = Locks.all(store);
        Runnable add = () -> startActivity(new Intent(this, LimitPickerActivity.class));
        if (all.isEmpty()) {
            BlueUi.gap(root, 20);
            LinearLayout g = BlueUi.group(root, true);
            g.addView(BlueUi.empty(this, "hourglass", "还没有应用锁", "给短视频、游戏这类一刷就停不下来的应用，定一个每天的时长。到点就锁，明天自动解锁。"));
            g.addView(BlueUi.button(this, "添加应用锁", true, add), new LinearLayout.LayoutParams(-1, -2));
        } else {
            int locked = 0, soon = 0; for (Locks.Status s : all) { if (s.locked()) locked++; else if (s.warning()) soon++; }
            BlueUi.gap(root, 18);
            BlueUi.tiles(root,
                    BlueUi.statTile(this, "已加锁", all.size() + " 个", "应用"),
                    BlueUi.statTile(this, "已锁定", locked + " 个", "00:00 自动解锁"),
                    BlueUi.statTile(this, "快到时间", soon + " 个", "剩不到 5 分钟"));
            BlueUi.sectionHeader(root, "今天", "＋ 添加", add);
            LinearLayout g = BlueUi.group(root, false);
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) BlueUi.insetDivider(g, 68);
                Locks.Status s = all.get(i); appRow(g, s.pkg, s, s.today != null ? Policy.limitFraction(s.usedMs, s.today) : 0);
            }
            BlueUi.gap(root, 12);
            root.addView(BlueUi.button(this, "添加应用锁", false, add), new LinearLayout.LayoutParams(-1, -2));
        }
        if (!GuardService.connected) {
            BlueUi.gap(root, 12);
            LinearLayout warn = BlueUi.group(root, false, BlueUi.alpha(BlueUi.STAR, 0x26));
            BlueUi.item(warn, BlueUi.iconTile(this, "info", BlueUi.STAR), 36, "应用拦截还没开启", "开启后才会计时和锁定", null, this::accessibilitySettings);
        }

        BlueUi.sectionHeader(root, "规则", null, null);
        LinearLayout rules = BlueUi.group(root, false);
        BlueUi.item(rules, BlueUi.iconTile(this, "lock", BlueUi.BLUE), 34, "用满就锁", "白天夜里都算，锁到明天 00:00 自动解除", null, null); BlueUi.insetDivider(rules, 64);
        BlueUi.item(rules, BlueUi.iconTile(this, "bell", BlueUi.BLUE), 34, "提前提醒", "只剩 5 分钟时发一条通知，让你有时间收尾", null, null); BlueUi.insetDivider(rules, 64);
        BlueUi.item(rules, BlueUi.iconTile(this, "clock", BlueUi.BLUE), 34, "确需使用", "写明原因、冷静 10 分钟起，可以再用 5 分钟", null, null); BlueUi.insetDivider(rules, 64);
        BlueUi.item(rules, BlueUi.iconTile(this, "edit", BlueUi.BLUE), 34, "防止反悔", "调短立即生效；调长或取消要到明天", null, null);
        BlueUi.footnote(root, "计时只算解锁屏幕后应用在前台的时间，桌面和系统界面不算。数据只存在这台手机里。");
    }

    // ---- 夜间 ----------------------------------------------------------------------------------

    private void renderNight() {
        Policy.Schedule schedule = store.schedule();
        root.addView(BlueUi.largeTitle(this, "夜间守护", "到点暂停限制清单里的应用，分屏和小窗也有效", statusBadge()));
        BlueUi.gap(root, 18);
        LinearLayout hero = BlueUi.group(root, true, BlueUi.HERO);
        LinearLayout head = BlueUi.row(this); head.addView(BlueUi.label(this, "每日限制时段", BlueUi.FOOTNOTE, BlueUi.BLUE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = BlueUi.link(this, "修改", this::editSchedule); edit.setContentDescription("修改限制时段"); edit.setMinHeight(dp(32)); head.addView(edit);
        hero.addView(head); BlueUi.gap(hero, 8);
        TextView range = BlueUi.number(this, schedule.startText() + " – " + schedule.endText(), 34, BlueUi.INK); range.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)); hero.addView(range); BlueUi.gap(hero, 8);
        int hours = schedule.minutes() / 60, minutes = schedule.minutes() % 60;
        hero.addView(BlueUi.label(this, "每晚 " + hours + " 小时" + (minutes > 0 ? minutes + " 分钟" : "") + (schedule.start > schedule.end ? " · 跨越午夜" : ""), BlueUi.CAPTION, BlueUi.MUTED, false));
        BlueUi.gap(hero, 18);
        bar = new BlueUi.NightBar(this); hero.addView(bar, new LinearLayout.LayoutParams(-1, dp(16))); BlueUi.gap(hero, 12);
        LinearLayout counter = BlueUi.row(this); phase = BlueUi.label(this, "", BlueUi.CAPTION, BlueUi.MUTED, false); counter.addView(phase, new LinearLayout.LayoutParams(0, -2, 1));
        clock = BlueUi.number(this, "", 18, BlueUi.INK); counter.addView(clock); hero.addView(counter);
        Policy.Schedule desired = store.desiredSchedule();
        if (!desired.equals(schedule)) { BlueUi.gap(hero, 12); hero.addView(BlueUi.label(this, "已改为 " + desired.text() + "，今晚 " + schedule.endText() + " 后生效", BlueUi.CAPTION, BlueUi.BLUE, false)); }

        renderRest(schedule);
        renderNights();

        BlueUi.sectionHeader(root, "限制清单", "管理", () -> startActivity(new Intent(this, AppPickerActivity.class)));
        LinearLayout list = BlueUi.group(root, true);
        TextView summary = BlueUi.label(this, "正在读取已选应用…", BlueUi.FOOTNOTE, BlueUi.MUTED, false); list.addView(summary); BlueUi.gap(list, 14);
        LinearLayout preview = BlueUi.row(this); list.addView(preview);
        if (store.p.contains("pending")) { BlueUi.gap(list, 12); list.addView(BlueUi.label(this, "名单调整已保存，减少的限制将在 " + schedule.endText() + " 后生效。", BlueUi.CAPTION, BlueUi.BLUE, false)); }
        list.setOnClickListener(v -> startActivity(new Intent(this, AppPickerActivity.class))); list.setBackground(BlueUi.ripple(this, BlueUi.CARD, 18));
        loader.execute(() -> { try { List<AppCatalog.Entry> entries = AppCatalog.load(this); runOnUiThread(() -> { if (!isDestroyed()) showPreview(entries, summary, preview); }); }
            catch (RuntimeException e) { runOnUiThread(() -> { if (!isDestroyed()) summary.setText("点击管理，检查限制清单"); }); } });

        BlueUi.sectionHeader(root, "临时解锁", null, null);
        LinearLayout unlock = BlueUi.group(root, false);
        BlueUi.item(unlock, BlueUi.iconTile(this, "lock", BlueUi.BLUE), 34, "解锁规则", "写明原因 · 冷静 10 分钟起 · 限时 5 分钟", null, this::unlockInfo); BlueUi.insetDivider(unlock, 64);
        BlueUi.item(unlock, BlueUi.iconTile(this, "list", BlueUi.BLUE), 34, "解锁记录", store.history().isEmpty() ? "还没有临时解锁过" : "最近 " + store.history().size() + " 次", null, this::history);
    }

    /** How long the phone was put down during the last night (or tonight so far), with a timeline. */
    private void renderRest(Policy.Schedule schedule) {
        LocalDate key = clockNight ? Policy.nightKey(ZonedDateTime.now(), schedule) : store.recentNights(1).get(0).date;
        Store.NightUse use = store.rest(key);
        if (use == null) return;
        Policy.Rest r = use.rest;
        BlueUi.sectionHeader(root, clockNight ? "今晚到现在" : "昨晚", null, null);
        LinearLayout g = BlueUi.group(root, true);
        LinearLayout big = BlueUi.row(this); big.setGravity(Gravity.BOTTOM);
        big.addView(BlueUi.label(this, "放下手机 ", BlueUi.FOOTNOTE, BlueUi.MUTED, false));
        big.addView(BlueUi.number(this, Policy.duration(r.restMs()), 26, BlueUi.INK));
        g.addView(big); BlueUi.gap(g, 8);
        String detail = r.pickups == 0 ? (use.ongoing() ? "到现在还没有拿起过手机" : "整晚都没有拿起手机")
                : "拿起 " + r.pickups + " 次 · 共用了 " + Policy.duration(r.usedMs) + " · 最长连续放下 " + Policy.duration(r.longestRestMs);
        g.addView(BlueUi.label(this, detail, BlueUi.CAPTION, BlueUi.MUTED, false)); BlueUi.gap(g, 16);
        BlueUi.UseTimeline line = new BlueUi.UseTimeline(this); line.set(use); line.setContentDescription(detail);
        g.addView(line, new LinearLayout.LayoutParams(-1, dp(14))); BlueUi.gap(g, 8);
        LinearLayout ends = BlueUi.row(this);
        ends.addView(BlueUi.label(this, schedule.startText(), 11, BlueUi.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        ends.addView(BlueUi.label(this, "■ 使用", 11, BlueUi.STAR, false)); ends.addView(BlueUi.label(this, "   " + schedule.endText(), 11, BlueUi.MUTED, false));
        g.addView(ends);
        if (use.from > use.start) { BlueUi.gap(g, 8); g.addView(BlueUi.label(this, "灰色部分是开始记录之前的时间", 11, BlueUi.MUTED, false)); }
    }

    /** Last seven nights as stars: full = no unlock, half = unlocked, dot = not guarded. */
    private void renderNights() {
        List<Store.Night> nights = store.recentNights(7);
        boolean any = false; for (Store.Night n : nights) any |= n.recorded;
        if (!any && !clockNight) return;
        int streak = store.calmStreak();
        BlueUi.sectionHeader(root, "最近 7 晚", null, null);
        LinearLayout g = BlueUi.group(root, true);
        String sub = streak >= 2 ? "已连续 " + streak + " 晚一次都没解锁" : clockNight ? "今晚已拦下 " + store.blocksTonight() + " 次" : "一次都不解锁，就能点亮一颗星";
        g.addView(BlueUi.label(this, sub, BlueUi.FOOTNOTE, BlueUi.INK, true)); BlueUi.gap(g, 16);
        LinearLayout row = BlueUi.row(this); row.setWeightSum(7);
        for (Store.Night n : nights) {
            LinearLayout col = BlueUi.column(this); col.setGravity(Gravity.CENTER_HORIZONTAL);
            String kind = !n.recorded || !n.guarded ? "dot" : n.unlocks == 0 ? "star-fill" : "star-half";
            col.addView(new BlueUi.Glyph(this, kind, kind.equals("dot") ? BlueUi.SUBTLE : BlueUi.STAR), new LinearLayout.LayoutParams(dp(26), dp(26))); BlueUi.gap(col, 6);
            col.addView(BlueUi.label(this, WEEK[n.date.getDayOfWeek().getValue() - 1], 11, BlueUi.MUTED, false));
            col.setContentDescription(n.date.getMonthValue() + "月" + n.date.getDayOfMonth() + "日，" + (!n.recorded || !n.guarded ? "未守护" : n.unlocks == 0 ? "安稳，拦下" + n.blocks + "次" : "解锁" + n.unlocks + "次"));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        }
        g.addView(row);
    }

    private void showPreview(List<AppCatalog.Entry> entries, TextView summary, LinearLayout preview) {
        Set<String> selected = store.selected(); List<AppCatalog.Entry> chosen = new ArrayList<>(); int browserCount = 0;
        for (AppCatalog.Entry e : entries) if (selected.contains(e.pkg)) { chosen.add(e); if (e.isBrowser()) browserCount++; }
        summary.setText(chosen.isEmpty() ? "还没有选择夜里要暂停的应用" : chosen.size() + " 个应用在夜间暂停" + (browserCount > 0 ? " · 含 " + browserCount + " 个浏览器" : ""));
        preview.removeAllViews();
        if (chosen.isEmpty()) { preview.addView(BlueUi.link(this, "添加娱乐、社交或游戏应用  →", () -> startActivity(new Intent(this, AppPickerActivity.class)))); return; }
        int slots = Math.max(1, Math.min(6, (getResources().getConfiguration().screenWidthDp - 120) / 48));
        for (int i = 0; i < Math.min(chosen.size(), slots); i++) {
            AppCatalog.Entry e = chosen.get(i); ImageView icon = BlueUi.appIcon(this, e.icon != null ? e.icon : AppCatalog.icon(this, e.pkg)); icon.setContentDescription(e.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36)); lp.rightMargin = dp(12); preview.addView(icon, lp);
        }
        if (chosen.size() > slots) {
            TextView more = BlueUi.label(this, "+" + (chosen.size() - slots), BlueUi.FOOTNOTE, BlueUi.MUTED, true); more.setGravity(Gravity.CENTER);
            more.setBackground(BlueUi.background(this, BlueUi.PAGE, 0, 18)); preview.addView(more, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
    }

    // ---- 设置 ----------------------------------------------------------------------------------

    private void renderSettings() {
        root.addView(BlueUi.largeTitle(this, "设置", "Peace 完全离线，数据只保存在这台手机上", null));
        BlueUi.sectionHeader(root, "守护状态", null, null);
        LinearLayout g = BlueUi.group(root, false);
        interception = BlueUi.item(g, BlueUi.iconTile(this, "shield", BlueUi.BLUE), 34, "应用拦截", interceptionText(), "", this::accessibilitySettings); BlueUi.insetDivider(g, 64);
        boolean notes = GuardNotification.permitted(this);
        state(BlueUi.item(g, BlueUi.iconTile(this, "bell", BlueUi.BLUE), 34, "通知与提醒", "睡前提醒 · 应用锁到点提醒 · 晨间小结", notes ? "已允许" : "未开启", this::notificationSettings), notes); BlueUi.insetDivider(g, 64);
        state(BlueUi.item(g, BlueUi.iconTile(this, "phone", BlueUi.BLUE), 34, "后台运行", backgroundConfirmed() ? "已由你确认，系统仍可能限制后台" : "允许自启动、关联启动及后台活动", backgroundConfirmed() ? "已确认" : "去设置", this::backgroundHelp), backgroundConfirmed());

        BlueUi.sectionHeader(root, "记录", null, null);
        LinearLayout r = BlueUi.group(root, false);
        BlueUi.item(r, BlueUi.iconTile(this, "chart", BlueUi.BLUE), 34, "用机记录", "每天亮屏时间、按应用、夜里放下手机", null, () -> startActivity(new Intent(this, HistoryActivity.class))); BlueUi.insetDivider(r, 64);
        BlueUi.item(r, BlueUi.iconTile(this, "list", BlueUi.BLUE), 34, "解锁记录", store.history().isEmpty() ? "还没有临时解锁过" : "最近 " + store.history().size() + " 次，含时间、应用和原因", null, this::history);

        BlueUi.sectionHeader(root, "关于", null, null);
        LinearLayout a = BlueUi.group(root, false);
        BlueUi.item(a, BlueUi.iconTile(this, "info", BlueUi.BLUE), 34, "规则与说明", "时段、名单、浏览器识别与能力边界", null, this::rules); BlueUi.insetDivider(a, 64);
        BlueUi.item(a, BlueUi.iconTile(this, "leaf", BlueUi.GOOD), 34, "关于 Peace", "版本 " + version() + " · 无账号 · 无广告 · 不联网", null, this::about);
    }

    private void state(BlueUi.Item it, boolean ok) { if (it.value != null) { it.value.setTextColor(ok ? BlueUi.GOOD : BlueUi.STAR); it.value.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)); } }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    // ---- live values --------------------------------------------------------------------------

    private void refresh() {
        ZonedDateTime now = ZonedDateTime.now(); Policy.Schedule s = store.schedule(); boolean night = Policy.night(now.toLocalTime(), s);
        if (night != clockNight) { render(); return; }
        if (badge != null) badge.setText(GuardService.connected ? (night ? "守护中" : store.focusing() ? "专注中" : "运行中") : "尚未就绪");
        if (clock != null) {
            long seconds = Math.max(0, Duration.between(now, Policy.nextBoundary(now, s)).getSeconds());
            phase.setText(night ? "距离 " + s.endText() + " 解除" : "距离今晚 " + s.startText() + " 开始");
            clock.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        }
        if (bar != null) bar.set(Policy.progress(now, s));
        if (interception != null) { interception.subtitle.setText(interceptionText()); interception.value.setText(GuardService.connected ? "已连接" : "待开启"); state(interception, GuardService.connected); }
        if (requestDetail != null) {
            long left = store.requestRemainingMs(); String pkg = store.p.getString("request", "");
            requestDetail.setText(Apps.label(this, pkg) + (left > 0 ? " · 冷静期还剩 " + (left / 60000 + 1) + " 分钟" : " · 可以输入验证句了"));
        }
        if (focusTitle != null) {
            boolean on = store.focusing();
            focusTitle.setText(on ? "专注中 · 还剩 " + Policy.duration(store.focusRemainingMs()) : "专注一下");
            focusDetail.setText(on ? "到点自动解除，不能提前结束" : "限制清单里的 " + store.desired().size() + " 个应用会暂停，到点自动解除");
            focusButton.setText(on ? "再延长一段" : "开始专注");
        }
        if (todayUsed == null) return;
        Store.Day t = store.today(); int goal = store.goalMinutes(); long goalMs = goal * 60000L;
        todayUsed.setText(Policy.duration(t.useMs)); ring.set(t.useMs, goal > 0 ? goalMs : 0);
        todayGoal.setText(goal > 0 ? "目标 " + Policy.duration(goalMs) : "未设目标");
        if (goal <= 0) { todayDetail.setText("设个目标，每周少用一点点"); return; }
        String status = t.useMs < goalMs ? "离目标还剩 " + Policy.duration(goalMs - t.useMs) : "已超出目标 " + Policy.duration(t.useMs - goalMs);
        String pace = goal > store.goalTarget() ? "\n每周自动减少，最终目标 " + Policy.duration(store.goalTarget() * 60000L) : "";
        todayDetail.setText(status + pace);
        todayDetail.setTextColor(t.useMs < goalMs ? BlueUi.MUTED : BlueUi.STAR);
    }

    // ---- choices ------------------------------------------------------------------------------

    /** Focus: the restricted list is stopped for a while, by choice; it cannot be ended early. */
    private void chooseFocus() {
        if (store.desired().isEmpty()) { Toast.makeText(this, "先在夜间限制清单里选几个容易分心的应用", Toast.LENGTH_LONG).show(); startActivity(new Intent(this, AppPickerActivity.class)); return; }
        boolean on = store.focusing();
        BlueUi.Sheet sheet = new BlueUi.Sheet(this, on ? "再专注多久？" : "专注多久？",
                "这段时间里，限制清单中的 " + store.desired().size() + " 个应用都会被拦下，时间到自动解除，不能提前结束。电话、闹钟等始终可用。");
        for (int m : new int[]{25, 45, 60, 90}) sheet.option(Policy.duration(m * 60000L), m == 25 ? "番茄钟，适合一件小事" : m == 45 ? "一节课的长度" : null, false, () -> {
            store.startFocus(m); Toast.makeText(this, "开始专注，加油", Toast.LENGTH_SHORT).show(); render(); });
        sheet.button("再想想", false, null); sheet.show();
    }

    private void chooseGoal() {
        int[] targets = {60, 90, 120, 150, 180, 240, 300};
        double avg = store.recentAverageMs(7); int current = store.goalTarget();
        BlueUi.Sheet sheet = new BlueUi.Sheet(this, "每日用机目标", Double.isNaN(avg) ? "最终想把每天的亮屏时间控制在多少？" : "最近日均 " + Policy.duration((long) avg) + "，最终想控制在多少？");
        for (int target : targets) sheet.option("每天 " + Policy.duration(target * 60000L), null, target == current, () -> choosePace(target));
        if (store.hasGoal()) sheet.button("不设目标", false, () -> { store.clearGoal(); render(); });
        sheet.show();
    }

    private void choosePace(int target) {
        BlueUi.Sheet sheet = new BlueUi.Sheet(this, "怎么减少？", "从最近 7 天的日均开始，每满一周降一档，直到每天 " + Policy.duration(target * 60000L) + "。");
        String[] names = {"温和", "积极", "直接"}; String[] details = {"每周少 10 分钟（推荐）", "每周少 20 分钟", "从今天起就按目标"}; int[] steps = {10, 20, 0};
        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            sheet.option(names[i], details[i], false, () -> {
                int base = store.setGoal(target, step);
                Toast.makeText(this, base > target ? "从每天 " + Policy.duration(base * 60000L) + " 开始，每周减少 " + step + " 分钟" : "目标：每天 " + Policy.duration(target * 60000L), Toast.LENGTH_LONG).show();
                render();
            });
        }
        sheet.show();
    }

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

    // ---- sheets -------------------------------------------------------------------------------

    private void accessibilitySettings() {
        new BlueUi.Sheet(this, "开启应用拦截", "在系统无障碍设置中开启“Peace”。\n\n系统会提示它可以“查看屏幕内容”。Peace 只读取屏幕上每个窗口属于哪个应用，用来计时、发现分屏和小窗里的受限应用；不读取文字、网页、聊天或密码，也没有联网权限。")
                .button("前往系统设置", true, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).button("以后再说", false, null).show();
    }
    private void backgroundHelp() {
        new BlueUi.Sheet(this, "允许后台运行", "在手机设置 → 应用和服务 → 应用启动管理中找到 Peace，关闭“自动管理”，并允许自启动、关联启动和后台活动。\n\n不同系统入口可能不同；Peace 无法自动确认这些选项。")
                .button("打开应用设置", true, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))))
                .button("我已检查", false, () -> { store.p.edit().putBoolean("backgroundConfirmed", true).apply(); render(); }).show();
    }
    private void unlockInfo() {
        new BlueUi.Sheet(this, "临时解锁规则", "在拦截页点“确实需要用一下”或“再用 5 分钟”。写下具体原因，冷静一段时间，再手动输入验证句，就能开放这一个应用 5 分钟。\n\n夜里的冷静期第一次 10 分钟，之后每多解锁一次多等 5 分钟，最多 30 分钟。白天的应用锁和专注按天单独计数。重启会取消授权和未完成的申请。")
                .button("知道了", true, null).show();
    }
    private void rules() {
        Policy.Schedule s = store.schedule();
        new BlueUi.Sheet(this, "规则与说明", "夜间守护每天按手机本地时间 " + s.text() + " 执行，可以在“夜间”页修改。夜里放宽规则（减少应用、改时段）要等到 " + s.endText() + " 后生效，加强规则立即生效。\n\n应用锁按每个应用当天在前台的时间计算，用满后锁到 00:00。调短立即生效，调长或取消明天生效。\n\n识别出的浏览器会默认勾选，认错了可以在应用列表取消，或在右侧菜单改分类。电话、闹钟、桌面和系统设置始终可用。\n\n应用内网页需要限制它所在的应用。普通安装不能阻止卸载、强行停止、撤销权限或更改时间。")
                .button("关闭", true, null).show();
    }
    private void about() {
        new BlueUi.Sheet(this, "Peace", "把时间还给自己。\n\n夜间守护、应用锁、专注和用机记录都在手机本地完成：没有账号、没有广告、没有网络权限，所有记录只存在这台手机的应用私有存储里。\n\n版本 " + version())
                .button("好的", true, null).show();
    }
    private void history() {
        List<String> lines = store.history();
        BlueUi.Sheet sheet = new BlueUi.Sheet(this, "解锁记录", lines.isEmpty() ? "还没有临时解锁过，继续保持。" : "最近 " + lines.size() + " 次临时使用，最新的在最上面。");
        for (String line : lines) {
            String[] p = line.split("\t", 3); if (p.length < 3) continue;
            LinearLayout r = BlueUi.column(this); r.setPadding(dp(4), dp(12), dp(4), dp(12));
            r.addView(BlueUi.label(this, Apps.label(this, p[1]) + "  ·  " + p[0], BlueUi.FOOTNOTE, BlueUi.INK, true)); BlueUi.gap(r, 6);
            r.addView(BlueUi.label(this, p[2], BlueUi.CAPTION, BlueUi.MUTED, false));
            sheet.view(r);
        }
        sheet.button("关闭", true, null); sheet.show();
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
}
