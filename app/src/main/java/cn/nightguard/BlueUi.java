package cn.nightguard;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/**
 * Peace's small design system: one palette that follows the system's dark mode, a type scale,
 * spacing, line icons and the building blocks every screen is made of (large titles, section
 * headers, grouped cards, list rows, stat tiles, rings, progress lines, segmented controls and
 * bottom sheets). The night cover always uses the dark palette.
 */
final class BlueUi {
    static int INK, MUTED, SUBTLE, BLUE, PAGE, LINE, PALE, CARD, HERO, SELECTED, ON_BLUE, STAR, DANGER, GOOD;
    static final int WHITE = Color.WHITE;
    static boolean dark;
    static { palette(false); }

    // Type scale (sp)
    static final int LARGE_TITLE = 30, TITLE = 20, HEADLINE = 16, BODY = 15, FOOTNOTE = 13, CAPTION = 12;

    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Selects the light or dark palette; call before building views. */
    static void use(Context c) { palette(systemDark(c)); }

    static void palette(boolean night) {
        dark = night;
        if (night) {
            INK = 0xffeceef5; MUTED = 0xff9aa1b2; SUBTLE = 0xff687083; BLUE = 0xff8b98ff; PAGE = 0xff0c0e14; LINE = 0xff262a36;
            PALE = 0xff232849; CARD = 0xff171a23; HERO = 0xff1c2140; SELECTED = 0xff232849; ON_BLUE = 0xff0c0e14;
            STAR = 0xfff2c26b; DANGER = 0xffff6b6e; GOOD = 0xff4cc38a;
        } else {
            INK = 0xff161a26; MUTED = 0xff6b7385; SUBTLE = 0xffa0a7b6; BLUE = 0xff4f5fd8; PAGE = 0xfff4f5f9; LINE = 0xffe9ebf1;
            PALE = 0xffeceefd; CARD = Color.WHITE; HERO = 0xffeef0fe; SELECTED = 0xffeef0fd; ON_BLUE = Color.WHITE;
            STAR = 0xffe9a23b; DANGER = 0xffe5484d; GOOD = 0xff2e9e6a;
        }
    }

    /** The same colour at a given opacity (0..255). */
    static int alpha(int color, int a) { return (color & 0x00ffffff) | (a << 24); }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    static GradientDrawable background(Context c, int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(dp(c, 1), border);
        return d;
    }

    static RippleDrawable ripple(Context c, int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(BLUE, 0x22)), background(c, color, 0, radius), null);
    }

    static TextView label(Context c, String value, int size, int color, boolean medium) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setIncludeFontPadding(false); t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setLineSpacing(dp(c, 3), 1);
        return t;
    }

    /** Text centred in its row. Children of a vertical LinearLayout fill the width by default, so centring the layout alone is not enough. */
    static TextView centered(TextView t) { t.setGravity(Gravity.CENTER_HORIZONTAL); return t; }

    static TextView text(Context c, String value, int size, int color) { return label(c, value, size, color, false); }

    /** Numbers that change in place keep their width. */
    static TextView number(Context c, String value, int size, int color) {
        TextView t = label(c, value, size, color, true); t.setFontFeatureSettings("tnum"); return t;
    }

    static void gap(LinearLayout parent, int height) { View space = new View(parent.getContext()); parent.addView(space, new LinearLayout.LayoutParams(1, dp(parent.getContext(), height))); }

    static void divider(LinearLayout parent) { View v = new View(parent.getContext()); v.setBackgroundColor(LINE); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), 1))); }

    /** A hairline that starts after a row's leading icon, like grouped lists on iOS and in Settings. */
    static void insetDivider(LinearLayout parent, int insetDp) {
        Context c = parent.getContext(); View v = new View(c); v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0.7f))); lp.leftMargin = dp(c, insetDp);
        parent.addView(v, lp);
    }

    static void page(Activity activity, View body) {
        Window w = activity.getWindow();
        body.setBackgroundColor(PAGE); activity.setContentView(body); // creates the decor view; the insets controller needs it
        w.setDecorFitsSystemWindows(false); w.setStatusBarColor(Color.TRANSPARENT); w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setBackgroundColor(PAGE);
        WindowInsetsController bars = w.getDecorView().getWindowInsetsController();
        if (bars != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            bars.setSystemBarsAppearance(dark ? 0 : light, light);
        }
        body.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom); return WindowInsets.CONSUMED;
        });
    }

    // ---- Buttons ------------------------------------------------------------------------------

    static Button button(Context c, String value, boolean primary, Runnable action) {
        Button b = new Button(c); b.setText(value); b.setTextSize(15); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setTextColor(primary ? ON_BLUE : BLUE);
        b.setMinHeight(dp(c, 50)); b.setMinimumHeight(dp(c, 50)); b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackgroundTintList(null); b.setBackground(ripple(c, primary ? BLUE : PALE, 14));
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
        FrameLayout box = new FrameLayout(c); box.setContentDescription(description); box.setBackground(ripple(c, Color.TRANSPARENT, 22));
        Glyph glyph = new Glyph(c, kind, INK); FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER);
        box.addView(glyph, p); box.setOnClickListener(v -> action.run()); box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return box;
    }

    /** Small rounded tag, e.g. "守护中". */
    static TextView pill(Context c, String value, int fg, int bg) {
        TextView t = label(c, value, 12, fg, true); t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5)); t.setBackground(background(c, bg, 0, 20));
        return t;
    }

    // ---- Page structure -----------------------------------------------------------------------

    /** Large page title with a one-line subtitle; {@code trailing} sits on the right, e.g. a status pill. */
    static LinearLayout largeTitle(Context c, String title, String subtitle, View trailing) {
        LinearLayout header = row(c); header.setGravity(Gravity.BOTTOM); LinearLayout titles = column(c);
        titles.addView(label(c, title, LARGE_TITLE, INK, true));
        if (subtitle != null) { gap(titles, 8); titles.addView(label(c, subtitle, FOOTNOTE, MUTED, false)); }
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null) header.addView(trailing);
        return header;
    }

    /** Back button plus a title, for pushed pages. */
    static LinearLayout navBar(Activity a, String title, String subtitle) {
        LinearLayout header = row(a); header.setPadding(dp(a, 6), dp(a, 8), dp(a, 20), dp(a, 4));
        header.addView(iconButton(a, "back", "返回", a::onBackPressed));
        LinearLayout titles = column(a); titles.addView(label(a, title, 22, INK, true));
        if (subtitle != null) { gap(titles, 5); titles.addView(label(a, subtitle, CAPTION, MUTED, false)); }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(a, 4); header.addView(titles, lp);
        return header;
    }

    /** A quiet heading above a group, with an optional action on the right. */
    static LinearLayout sectionHeader(LinearLayout parent, String title, String action, Runnable run) {
        Context c = parent.getContext(); LinearLayout h = row(c); h.setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 8)); h.setMinimumHeight(dp(c, 30));
        h.addView(label(c, title, FOOTNOTE, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) { TextView l = link(c, action, run); l.setMinHeight(dp(c, 28)); l.setPadding(dp(c, 8), 0, 0, 0); h.addView(l); }
        parent.addView(h); return h;
    }

    /** A rounded card that holds rows or content. {@code padded} adds inner padding for free content. */
    static LinearLayout group(LinearLayout parent, boolean padded) { return group(parent, padded, CARD); }

    static LinearLayout group(LinearLayout parent, boolean padded, int color) {
        Context c = parent.getContext(); LinearLayout g = column(c); g.setBackground(background(c, color, 0, 18));
        if (padded) g.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        g.setClipToOutline(true); parent.addView(g, new LinearLayout.LayoutParams(-1, -2)); return g;
    }

    /** Footnote under a group. */
    static void footnote(LinearLayout parent, String value) {
        Context c = parent.getContext(); TextView t = label(c, value, CAPTION, MUTED, false); t.setPadding(dp(c, 4), dp(c, 8), dp(c, 4), 0); parent.addView(t);
    }

    /** A line icon on a softly tinted rounded square. */
    static View iconTile(Context c, String kind, int tint) {
        FrameLayout box = new FrameLayout(c); box.setBackground(background(c, alpha(tint, dark ? 0x33 : 0x1f), 0, 9));
        box.addView(new Glyph(c, kind, tint), new FrameLayout.LayoutParams(dp(c, 18), dp(c, 18), Gravity.CENTER));
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return box;
    }

    static ImageView appIcon(Context c, Drawable icon) {
        ImageView v = new ImageView(c); v.setImageDrawable(icon); v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }

    /** The parts of a list row, so callers can update texts later. */
    static final class Item {
        final LinearLayout root, labels; final TextView title, subtitle, value;
        Item(LinearLayout root, LinearLayout labels, TextView title, TextView subtitle, TextView value) { this.root = root; this.labels = labels; this.title = title; this.subtitle = subtitle; this.value = value; }
    }

    /**
     * A list row: leading view (icon tile or app icon), title and subtitle, a trailing value and a
     * chevron when it opens something. Rows are at least 60dp tall so they are easy to tap.
     */
    static Item item(LinearLayout group, View leading, int leadingDp, String title, String subtitle, String value, Runnable action) {
        Context c = group.getContext(); LinearLayout r = row(c); r.setPadding(dp(c, 16), dp(c, 12), dp(c, 12), dp(c, 12)); r.setMinimumHeight(dp(c, 60));
        if (leading != null) r.addView(leading, new LinearLayout.LayoutParams(dp(c, leadingDp), dp(c, leadingDp)));
        LinearLayout labels = column(c); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(leading != null ? dp(c, 14) : 0, 0, dp(c, 8), 0); r.addView(labels, lp);
        TextView t = label(c, title, BODY, INK, true); t.setMaxLines(1); t.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(t);
        TextView s = null;
        if (subtitle != null) { gap(labels, 5); s = label(c, subtitle, CAPTION, MUTED, false); labels.addView(s); }
        TextView v = null;
        if (value != null) { v = label(c, value, FOOTNOTE, MUTED, false); r.addView(v); }
        if (action != null) {
            LinearLayout.LayoutParams chevron = new LinearLayout.LayoutParams(dp(c, 16), dp(c, 16)); chevron.leftMargin = dp(c, 4);
            r.addView(new Glyph(c, "next", SUBTLE), chevron);
            r.setBackground(ripple(c, Color.TRANSPARENT, 0)); r.setOnClickListener(x -> action.run());
        }
        r.setContentDescription(title + (subtitle != null ? "，" + subtitle : "") + (value != null ? "，" + value : ""));
        group.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return new Item(r, labels, t, s, v);
    }

    /** Label, a large number and a caption: three of these sit side by side. */
    static LinearLayout statTile(Context c, String label, String value, String caption) {
        LinearLayout t = column(c); t.setPadding(dp(c, 14), dp(c, 14), dp(c, 10), dp(c, 14)); t.setBackground(background(c, CARD, 0, 16));
        t.addView(label(c, label, CAPTION, MUTED, false)); gap(t, 8);
        t.addView(number(c, value, 21, INK)); gap(t, 6);
        TextView cap = label(c, caption, 11, MUTED, false); cap.setMaxLines(2); cap.setEllipsize(android.text.TextUtils.TruncateAt.END); t.addView(cap);
        t.setContentDescription(label + " " + value + "，" + caption);
        return t;
    }

    /** Tiles in one row with equal widths and a small gap. */
    static LinearLayout tiles(LinearLayout parent, View... tiles) {
        Context c = parent.getContext(); LinearLayout r = row(c); r.setGravity(Gravity.TOP);
        for (int i = 0; i < tiles.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); if (i > 0) lp.leftMargin = dp(c, 10); r.addView(tiles[i], lp);
        }
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2)); return r;
    }

    /** A segmented control. Calls {@code chosen} with the index tapped. */
    static LinearLayout segmented(Context c, String[] options, int selected, java.util.function.IntConsumer chosen) {
        LinearLayout bar = row(c); int pad = dp(c, 3); bar.setPadding(pad, pad, pad, pad); bar.setBackground(background(c, dark ? CARD : LINE, 0, 12));
        for (int i = 0; i < options.length; i++) {
            boolean on = i == selected; int index = i;
            TextView t = label(c, options[i], FOOTNOTE, on ? INK : MUTED, on); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(c, 34));
            if (on) t.setBackground(background(c, dark ? PALE : CARD, 0, 10)); else t.setBackground(ripple(c, Color.TRANSPARENT, 10));
            t.setOnClickListener(v -> chosen.accept(index)); t.setContentDescription(options[i] + (on ? "，已选" : ""));
            bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return bar;
    }

    /** An empty state: a tinted icon, a title and a hint. */
    static LinearLayout empty(Context c, String kind, String title, String hint) {
        LinearLayout box = column(c); box.setGravity(Gravity.CENTER_HORIZONTAL); box.setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 24));
        FrameLayout circle = new FrameLayout(c); circle.setBackground(background(c, PALE, 0, 32));
        circle.addView(new Glyph(c, kind, BLUE), new FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), Gravity.CENTER));
        box.addView(circle, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64))); gap(box, 16);
        TextView t = label(c, title, HEADLINE, INK, true); t.setGravity(Gravity.CENTER); box.addView(t); gap(box, 8);
        TextView h = label(c, hint, FOOTNOTE, MUTED, false); h.setGravity(Gravity.CENTER); box.addView(h);
        return box;
    }

    // ---- Bottom sheet -------------------------------------------------------------------------

    /**
     * A sheet that slides up from the bottom: replaces stacked system dialogs with one calm,
     * consistent surface. Options and buttons close the sheet before running their action.
     */
    static final class Sheet {
        final Dialog dialog; final LinearLayout body; private final Context c;

        Sheet(Activity a, String title, String message) {
            c = a; dialog = new Dialog(a); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ScrollView scroll = new ScrollView(a); scroll.setFillViewport(false);
            LinearLayout box = column(a); box.setPadding(dp(a, 22), dp(a, 10), dp(a, 22), dp(a, 22));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); float r = dp(a, 26); bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            scroll.setBackground(bg); scroll.addView(box);
            View handle = new View(a); handle.setBackground(background(a, LINE, 0, 3));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(a, 36), dp(a, 5)); hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = dp(a, 16); box.addView(handle, hp);
            if (title != null) { box.addView(label(a, title, TITLE, INK, true)); gap(box, 8); }
            if (message != null) { TextView m = label(a, message, 14, MUTED, false); box.addView(m); gap(box, 8); }
            body = column(a); box.addView(body, new LinearLayout.LayoutParams(-1, -2));
            dialog.setContentView(scroll);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setGravity(Gravity.BOTTOM);
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                w.setWindowAnimations(android.R.style.Animation_InputMethod); w.setDimAmount(0.42f);
            }
        }

        /** A choosable line; {@code checked} draws a tick. */
        Sheet option(String title, String detail, boolean checked, Runnable action) {
            LinearLayout r = row(c); r.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12)); r.setMinimumHeight(dp(c, 54));
            r.setBackground(ripple(c, checked ? SELECTED : Color.TRANSPARENT, 12));
            LinearLayout labels = column(c); r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            labels.addView(label(c, title, BODY, checked ? BLUE : INK, true));
            if (detail != null) { gap(labels, 4); labels.addView(label(c, detail, CAPTION, MUTED, false)); }
            if (checked) r.addView(new Glyph(c, "check", BLUE), new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
            r.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
            r.setContentDescription(title + (detail != null ? "，" + detail : "") + (checked ? "，当前选择" : ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 2); body.addView(r, lp);
            return this;
        }

        Sheet view(View v) { body.addView(v, new LinearLayout.LayoutParams(-1, -2)); return this; }

        Sheet text(String value) { TextView t = label(c, value, 14, INK, false); body.addView(t); gap(body, 6); return this; }

        Sheet button(String value, boolean primary, Runnable action) {
            gap(body, 10); body.addView(BlueUi.button(c, value, primary, () -> { dialog.dismiss(); if (action != null) action.run(); }), new LinearLayout.LayoutParams(-1, -2));
            return this;
        }

        void show() { dialog.show(); }
    }

    // ---- Icons --------------------------------------------------------------------------------

    static final class Glyph extends View {
        private final String kind; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private int color;
        Glyph(Context c, String kind, int color) { super(c); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void tint(int value) { color = value; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            switch (kind) {
                case "back": path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19); c.drawPath(path, p); break;
                case "next": path.moveTo(9, 6); path.lineTo(15, 12); path.lineTo(9, 18); c.drawPath(path, p); break;
                case "search": c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(16, 16, 21, 21, p); break;
                case "check": path.moveTo(5, 12); path.lineTo(10, 17); path.lineTo(19, 7); c.drawPath(path, p); break;
                case "plus": c.drawLine(12, 5, 12, 19, p); c.drawLine(5, 12, 19, 12, p); break;
                case "moon": // crescent: a circle minus an offset circle
                    path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                    Path bite = new Path(); bite.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW);
                    path.op(bite, Path.Op.DIFFERENCE); c.drawPath(path, p); break;
                case "grid": for (int x = 4; x <= 14; x += 10) for (int y = 4; y <= 14; y += 10) c.drawRoundRect(x, y, x + 6, y + 6, 1.5f, 1.5f, p); break;
                case "clock": c.drawCircle(12, 12, 9, p); c.drawLine(12, 6.5f, 12, 12, p); c.drawLine(12, 12, 15.5f, 14, p); break;
                case "more": p.setStyle(Paint.Style.FILL); for (int y = 5; y <= 19; y += 7) c.drawCircle(12, y, 1.5f, p); break;
                case "lock": c.drawRoundRect(5, 10.5f, 19, 21, 2.5f, 2.5f, p); path.moveTo(8, 10.5f); path.lineTo(8, 7.5f); path.cubicTo(8, 2.5f, 16, 2.5f, 16, 7.5f); path.lineTo(16, 10.5f); c.drawPath(path, p); c.drawLine(12, 14.5f, 12, 17, p); break;
                case "hourglass": path.moveTo(6.5f, 3.5f); path.lineTo(17.5f, 3.5f); path.moveTo(6.5f, 20.5f); path.lineTo(17.5f, 20.5f);
                    path.moveTo(8, 3.5f); path.cubicTo(8, 9, 16, 9, 16, 12); path.cubicTo(16, 15, 8, 15, 8, 20.5f);
                    path.moveTo(16, 3.5f); path.cubicTo(16, 9, 8, 9, 8, 12); path.cubicTo(8, 15, 16, 15, 16, 20.5f); c.drawPath(path, p); break;
                case "chart": c.drawLine(5, 20, 5, 13, p); c.drawLine(10, 20, 10, 8, p); c.drawLine(15, 20, 15, 11, p); c.drawLine(20, 20, 20, 5, p); break;
                case "ring": c.drawCircle(12, 12, 8.5f, p); p.setStrokeWidth(3.2f); c.drawArc(3.5f, 3.5f, 20.5f, 20.5f, -90, 250, false, p); break;
                case "gear": c.drawCircle(12, 12, 3.2f, p);
                    for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; c.drawLine(12 + (float) (6.2 * Math.cos(a)), 12 + (float) (6.2 * Math.sin(a)), 12 + (float) (8.8 * Math.cos(a)), 12 + (float) (8.8 * Math.sin(a)), p); }
                    c.drawCircle(12, 12, 6.2f, p); break;
                case "target": c.drawCircle(12, 12, 8.5f, p); c.drawCircle(12, 12, 4.5f, p); p.setStyle(Paint.Style.FILL); c.drawCircle(12, 12, 1.4f, p); break;
                case "list": for (int y = 6; y <= 18; y += 6) { c.drawLine(9, y, 20, y, p); c.drawPoint(4.5f, y, p); } break;
                case "info": c.drawCircle(12, 12, 9, p); c.drawLine(12, 11, 12, 16.5f, p); p.setStyle(Paint.Style.FILL); c.drawCircle(12, 7.6f, 1.1f, p); break;
                case "phone": c.drawRoundRect(7, 3, 17, 21, 2.5f, 2.5f, p); c.drawLine(10.5f, 18, 13.5f, 18, p); break;
                case "leaf": path.moveTo(5, 19); path.cubicTo(5, 9, 11, 4, 20, 4); path.cubicTo(20, 13, 15, 19, 5, 19); path.close(); c.drawPath(path, p); c.drawLine(5, 19, 13, 11, p); break;
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

    // ---- Data views ---------------------------------------------------------------------------

    /**
     * A progress ring, like Screen Time and Activity: the track, the used part in blue, and past
     * the goal a second lap in amber. Centre content is added as children of the returned frame.
     */
    static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private double fraction; private boolean hasGoal;
        Ring(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(double used, double goal) { hasGoal = goal > 0; fraction = hasGoal ? used / goal : 0; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float stroke = getWidth() * 0.085f, inset = stroke / 2 + 1; RectF box = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(stroke); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(LINE); c.drawArc(box, 0, 360, false, p);
            if (!hasGoal || fraction <= 0) return;
            p.setColor(BLUE); c.drawArc(box, -90, (float) (360 * Math.min(1, fraction)), false, p);
            if (fraction > 1) { p.setColor(STAR); c.drawArc(box, -90, (float) (360 * Math.min(0.999, fraction - 1)), false, p); }
        }
    }

    /** A thin rounded bar filled to a fraction in the given colour. */
    static final class Line extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private float fraction; private int color = BLUE;
        Line(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(double value, int color) { fraction = (float) Math.max(0, Math.min(1, value)); this.color = color; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setColor(LINE); c.drawRoundRect(0, 0, w, h, r, r, p);
            if (fraction > 0) { p.setColor(color); c.drawRoundRect(0, 0, Math.max(h, w * fraction), h, r, r, p); }
        }
    }

    /** Today's use against the goal. Past the goal the bar stays full and the overflow shows in amber at the end. */
    static final class Meter extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private double used, goal;
        Meter(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(double used, double goal) { this.used = used; this.goal = goal; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setColor(LINE); c.drawRoundRect(0, 0, w, h, r, r, p);
            if (goal <= 0) return;
            double f = used / goal;
            if (f <= 1) { p.setColor(BLUE); if (f > 0) c.drawRoundRect(0, 0, Math.max(h, (float) (w * f)), h, r, r, p); return; }
            float split = (float) (w / f); // where the goal sits on a bar scaled to today's use
            p.setColor(BLUE); c.drawRoundRect(0, 0, w, h, r, r, p);
            c.save(); c.clipRect(split + 2 * getResources().getDisplayMetrics().density, 0, w, h); p.setColor(STAR); c.drawRoundRect(0, 0, w, h, r, r, p); c.restore();
        }
    }

    /** One night as a band: soft blue where the phone was put down, amber where it was in use, grey where nothing was recorded. */
    static final class UseTimeline extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private Store.NightUse use;
        UseTimeline(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void set(Store.NightUse value) { use = value; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setStyle(Paint.Style.FILL); p.setColor(LINE); c.drawRoundRect(0, 0, w, h, r, r, p);
            if (use == null || use.end <= use.start) return;
            double span = use.end - use.start;
            c.save(); Path clip = new Path(); clip.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW); c.clipPath(clip);
            p.setColor(alpha(BLUE, dark ? 0x66 : 0x55));
            c.drawRect((float) ((use.from - use.start) / span * w), 0, (float) ((use.to - use.start) / span * w), h, p);
            p.setColor(STAR); float min = getResources().getDisplayMetrics().density * 2;
            for (long[] s : use.sessions) {
                long a = Math.max(s[0], use.from), b = Math.min(s[1], use.to); if (b <= a) continue;
                float x1 = (float) ((a - use.start) / span * w), x2 = Math.max(x1 + min, (float) ((b - use.start) / span * w));
                c.drawRect(x1, 0, x2, h, p);
            }
            c.restore();
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
