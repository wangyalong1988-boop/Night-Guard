package cn.nightguard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** "Add app lock": every launchable app, the most used first, with today's and the weekly time. */
public class LimitPickerActivity extends Activity {
    private Store store; private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final LruCache<String, Drawable> icons = new LruCache<>(64);
    private List<AppCatalog.Entry> entries = new ArrayList<>(), shown = new ArrayList<>();
    private Map<String, Long> week = new HashMap<>(), today = new HashMap<>(); private Map<String, Integer> locks = new HashMap<>();
    private String query = ""; private TextView empty, count; private final Rows adapter = new Rows();
    private int dp(float v) { return BlueUi.dp(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = new Store(this); BlueUi.use(this);
        if (state != null) query = state.getString("query", "");
        build();
        loader.execute(() -> {
            try { List<AppCatalog.Entry> loaded = AppCatalog.load(this); runOnUiThread(() -> { if (!isDestroyed()) show(loaded); }); }
            catch (RuntimeException e) { runOnUiThread(() -> { if (!isDestroyed()) empty.setText("应用加载失败，请返回后重试"); }); }
        });
    }
    @Override protected void onDestroy() { loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("query", query); }

    private void build() {
        LinearLayout page = BlueUi.column(this); page.setFocusableInTouchMode(true); BlueUi.page(this, page);
        page.addView(BlueUi.navBar(this, "添加应用锁", "选一个容易停不下来的应用"));

        LinearLayout box = BlueUi.row(this); box.setPadding(dp(14), 0, dp(8), 0); box.setBackground(BlueUi.background(this, BlueUi.CARD, 0, 14));
        box.addView(new BlueUi.Glyph(this, "search", BlueUi.MUTED), new LinearLayout.LayoutParams(dp(18), dp(18)));
        EditText search = new EditText(this); search.setSingleLine(); search.setTextSize(15); search.setTextColor(BlueUi.INK); search.setHintTextColor(BlueUi.MUTED);
        search.setHint("搜索应用"); search.setContentDescription("搜索应用"); search.setBackgroundColor(Color.TRANSPARENT); search.setPadding(dp(10), dp(13), dp(4), dp(13));
        search.setText(query); box.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { query = s.toString(); refresh(); }
            public void afterTextChanged(Editable e) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(20), dp(10), dp(20), dp(6)); page.addView(box, lp);
        count = BlueUi.label(this, "正在读取应用…", BlueUi.CAPTION, BlueUi.MUTED, false); count.setPadding(dp(24), dp(8), dp(24), dp(8)); page.addView(count);

        FrameLayout content = new FrameLayout(this);
        ListView list = new ListView(this); list.setDivider(null); list.setClipToPadding(false); list.setPadding(dp(16), 0, dp(16), dp(20));
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = BlueUi.label(this, "正在读取应用…", BlueUi.BODY, BlueUi.MUTED, false); empty.setGravity(Gravity.CENTER); content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void show(List<AppCatalog.Entry> loaded) {
        entries = new ArrayList<>(loaded); week = store.appTotals(7); today = store.appTimes(LocalDate.now()); locks = store.desiredLimits();
        Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        entries.sort((a, b) -> {
            long wa = week.getOrDefault(a.pkg, 0L), wb = week.getOrDefault(b.pkg, 0L);
            if (wa != wb) return Long.compare(wb, wa);
            return collator.compare(a.label, b.label);
        });
        refresh();
    }

    private void refresh() {
        String q = query.trim().toLowerCase(Locale.ROOT); shown = new ArrayList<>();
        for (AppCatalog.Entry e : entries) if (q.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(q) || e.pkg.toLowerCase(Locale.ROOT).contains(q)) shown.add(e);
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (!entries.isEmpty()) { empty.setText("没有找到应用，换个名字试试"); count.setText(q.isEmpty() ? "按最近 7 天用时排序 · 已加锁 " + locks.size() + " 个" : shown.size() + " 个结果"); }
    }

    private final class Rows extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int p) { return shown.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int position, View reuse, ViewGroup parent) {
            Holder h;
            if (reuse == null) {
                h = new Holder(); LinearLayout outer = BlueUi.column(LimitPickerActivity.this); outer.setPadding(0, dp(3), 0, dp(3));
                h.row = BlueUi.row(LimitPickerActivity.this); h.row.setPadding(dp(14), dp(12), dp(12), dp(12)); h.row.setMinimumHeight(dp(68)); outer.addView(h.row);
                h.icon = BlueUi.appIcon(LimitPickerActivity.this, null); h.row.addView(h.icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                LinearLayout labels = BlueUi.column(LimitPickerActivity.this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(14), 0, dp(8), 0); h.row.addView(labels, lp);
                h.name = BlueUi.label(LimitPickerActivity.this, "", BlueUi.BODY, BlueUi.INK, true); h.name.setMaxLines(1); h.name.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(h.name); BlueUi.gap(labels, 5);
                h.meta = BlueUi.label(LimitPickerActivity.this, "", BlueUi.CAPTION, BlueUi.MUTED, false); labels.addView(h.meta);
                h.value = BlueUi.label(LimitPickerActivity.this, "", BlueUi.FOOTNOTE, BlueUi.BLUE, true); h.row.addView(h.value);
                outer.setTag(h); reuse = outer;
            } else h = (Holder) reuse.getTag();
            AppCatalog.Entry e = shown.get(position);
            Drawable icon = e.icon != null ? e.icon : icons.get(e.pkg); if (icon == null) { icon = AppCatalog.icon(LimitPickerActivity.this, e.pkg); icons.put(e.pkg, icon); }
            h.icon.setImageDrawable(icon); h.name.setText(e.label);
            long w = week.getOrDefault(e.pkg, 0L), t = today.getOrDefault(e.pkg, 0L); Integer lock = locks.get(e.pkg);
            h.meta.setText(w < 60000 ? "最近 7 天几乎没用" : "今天 " + Policy.duration(t) + " · 近 7 天日均 " + Policy.duration(w / 7));
            h.value.setText(lock != null ? "每天 " + Policy.duration(lock * 60000L) : "");
            h.value.setVisibility(lock != null ? View.VISIBLE : View.GONE);
            h.row.setBackground(BlueUi.ripple(LimitPickerActivity.this, lock != null ? BlueUi.SELECTED : BlueUi.CARD, 16));
            h.row.setContentDescription(e.label + "，" + h.meta.getText() + (lock != null ? "，已加锁每天" + Policy.duration(lock * 60000L) : ""));
            h.row.setOnClickListener(v -> Locks.edit(LimitPickerActivity.this, store, e.pkg, LimitPickerActivity.this::finish));
            return reuse;
        }
    }
    private static final class Holder { LinearLayout row; ImageView icon; TextView name, meta, value; }
}
