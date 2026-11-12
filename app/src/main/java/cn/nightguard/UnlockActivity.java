package cn.nightguard;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/**
 * Asking for a few minutes with a stopped app, in three visible steps: write why, wait, type
 * the phrase. The wait survives leaving the page; a reboot cancels it (see {@link Store}).
 */
public class UnlockActivity extends Activity {
    private Store store; private String target, reasonDraft = "", phraseDraft = "";
    private EditText reasonInput, phraseInput; private TextView countdown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timer = new Runnable() { public void run() { refreshCountdown(); handler.postDelayed(this, 1000); } };
    private int dp(float v) { return BlueUi.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this);
        if (state != null) { reasonDraft = state.getString("reasonDraft", ""); phraseDraft = state.getString("phraseDraft", ""); }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null; }
    @Override protected void onResume() { super.onResume(); render(); handler.removeCallbacks(timer); handler.post(timer); }
    @Override protected void onPause() { capture(); handler.removeCallbacks(timer); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { capture(); out.putString("reasonDraft", reasonDraft); out.putString("phraseDraft", phraseDraft); super.onSaveInstanceState(out); }

    private void capture() { if (reasonInput != null) reasonDraft = reasonInput.getText().toString(); if (phraseInput != null) phraseDraft = phraseInput.getText().toString(); }

    private String why() { return target == null ? null : store.blockReason(target, store.selected()); }

    private void render() {
        BlueUi.use(this); capture(); reasonInput = null; phraseInput = null; countdown = null;
        target = getIntent().getStringExtra("target"); if (target == null) target = store.p.getString("request", null);
        // A request only makes sense while the app is actually stopped (night, focus or its lock).
        if (target == null || Apps.safe(this, target) || why() == null) {
            store.cancelRequest(); Toast.makeText(this, "这个应用现在可以正常使用", Toast.LENGTH_SHORT).show(); finish(); return;
        }
        String why = why(); boolean night = "night".equals(why);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); BlueUi.page(this, scroll);
        LinearLayout page = BlueUi.column(this); scroll.addView(page);
        page.addView(BlueUi.navBar(this, "申请临时使用", null));
        LinearLayout root = BlueUi.column(this); root.setPadding(dp(20), dp(8), dp(20), dp(28)); page.addView(root);

        // Which app and why it is stopped
        LinearLayout app = BlueUi.group(root, true); LinearLayout head = BlueUi.row(this);
        head.addView(BlueUi.appIcon(this, AppCatalog.icon(this, target)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(14); head.addView(labels, lp);
        labels.addView(BlueUi.label(this, Apps.label(this, target), BlueUi.HEADLINE + 1, BlueUi.INK, true)); BlueUi.gap(labels, 6);
        labels.addView(BlueUi.label(this, night ? "夜间守护中" : "focus".equals(why) ? "专注中" : "今天的应用锁时间已用完", BlueUi.CAPTION, BlueUi.MUTED, false));
        app.addView(head);

        boolean waiting = target.equals(store.p.getString("request", ""));
        long minutes = waiting ? store.requestWait() / 60000 : store.waitForNextRequest() / 60000;
        BlueUi.sectionHeader(root, "三步开放 5 分钟", null, null);
        LinearLayout steps = BlueUi.group(root, true);
        boolean cooled = waiting && store.requestRemainingMs() == 0;
        step(steps, 1, "写下具体原因", waiting ? "已完成" : "至少 10 个字，不能是重复的字", waiting);
        step(steps, 2, "冷静 " + minutes + " 分钟", cooled ? "已完成" : "可以离开这个页面，计时不会中断", cooled); BlueUi.gap(steps, 4);
        step(steps, 3, "输入验证句", "逐字输入，不能复制粘贴", false);

        LinearLayout card = BlueUi.group(root, true);
        if (!waiting) {
            int used = night ? store.unlocksTonight() : 0;
            if (used > 0) { card.addView(BlueUi.label(this, "今晚已经解锁过 " + used + " 次，所以这次要多等一会儿。", BlueUi.FOOTNOTE, BlueUi.STAR, true)); BlueUi.gap(card, 12); }
            card.addView(BlueUi.label(this, "你现在要用它做什么？", BlueUi.HEADLINE, BlueUi.INK, true)); BlueUi.gap(card, 12);
            EditText reason = input(card, "例如：回复老师关于明天作业的消息"); reasonInput = reason; reason.setText(reasonDraft);
            reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); reason.setMinLines(3); reason.setGravity(Gravity.TOP);
            BlueUi.gap(card, 6);
            card.addView(BlueUi.button(this, "开始 " + minutes + " 分钟冷静期", true, () -> {
                String problem = Policy.reasonProblem(reason.getText().toString());
                if (problem != null) { reason.setError(problem); return; }
                store.begin(target, reason.getText().toString()); reasonDraft = ""; reasonInput = null; render();
            }), new LinearLayout.LayoutParams(-1, -2));
        } else {
            countdown = BlueUi.number(this, "", 34, BlueUi.INK); countdown.setGravity(Gravity.CENTER); card.addView(countdown, new LinearLayout.LayoutParams(-1, -2)); BlueUi.gap(card, 6);
            TextView caption = BlueUi.label(this, "冷静期剩余", BlueUi.CAPTION, BlueUi.MUTED, false); caption.setGravity(Gravity.CENTER); caption.setTag("caption"); card.addView(caption, new LinearLayout.LayoutParams(-1, -2));
            BlueUi.gap(card, 16);
            TextView reason = BlueUi.label(this, "“" + store.p.getString("reason", "") + "”\n再读一遍：还觉得必须现在做吗？", BlueUi.FOOTNOTE, BlueUi.MUTED, false);
            reason.setPadding(dp(14), dp(12), dp(14), dp(12)); reason.setBackground(BlueUi.background(this, BlueUi.PAGE, 0, 12)); card.addView(reason, new LinearLayout.LayoutParams(-1, -2));
            BlueUi.gap(card, 18);
            card.addView(BlueUi.label(this, "冷静期结束后，逐字输入：", BlueUi.FOOTNOTE, BlueUi.MUTED, false)); BlueUi.gap(card, 10);
            TextView challenge = BlueUi.label(this, store.p.getString("phrase", ""), 17, BlueUi.INK, true); challenge.setTextIsSelectable(false); card.addView(challenge); BlueUi.gap(card, 12);
            EditText phrase = input(card, "完整输入上面的句子和数字"); phraseInput = phrase; phrase.setText(phraseDraft); phrase.setLongClickable(false);
            phrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); BlueUi.gap(card, 6);
            card.addView(BlueUi.button(this, "完成验证，开放 5 分钟", true, () -> {
                if (store.unlock(target, phrase.getText().toString())) {
                    String pkg = target; reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null;
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg); finish(); if (launch != null) startActivity(launch);
                } else Toast.makeText(this, store.requestRemainingMs() > 0 ? "冷静期还没结束" : "验证句不一致，请逐字输入", Toast.LENGTH_LONG).show();
            }), new LinearLayout.LayoutParams(-1, -2));
        }
        BlueUi.gap(root, 12);
        root.addView(BlueUi.button(this, waiting ? "取消申请" : night ? "算了，去睡觉" : "算了，不用了", false, () -> giveUp(night)), new LinearLayout.LayoutParams(-1, -2));
        BlueUi.footnote(root, "开放期间只有这一个应用可用，5 分钟后重新拦下。重启手机会取消授权和未完成的申请。");
        refreshCountdown();
    }

    private void step(LinearLayout parent, int n, String title, String detail, boolean done) {
        LinearLayout r = BlueUi.row(this); r.setPadding(0, dp(6), 0, dp(6));
        View mark;
        if (done) { FrameLayout f = new FrameLayout(this); f.setBackground(BlueUi.background(this, BlueUi.alpha(BlueUi.GOOD, 0x22), 0, 13)); f.addView(new BlueUi.Glyph(this, "check", BlueUi.GOOD), new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER)); mark = f; }
        else { TextView num = BlueUi.label(this, String.valueOf(n), BlueUi.FOOTNOTE, BlueUi.BLUE, true); num.setGravity(Gravity.CENTER); num.setBackground(BlueUi.background(this, BlueUi.PALE, 0, 13)); mark = num; }
        r.addView(mark, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout labels = BlueUi.column(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(12); r.addView(labels, lp);
        labels.addView(BlueUi.label(this, title, BlueUi.BODY, done ? BlueUi.MUTED : BlueUi.INK, true)); BlueUi.gap(labels, 4);
        labels.addView(BlueUi.label(this, detail, BlueUi.CAPTION, BlueUi.MUTED, false));
        parent.addView(r);
    }

    private EditText input(LinearLayout parent, String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setTextColor(BlueUi.INK); e.setHintTextColor(BlueUi.SUBTLE);
        e.setPadding(dp(14), dp(14), dp(14), dp(14)); e.setBackground(BlueUi.background(this, BlueUi.PAGE, BlueUi.LINE, 12)); parent.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }

    private void giveUp(boolean night) {
        store.cancelRequest(); reasonDraft = ""; phraseDraft = ""; reasonInput = null; phraseInput = null;
        Toast.makeText(this, night ? "好的，晚安" : "做得好，忍住了", Toast.LENGTH_SHORT).show(); finish();
    }

    private void refreshCountdown() {
        if (countdown == null) return;
        long remaining = store.requestRemainingMs();
        countdown.setText(remaining == 0 ? "可以验证了" : String.format(java.util.Locale.ROOT, "%02d:%02d", remaining / 60000, (remaining / 1000) % 60));
        if (remaining == 0 && countdown.getTag() == null) { countdown.setTag("done"); countdown.setTextColor(BlueUi.GOOD); }
    }
}
