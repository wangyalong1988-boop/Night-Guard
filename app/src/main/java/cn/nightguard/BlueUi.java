package cn.nightguard;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared spacing, typography and line icons for the native interface. */
final class BlueUi {
    static final int INK=0xff1c314c, MUTED=0xff65768c, BLUE=0xff396fa8;
    static final int PAGE=0xfff2f6fb, LINE=0xffe5ecf3, PALE=0xffe4effb, WHITE=Color.WHITE;
    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static GradientDrawable background(Context c,int color,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(border!=0)d.setStroke(dp(c,1),border);return d;
    }
    static RippleDrawable ripple(Context c,int color,int radius){
        return new RippleDrawable(ColorStateList.valueOf(0x16396fa8),background(c,color,0,radius),null);
    }
    static TextView label(Context c,String value,int size,int color,boolean medium){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setIncludeFontPadding(false);t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setLineSpacing(dp(c,3),1);return t;
    }
    static TextView text(Context c,String value,int size,int color){return label(c,value,size,color,false);}
    static void gap(LinearLayout parent,int height){View space=new View(parent.getContext());parent.addView(space,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));}
    static void divider(LinearLayout parent){View v=new View(parent.getContext());v.setBackgroundColor(LINE);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(parent.getContext(),1)));}
    static void page(Activity activity,View body){
        body.setBackgroundColor(PAGE);activity.setContentView(body);
        body.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;
        });
    }
    static Button button(Context c,String value,boolean primary,Runnable action){
        Button b=new Button(c);b.setText(value);b.setTextSize(15);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?WHITE:BLUE);
        b.setMinHeight(dp(c,50));b.setMinimumHeight(dp(c,50));b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        b.setBackgroundTintList(null);b.setBackground(ripple(c,primary?BLUE:PALE,12));
        b.setOnClickListener(v->action.run());return b;
    }
    static TextView link(Context c,String value,Runnable action){
        TextView t=label(c,value,13,BLUE,true);t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c,44));t.setPadding(dp(c,8),0,dp(c,8),0);
        t.setBackground(ripple(c,Color.TRANSPARENT,8));t.setOnClickListener(v->action.run());return t;
    }
    static View iconButton(Context c,String kind,String description,Runnable action){
        FrameLayout box=new FrameLayout(c);box.setContentDescription(description);box.setBackground(ripple(c,Color.TRANSPARENT,12));
        Glyph glyph=new Glyph(c,kind,INK);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(c,22),dp(c,22),Gravity.CENTER);
        box.addView(glyph,p);box.setOnClickListener(v->action.run());box.setLayoutParams(new LinearLayout.LayoutParams(dp(c,44),dp(c,44)));return box;
    }
    static final class Glyph extends View {
        private final String kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int color;
        Glyph(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            switch(kind){
                case "back":path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);c.drawPath(path,p);break;
                case "next":path.moveTo(9,6);path.lineTo(15,12);path.lineTo(9,18);c.drawPath(path,p);break;
                case "search":c.drawCircle(10.5f,10.5f,6.5f,p);c.drawLine(16,16,21,21,p);break;
                case "check":path.moveTo(5,12);path.lineTo(10,17);path.lineTo(19,7);c.drawPath(path,p);break;
                case "moon":path.moveTo(18,15);path.cubicTo(9,18,5,10,10,4);path.cubicTo(-1,9,5,25,17,19);path.lineTo(20,16);path.close();c.drawPath(path,p);break;
                case "grid":for(int x=4;x<=14;x+=10)for(int y=4;y<=14;y+=10)c.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,p);break;
                case "clock":c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,16,14,p);break;
                case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.5f,p);break;
                case "lock":c.drawRoundRect(5,10,19,21,2,2,p);c.drawArc(8,2,16,16,180,180,false,p);c.drawLine(12,14,12,17,p);break;
                case "bell":path.moveTo(5,17);path.lineTo(7,14);path.lineTo(7,9);path.cubicTo(7,2,17,2,17,9);path.lineTo(17,14);path.lineTo(19,17);path.close();c.drawPath(path,p);c.drawArc(9,17,15,22,0,180,false,p);break;
                default:path.moveTo(12,3);path.lineTo(20,6);path.lineTo(20,12);path.cubicTo(20,17,15,20,12,22);path.cubicTo(9,20,4,17,4,12);path.lineTo(4,6);path.close();c.drawPath(path,p);break;
            }c.restore();
        }
    }
}
