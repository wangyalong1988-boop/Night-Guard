package cn.nightguard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class BlueUi {
    static final int INK = 0xff213e60, MUTED = 0xff607e9b, BLUE = 0xff397fc4;
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(Context c, int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (border != 0) d.setStroke(dp(c, 1), border);
        return d;
    }
    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(c, 6), 0, dp(c, 6)); t.setLineSpacing(dp(c, 3), 1);
        return t;
    }
    static Button button(Context c, String label, boolean primary, Runnable action) {
        Button b = new Button(c); b.setText(label); b.setTextSize(15); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : BLUE); b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22397fc4),
                background(c, primary ? BLUE : 0xffedf6ff, primary ? 0 : 0xffd5e6f6, 16), null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 10); b.setLayoutParams(lp); b.setOnClickListener(v -> action.run());
        return b;
    }
    static final class Dial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String time = "", label = ""; private float progress;
        Dial(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
        void update(String value, String caption, float fraction) {
            time = value; label = caption; progress = Math.max(0, Math.min(1, fraction));
            setContentDescription(caption + " " + value); invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(cx,cy)-dp(getContext(),14);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(getContext(),8));
            paint.setColor(0xffdcecf9); canvas.drawCircle(cx,cy,radius,paint);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(BLUE);
            canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,-90,progress*360,false,paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            paint.setColor(INK); paint.setTextSize(dp(getContext(),30));
            canvas.drawText(time,cx,cy+dp(getContext(),3),paint);
            paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(dp(getContext(),12));paint.setColor(MUTED);
            canvas.drawText(label,cx,cy+dp(getContext(),29),paint);
        }
    }
}
