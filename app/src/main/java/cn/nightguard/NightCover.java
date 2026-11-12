package cn.nightguard;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.Random;

/**
 * The screen shown over an app that should rest. It is always dark: at night a bright page would
 * be the last thing someone trying to sleep should see, and by day the same calm sky says "pause".
 */
final class NightCover {
    enum Mode { NIGHT, FOCUS, LIMIT }

    static final String[] WHISPERS = {
        "屏幕也需要休息，你也一样。",
        "现在放下，明天会轻松很多。",
        "把手机放远一点，深呼吸三次。",
        "今天已经够努力了，去睡吧。",
        "想做的事，明早再做也来得及。",
        "夜里的决定，常常不是最好的决定。",
    };
    static final String[] FOCUS_WHISPERS = {
        "专注的时间很珍贵，再坚持一下。",
        "手机不会跑掉，等这段忙完再看。",
        "你选择了专注，这个决定很棒。",
    };

    /** Shown when an app lock closes. The tone is a kept promise, not a punishment. */
    static final String[] LIMIT_WHISPERS = {
        "停在这里刚刚好，剩下的时间留给自己。",
        "放下它，世界不会错过你。",
        "说到做到的感觉，比再刷一条更好。",
        "抬头看看身边，去喝杯水、走两步。",
        "它明天还在，今天的你可以去做点别的。",
    };

    final View root; final TextView countdown;
    private final long endsAt;

    private NightCover(View root, TextView countdown, long endsAt) { this.root = root; this.countdown = countdown; this.endsAt = endsAt; }

    /**
     * @param endsAt when the cover lifts by itself (epoch millis)
     * @param endText clock text for the night's end, or null for other modes
     * @param limitMinutes the app's daily limit, for the limit mode
     * @param blocks stops counted tonight (night) or today (focus, limit), including this one
     * @param waitMinutes cooling-off time a request would need right now
     */
    static NightCover build(Context c, Mode mode, Drawable icon, String appName, long endsAt, String endText, int limitMinutes, int blocks,
                            long waitMinutes, boolean split, Runnable home, Runnable unlock) {
        boolean wasDark = BlueUi.dark; BlueUi.palette(true);
        try {
            FrameLayout frame = new FrameLayout(c);
            frame.addView(new Sky(c, mode != Mode.NIGHT), new FrameLayout.LayoutParams(-1, -1));
            ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout page = BlueUi.column(c); page.setGravity(Gravity.CENTER_HORIZONTAL); int pad = BlueUi.dp(c, 28);
            page.setPadding(pad, BlueUi.dp(c, 96), pad, BlueUi.dp(c, 40)); scroll.addView(page);

            ImageView sleeping = new ImageView(c); sleeping.setImageDrawable(icon); sleeping.setContentDescription(appName);
            ColorMatrix gray = new ColorMatrix(); gray.setSaturation(0); sleeping.setColorFilter(new ColorMatrixColorFilter(gray)); sleeping.setImageAlpha(150);
            int pd = BlueUi.dp(c, 14); sleeping.setPadding(pd, pd, pd, pd); sleeping.setBackground(BlueUi.background(c, 0x22ffffff, 0x33ffffff, 26));
            page.addView(sleeping, new LinearLayout.LayoutParams(BlueUi.dp(c, 80), BlueUi.dp(c, 80))); BlueUi.gap(page, 22);

            if (mode == Mode.LIMIT) { page.addView(BlueUi.pill(c, "应用锁 · 已锁定", BlueUi.INK, 0x1fffffff)); BlueUi.gap(page, 16); }
            String titleText = mode == Mode.NIGHT ? appName + " 已经睡了" : mode == Mode.FOCUS ? "专注中，" + appName + " 先等等" : "今天的「" + appName + "」到点了";
            String whisperText = mode == Mode.NIGHT ? WHISPERS[Math.floorMod(blocks - 1, WHISPERS.length)]
                    : mode == Mode.FOCUS ? FOCUS_WHISPERS[Math.floorMod(blocks - 1, FOCUS_WHISPERS.length)]
                    : "说好每天 " + Policy.duration(limitMinutes * 60000L) + "，今天的份已经用完。\n" + LIMIT_WHISPERS[Math.floorMod(blocks - 1, LIMIT_WHISPERS.length)];
            TextView title = BlueUi.label(c, titleText, 24, BlueUi.INK, true); title.setGravity(Gravity.CENTER); page.addView(title); BlueUi.gap(page, 12);
            TextView whisper = BlueUi.label(c, whisperText, 15, BlueUi.MUTED, false); whisper.setGravity(Gravity.CENTER); page.addView(whisper);
            BlueUi.gap(page, 40);

            TextView countdown = BlueUi.label(c, "", 40, BlueUi.INK, false); countdown.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            countdown.setLetterSpacing(0.04f); countdown.setFontFeatureSettings("tnum"); countdown.setGravity(Gravity.CENTER); page.addView(countdown); BlueUi.gap(page, 8);
            String captionText = mode == Mode.NIGHT ? "距离 " + endText + " 自动解除" : mode == Mode.FOCUS ? "专注结束后自动解除" : "明天 00:00 自动解锁";
            TextView caption = BlueUi.label(c, captionText, 13, BlueUi.MUTED, false); caption.setGravity(Gravity.CENTER); page.addView(caption); BlueUi.gap(page, 22);

            if (blocks > 0) { page.addView(BlueUi.pill(c, (mode == Mode.NIGHT ? "今晚" : "今天") + "已帮你挡下 " + blocks + " 次", BlueUi.STAR, 0x26f2d58a)); BlueUi.gap(page, 34); }

            LinearLayout actions = BlueUi.column(c); page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            actions.addView(BlueUi.button(c, mode == Mode.LIMIT ? "好，明天再见" : "回到桌面", true, home), new LinearLayout.LayoutParams(-1, -2)); BlueUi.gap(actions, 12);
            Button need = BlueUi.button(c, mode == Mode.LIMIT ? "再用 5 分钟" : "确实需要用一下", false, unlock); need.setBackground(BlueUi.ripple(c, 0x14ffffff, 12)); need.setTextColor(BlueUi.MUTED);
            actions.addView(need, new LinearLayout.LayoutParams(-1, -2)); BlueUi.gap(actions, 14);
            TextView rule = BlueUi.label(c, "需写明原因并冷静 " + waitMinutes + " 分钟，之后只开放这个应用 5 分钟", 12, BlueUi.MUTED, false);
            rule.setGravity(Gravity.CENTER); actions.addView(rule);
            if (split) { BlueUi.gap(actions, 10); TextView note = BlueUi.label(c, "分屏和小窗里的应用也会被遮住", 12, BlueUi.MUTED, false); note.setGravity(Gravity.CENTER); actions.addView(note); }

            NightCover cover = new NightCover(frame, countdown, endsAt); cover.tick(java.time.ZonedDateTime.now());
            return cover;
        } finally { BlueUi.palette(wasDark); }
    }

    void tick(java.time.ZonedDateTime now) {
        long s = Math.max(0, (endsAt - now.toInstant().toEpochMilli() + 999) / 1000);
        countdown.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
    }

    /** A still sky: a soft gradient and stars placed the same way all day; at night, a crescent moon too. */
    static final class Sky extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final long seed = java.time.LocalDate.now().toEpochDay(); private final boolean dusk;
        Sky(Context c, boolean dusk) { super(c); this.dusk = dusk; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int[] colors = dusk ? new int[]{0xff0b1630, 0xff1a2a4d, 0xff3a3557} : new int[]{0xff060c18, 0xff0d1930, 0xff1a2a47};
            p.setShader(new LinearGradient(0, 0, 0, h, colors, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            Random r = new Random(seed); float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < (dusk ? 30 : 70); i++) {
                float x = r.nextFloat() * w, y = (float) Math.pow(r.nextFloat(), 1.6) * h * 0.75f, size = (0.5f + r.nextFloat() * 1.1f) * d;
                p.setColor(Color.argb(60 + r.nextInt(150), 255, 248, 230)); c.drawCircle(x, y, size, p);
            }
            if (dusk) return;
            float mx = w * 0.8f, my = h * 0.11f, mr = 22 * d;
            p.setColor(0x14f2d58a); c.drawCircle(mx, my, mr * 2.4f, p);
            Path moon = new Path(); moon.addCircle(mx, my, mr, Path.Direction.CW);
            Path bite = new Path(); bite.addCircle(mx + mr * 0.45f, my - mr * 0.35f, mr * 0.85f, Path.Direction.CW); moon.op(bite, Path.Op.DIFFERENCE);
            p.setColor(0xfff2d58a); c.drawPath(moon, p);
        }
    }
}
