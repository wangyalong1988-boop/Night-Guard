package cn.nightguard;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Random;

/**
 * The screen shown over a restricted app. It is always dark: it appears in the middle of the night,
 * so a bright white page would be the last thing someone trying to sleep should see.
 */
final class NightCover {
    static final String[] WHISPERS = {
        "屏幕也需要休息，你也一样。",
        "现在放下，明天会轻松很多。",
        "把手机放远一点，深呼吸三次。",
        "今天已经够努力了，去睡吧。",
        "想做的事，明早再做也来得及。",
        "夜里的决定，常常不是最好的决定。",
    };

    final View root; final TextView countdown, caption;
    private final Policy.Schedule schedule;

    private NightCover(View root, TextView countdown, TextView caption, Policy.Schedule schedule) { this.root = root; this.countdown = countdown; this.caption = caption; this.schedule = schedule; }

    /**
     * @param blocks how many times tonight an app has been stopped, including this one
     * @param waitMinutes cooling-off time a request would need right now
     */
    static NightCover build(Context c, String pkg, Drawable icon, String appName, Policy.Schedule schedule, int blocks, long waitMinutes,
                            boolean split, Runnable home, Runnable unlock) {
        boolean wasDark = BlueUi.dark; BlueUi.palette(true);
        try {
            FrameLayout frame = new FrameLayout(c);
            frame.addView(new Sky(c), new FrameLayout.LayoutParams(-1, -1));
            ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout page = BlueUi.column(c); page.setGravity(Gravity.CENTER_HORIZONTAL); int pad = BlueUi.dp(c, 28);
            page.setPadding(pad, BlueUi.dp(c, 96), pad, BlueUi.dp(c, 40)); scroll.addView(page);

            ImageView sleeping = new ImageView(c); sleeping.setImageDrawable(icon); sleeping.setContentDescription(appName);
            ColorMatrix gray = new ColorMatrix(); gray.setSaturation(0); sleeping.setColorFilter(new ColorMatrixColorFilter(gray)); sleeping.setImageAlpha(150);
            int pd = BlueUi.dp(c, 14); sleeping.setPadding(pd, pd, pd, pd); sleeping.setBackground(BlueUi.background(c, 0x22ffffff, 0x33ffffff, 26));
            page.addView(sleeping, new LinearLayout.LayoutParams(BlueUi.dp(c, 80), BlueUi.dp(c, 80))); BlueUi.gap(page, 22);

            TextView title = BlueUi.label(c, appName + " 已经睡了", 24, BlueUi.INK, true); title.setGravity(Gravity.CENTER); page.addView(title); BlueUi.gap(page, 12);
            TextView whisper = BlueUi.label(c, WHISPERS[Math.floorMod(blocks - 1, WHISPERS.length)], 15, BlueUi.MUTED, false); whisper.setGravity(Gravity.CENTER); page.addView(whisper);
            BlueUi.gap(page, 40);

            TextView countdown = BlueUi.label(c, "", 40, BlueUi.INK, false); countdown.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            countdown.setLetterSpacing(0.04f); countdown.setFontFeatureSettings("tnum"); countdown.setGravity(Gravity.CENTER); page.addView(countdown); BlueUi.gap(page, 8);
            TextView caption = BlueUi.label(c, "", 13, BlueUi.MUTED, false); caption.setGravity(Gravity.CENTER); page.addView(caption); BlueUi.gap(page, 22);

            if (blocks > 0) { TextView count = BlueUi.pill(c, "今晚已帮你挡下 " + blocks + " 次", BlueUi.STAR, 0x26f2d58a); page.addView(count); BlueUi.gap(page, 34); }

            LinearLayout actions = BlueUi.column(c); page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            actions.addView(BlueUi.button(c, "回到桌面", true, home), new LinearLayout.LayoutParams(-1, -2)); BlueUi.gap(actions, 12);
            Button need = BlueUi.button(c, "确实需要用一下", false, unlock); need.setBackground(BlueUi.ripple(c, 0x14ffffff, 12)); need.setTextColor(BlueUi.MUTED);
            actions.addView(need, new LinearLayout.LayoutParams(-1, -2)); BlueUi.gap(actions, 14);
            TextView rule = BlueUi.label(c, "需写明原因并冷静 " + waitMinutes + " 分钟，之后只开放这个应用 5 分钟", 12, BlueUi.MUTED, false);
            rule.setGravity(Gravity.CENTER); actions.addView(rule);
            if (split) { BlueUi.gap(actions, 10); TextView note = BlueUi.label(c, "分屏和小窗里的受限应用也会被遮住", 12, BlueUi.MUTED, false); note.setGravity(Gravity.CENTER); actions.addView(note); }

            NightCover cover = new NightCover(frame, countdown, caption, schedule); cover.tick(ZonedDateTime.now());
            return cover;
        } finally { BlueUi.palette(wasDark); }
    }

    void tick(ZonedDateTime now) {
        long s = Math.max(0, Duration.between(now, Policy.nextBoundary(now, schedule)).getSeconds());
        countdown.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        caption.setText("距离 " + schedule.endText() + " 自动解除");
    }

    /** A still night sky: a soft gradient, a crescent moon and stars placed the same way all night. */
    static final class Sky extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final long seed = java.time.LocalDate.now().toEpochDay();
        Sky(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xff060c18, 0xff0d1930, 0xff1a2a47}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p); p.setShader(null);
            Random r = new Random(seed); float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < 70; i++) {
                float x = r.nextFloat() * w, y = (float) Math.pow(r.nextFloat(), 1.6) * h * 0.75f, size = (0.5f + r.nextFloat() * 1.1f) * d;
                p.setColor(Color.argb(60 + r.nextInt(150), 255, 248, 230)); c.drawCircle(x, y, size, p);
            }
            float mx = w * 0.8f, my = h * 0.11f, mr = 22 * d;
            p.setColor(0x14f2d58a); c.drawCircle(mx, my, mr * 2.4f, p);
            Path moon = new Path(); moon.addCircle(mx, my, mr, Path.Direction.CW);
            Path bite = new Path(); bite.addCircle(mx + mr * 0.45f, my - mr * 0.35f, mr * 0.85f, Path.Direction.CW); moon.op(bite, Path.Op.DIFFERENCE);
            p.setColor(0xfff2d58a); c.drawPath(moon, p);
        }
    }
}
