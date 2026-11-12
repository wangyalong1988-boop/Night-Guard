package cn.nightguard;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/**
 * A single-series bar chart drawn on a canvas: thin bars with rounded tops anchored to the
 * baseline, a recessive grid, an optional dashed goal line, and a tap-to-read tooltip.
 * Days without data show a small dot on the baseline instead of a zero bar.
 */
final class BarChart extends View {
    interface Format { String value(double v); String axis(double v); String title(int index); }

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double[] values = new double[0], goal; private String[] labels = new String[0]; private Format format;
    private int selected = -1, highlight = -1; private final float d;
    private double reference = Double.NaN; private String referenceLabel;

    /** A constant comparison line, drawn dotted in a quieter ink than the goal; NaN removes it. */
    void reference(double value, String label) { reference = value; referenceLabel = label; invalidate(); }

    BarChart(Context c) {
        super(c); d = getResources().getDisplayMetrics().density;
        setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                int i = indexAt(e.getX()); if (i != selected) { selected = i; invalidate(); }
                v.getParent().requestDisallowInterceptTouchEvent(true); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return true;
        });
    }

    @Override public boolean performClick() { return super.performClick(); }

    /** @param goal per-bar goal (NaN where none) or null; @param highlight bar drawn in full colour, usually today */
    void set(double[] values, String[] labels, double[] goal, int highlight, Format format) {
        this.values = values; this.labels = labels; this.goal = goal; this.highlight = highlight; this.format = format; selected = -1;
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < values.length; i++) if (!Double.isNaN(values[i])) sum.append(format.title(i)).append(' ').append(format.value(values[i])).append("；");
        setContentDescription(sum.length() == 0 ? "还没有数据" : sum.toString());
        invalidate();
    }

    private float left() { return 34 * d; }
    private float right() { return getWidth() - 6 * d; }
    private float top() { return 30 * d; }
    private float bottom() { return getHeight() - 20 * d; }

    private int indexAt(float x) {
        if (values.length == 0) return -1;
        float slot = (right() - left()) / values.length;
        int i = (int) ((x - left()) / slot);
        return i < 0 || i >= values.length ? -1 : i;
    }

    /** A rounded maximum and tick step, e.g. 0, 2h, 4h, 6h. */
    private double[] scale() {
        double max = 0;
        for (double v : values) if (!Double.isNaN(v)) max = Math.max(max, v);
        if (goal != null) for (double g : goal) if (!Double.isNaN(g)) max = Math.max(max, g);
        if (!Double.isNaN(reference)) max = Math.max(max, reference);
        double hour = 3600000, step = max <= 2 * hour ? hour / 2 : max <= 4 * hour ? hour : max <= 8 * hour ? 2 * hour : 3 * hour;
        if (max <= 0) max = 2 * hour;
        double top = Math.ceil(max / step) * step;
        return new double[]{top, step};
    }

    @Override protected void onDraw(Canvas c) {
        if (format == null) return;
        double[] sc = scale(); double top = sc[0], step = sc[1];
        float l = left(), r = right(), t = top(), b = bottom(), h = b - t;

        text.setTextSize(10 * d); text.setColor(BlueUi.MUTED); text.setTextAlign(Paint.Align.RIGHT);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1); line.setPathEffect(null);
        for (double v = 0; v <= top + 1; v += step) {
            float y = (float) (b - v / top * h);
            line.setColor(BlueUi.LINE); c.drawLine(l, y, r, y, line);
            c.drawText(format.axis(v), l - 6 * d, y + 3.5f * d, text);
        }

        int n = values.length; if (n == 0) return;
        float slot = (r - l) / n, w = Math.min(slot * 0.62f, 22 * d), radius = Math.min(4 * d, w / 2);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float cx = l + slot * (i + 0.5f);
            if (Double.isNaN(values[i])) { bar.setColor(BlueUi.LINE); c.drawCircle(cx, b - 2 * d, 2 * d, bar); }
            else {
                float y = (float) (b - Math.min(values[i], top) / top * h);
                int base = BlueUi.BLUE, alpha = (i == highlight || i == selected || highlight < 0) ? 0xff : 0x99;
                bar.setColor((base & 0x00ffffff) | (alpha << 24));
                if (b - y > 0.5f) {
                    Path p = new Path(); float rr = Math.min(radius, b - y);
                    p.addRoundRect(cx - w / 2, y, cx + w / 2, b, new float[]{rr, rr, rr, rr, 0, 0, 0, 0}, Path.Direction.CW);
                    c.drawPath(p, bar);
                }
            }
            if (labels[i] != null && !labels[i].isEmpty()) {
                text.setColor(i == highlight ? BlueUi.INK : BlueUi.MUTED); c.drawText(labels[i], cx, getHeight() - 5 * d, text);
            }
        }

        if (goal != null) {
            line.setColor(BlueUi.INK); line.setStrokeWidth(1.5f * d); line.setPathEffect(new DashPathEffect(new float[]{4 * d, 3 * d}, 0));
            Path path = new Path(); boolean open = false; float lastX = 0, lastY = 0;
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(goal[i])) { open = false; continue; }
                float y = (float) (b - Math.min(goal[i], top) / top * h), x0 = l + slot * i, x1 = x0 + slot;
                if (!open) path.moveTo(x0, y); else path.lineTo(x0, y);
                path.lineTo(x1, y); open = true; lastX = x1; lastY = y;
            }
            c.drawPath(path, line); line.setPathEffect(null);
            if (lastX > 0) { text.setTextAlign(Paint.Align.RIGHT); text.setColor(BlueUi.INK); c.drawText("目标", lastX, lastY - 5 * d, text); }
        }

        if (!Double.isNaN(reference)) {
            float y = (float) (b - Math.min(reference, top) / top * h);
            line.setColor(BlueUi.MUTED); line.setStrokeWidth(1.5f * d); line.setStrokeCap(Paint.Cap.ROUND);
            line.setPathEffect(new DashPathEffect(new float[]{0.1f, 4 * d}, 0)); // round dots, distinct from the dashed goal
            c.drawLine(l, y, r, y, line); line.setPathEffect(null); line.setStrokeCap(Paint.Cap.BUTT);
            text.setTextAlign(Paint.Align.LEFT); text.setColor(BlueUi.MUTED);
            boolean nearGoal = false;
            if (goal != null && n > 0 && !Double.isNaN(goal[n - 1])) nearGoal = Math.abs((float) (b - Math.min(goal[n - 1], top) / top * h) - y) < 14 * d;
            c.drawText(referenceLabel + " " + format.value(reference), l + 4 * d, nearGoal ? y + 13 * d : y - 5 * d, text);
        }

        if (selected >= 0 && selected < n) tooltip(c, l + slot * (selected + 0.5f), t, selected);
    }

    private void tooltip(Canvas c, float cx, float t, int i) {
        String head = format.title(i), body = Double.isNaN(values[i]) ? "没有记录" : format.value(values[i]);
        if (goal != null && !Double.isNaN(goal[i]) && !Double.isNaN(values[i])) body += " / 目标 " + format.value(goal[i]);
        text.setTextAlign(Paint.Align.LEFT); text.setTextSize(11 * d);
        float pad = 8 * d, width = Math.max(text.measureText(head), text.measureText(body)) + pad * 2, height = 38 * d;
        float x = Math.max(0, Math.min(getWidth() - width, cx - width / 2)), y = Math.max(0, t - height + 4 * d);
        line.setColor(BlueUi.MUTED); line.setStrokeWidth(1); line.setStyle(Paint.Style.STROKE); c.drawLine(cx, y + height, cx, bottom(), line);
        bar.setColor(BlueUi.CARD); c.drawRoundRect(x, y, x + width, y + height, 6 * d, 6 * d, bar);
        line.setColor(BlueUi.LINE); c.drawRoundRect(x, y, x + width, y + height, 6 * d, 6 * d, line);
        text.setColor(BlueUi.MUTED); c.drawText(head, x + pad, y + 15 * d, text);
        text.setColor(BlueUi.INK); text.setFakeBoldText(true); c.drawText(body, x + pad, y + 31 * d, text); text.setFakeBoldText(false);
        text.setTextSize(10 * d);
    }
}
