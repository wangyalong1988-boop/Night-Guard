package cn.nightguard;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/**
 * Shared colours, spacing, typography and line icons. The palette follows the system's dark mode;
 * the night cover always uses the dark one, because it appears in the middle of the night.
 */
final class BlueUi {
    static int INK, MUTED, BLUE, PAGE, LINE, PALE, CARD, HERO, SELECTED, ON_BLUE, STAR;
    static final int WHITE = Color.WHITE;
    static boolean dark;
    static { palette(false); }

    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Selects the light or dark palette; call before building views. */
    static void use(Context c) { palette(systemDark(c)); }

    static void palette(boolean night) {
        dark = night;
        if (night) {
            INK = 0xffe6edf7; MUTED = 0xff97a7bd; BLUE = 0xff8db8ec; PAGE = 0xff0d1625; LINE = 0xff22324a;
            PALE = 0xff1d3150; CARD = 0xff152137; HERO = 0xff1a2b46; SELECTED = 0xff1d3150; ON_BLUE = 0xff0d1625; STAR = 0xfff2d58a;
        } else {
            INK = 0xff1c314c; MUTED = 0xff65768c; BLUE = 0xff396fa8; PAGE = 0xfff2f6fb; LINE = 0xffe5ecf3;
            PALE = 0xffe4effb; CARD = Color.WHITE; HERO = 0xffdfeafa; SELECTED = 0xffe5effa; ON_BLUE = Color.WHITE; STAR = 0xffd9a521;
        }
    }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    static GradientDrawable background(Context c, int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(dp(c, 1), border);
        return d;
    }

    static RippleDrawable ripple(Context c, int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf((BLUE & 0x00ffffff) | 0x22000000), background(c, color, 0, radius), null);
    }

    static TextView label(Context c, String value, int size, int color, boolean medium) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setIncludeFontPadding(false); t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setLineSpacing(dp(c, 3), 1);
        return t;
    }

    static TextView text(Context c, String value, int size, int color) { return label(c, value, size, color, false); }

    static void gap(LinearLayout parent, int height) { View space = new View(parent.getContext()); parent.addView(space, new LinearLayout.LayoutParams(1, dp(parent.getContext(), height))); }

    static void divider(LinearLayout parent) { View v = new View(parent.getContext()); v.setBackgroundColor(LINE); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), 1))); }

    static void page(Activity activity, View body) {
        Window w = activity.getWindow();
        w.setDecorFitsSystemWindows(false); w.setStatusBarColor(Color.TRANSPARENT); w.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsController bars = w.getInsetsController();
        if (bars != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            bars.setSystemBarsAppearance(dark ? 0 : light, light);
        }
        w.getDecorView().setBackgroundColor(PAGE);
        body.setBackgroundColor(PAGE); activity.setContentView(body);
        body.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom); return WindowInsets.CONSUMED;
        });
    }

    static Button button(Context c, String value, boolean primary, Runnable action) {
        Button b = new Button(c); b.setText(value); b.setTextSize(15); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setTextColor(primary ? ON_BLUE : BLUE);
        b.setMinHeight(dp(c, 50)); b.setMinimumHeight(dp(c, 50)); b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackgroundTintList(null); b.setBackground(ripple(c, primary ? BLUE : PALE, 12));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    static TextView link(Context c, String value, Runnable action) {
        TextView t = label(c, value, 13, BLUE, true); t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 44)); t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        t.setBackground(ripple(c, Color.TRANSPARENT, 8)); t.setOnClickListener(v -> action.run());
        return t;
    }

    static View iconButton(Context c, String kind, String description, Runnable action) {
        FrameLayout box = new FrameLayout(c); box.setContentDescription(description); box.setBackground(ripple(c, Color.TRANSPARENT, 12));
        Glyph glyph = new Glyph(c, kind, INK); FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER);
        box.addView(glyph, p); box.setOnClickListener(v -> action.run()); box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return box;
    }

    /** Small rounded tag, e.g. "限制时段". */
    static TextView pill(Context c, String value, int fg, int bg) {
        TextView t = label(c, value, 12, fg, true); t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6)); t.setBackground(background(c, bg, 0, 8));
        return t;
    }

    static final class Glyph extends View {
        private final String kind; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final int color;
        Glyph(Context c, String kind, int color) { super(c); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            switch (kind) {
                case "back": path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19); c.drawPath(path, p); break;
                case "next": path.moveTo(9, 6); path.lineTo(15, 12); path.lineTo(9, 18); c.drawPath(path, p); break;
                case "search": c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(16, 16, 21, 21, p); break;
                case "check": path.moveTo(5, 12); path.lineTo(10, 17); path.lineTo(19, 7); c.drawPath(path, p); break;
                case "moon": // crescent: outer arc of a circle minus an offset circle
                    path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                    Path bite = new Path(); bite.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW);
                    path.op(bite, Path.Op.DIFFERENCE); c.drawPath(path, p); break;
                case "grid": for (int x = 4; x <= 14; x += 10) for (int y = 4; y <= 14; y += 10) c.drawRoundRect(x, y, x + 6, y + 6, 1.5f, 1.5f, p); break;
                case "clock": c.drawCircle(12, 12, 9, p); c.drawLine(12, 6, 12, 12, p); c.drawLine(12, 12, 16, 14, p); break;
                case "more": p.setStyle(Paint.Style.FILL); for (int y = 5; y <= 19; y += 7) c.drawCircle(12, y, 1.5f, p); break;
                case "lock": c.drawRoundRect(5, 10, 19, 21, 2, 2, p); c.drawArc(8, 2, 16, 16, 180, 180, false, p); c.drawLine(12, 14, 12, 17, p); break;
                case "bell": path.moveTo(5, 17); path.lineTo(7, 14); path.lineTo(7, 9); path.cubicTo(7, 2, 17, 2, 17, 9); path.lineTo(17, 14); path.lineTo(19, 17); path.close(); c.drawPath(path, p); c.drawArc(9, 17, 15, 22, 0, 180, false, p); break;
                case "edit": path.moveTo(5, 19); path.lineTo(5.6f, 15.4f); path.lineTo(15.5f, 5.5f); path.lineTo(18.5f, 8.5f); path.lineTo(8.6f, 18.4f); path.close(); c.drawPath(path, p); c.drawLine(13.5f, 7.5f, 16.5f, 10.5f, p); break;
                case "star": case "star-fill": case "star-half":
                    star(path, 12, 12.6f, 9.2f, 3.9f);
                    if (!kind.equals("star")) { p.setStyle(Paint.Style.FILL); if (kind.equals("star-half")) { c.save(); c.clipRect(0, 0, 12, 24); c.drawPath(path, p); c.restore(); } else c.drawPath(path, p); p.setStyle(Paint.Style.STROKE); }
                    c.drawPath(path, p); break;
                case "dot": p.setStyle(Paint.Style.FILL); c.drawCircle(12, 12, 2.2f, p); break;
                default: path.moveTo(12, 3); path.lineTo(20, 6); path.lineTo(20, 12); path.cubicTo(20, 17, 15, 20, 12, 22); path.cubicTo(9, 20, 4, 17, 4, 12); path.lineTo(4, 6); path.close(); c.drawPath(path, p); break;
            }
            c.restore();
        }
        static void star(Path path, float cx, float cy, float outer, float inner) {
            for (int i = 0; i < 10; i++) {
                double a = Math.PI / 2 + i * Math.PI / 5; float r = i % 2 == 0 ? outer : inner;
                float x = cx + (float) (r * Math.cos(a)), y = cy - (float) (r * Math.sin(a));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
        }
    }

    /** The night drawn as a line: elapsed part filled, a small moon where "now" is. */
    static final class NightBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private float fraction = -1;
        NightBar(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(double value) { fraction = (float) value; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth(), mid = h / 2, r = h / 2, track = Math.max(2, h / 5);
            p.setStyle(Paint.Style.FILL); p.setColor(LINE); c.drawRoundRect(r, mid - track / 2, w - r, mid + track / 2, track, track, p);
            if (fraction < 0) return;
            float x = r + (w - 2 * r) * fraction;
            p.setColor(BLUE); c.drawRoundRect(r, mid - track / 2, x, mid + track / 2, track, track, p);
            p.setColor(CARD); c.drawCircle(x, mid, r, p); p.setColor(BLUE); c.drawCircle(x, mid, r * 0.72f, p);
            p.setColor(CARD); c.drawCircle(x + r * 0.32f, mid - r * 0.28f, r * 0.58f, p);
        }
    }
}
