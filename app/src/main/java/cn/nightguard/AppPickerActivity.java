package cn.nightguard;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.util.*;
import java.util.concurrent.*;

public class AppPickerActivity extends Activity {
    private Store store;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final LruCache<String,Drawable> icons=new LruCache<>(64);
    private List<AppCatalog.Entry> entries=new ArrayList<>(),filtered=new ArrayList<>();
    AppSelection selection;
    private Set<String> restored;
    private String filter=AppCategory.ALL,query="";
    private boolean onlySelected=false;
    private ListView list;private EditText search;private LinearLayout chips,filters;
    private TextView summary,results,empty,selectedTab;private Button save;
    private final Rows adapter=new Rows();
    private int dp(float v){return BlueUi.dp(this,v);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);
        if(state!=null){restored=new HashSet<>(state.getStringArrayList("draft"));filter=state.getString("filter",AppCategory.ALL);query=state.getString("query","");onlySelected=state.getBoolean("selected");}
        build();
        loader.execute(()->{try{List<AppCatalog.Entry> loaded=AppCatalog.load(this);runOnUiThread(()->{if(!isDestroyed())showEntries(loaded);});}
            catch(RuntimeException e){runOnUiThread(()->{if(!isDestroyed()){empty.setText("应用加载失败，请返回后重试");empty.setVisibility(View.VISIBLE);}});}});
    }
    @Override protected void onResume(){super.onResume();GuardService.settingsVisible=true;}
    @Override protected void onPause(){GuardService.settingsVisible=false;super.onPause();}
    @Override protected void onDestroy(){loader.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putStringArrayList("draft",new ArrayList<>(selection!=null?selection.snapshot():restored!=null?restored:store.desired()));
        out.putString("filter",filter);out.putString("query",query);out.putBoolean("selected",onlySelected);
    }
    private void build(){
        LinearLayout page=BlueUi.column(this);page.setFocusableInTouchMode(true);BlueUi.page(this,page);
        LinearLayout header=BlueUi.row(this);header.setPadding(dp(10),dp(12),dp(20),dp(8));
        header.addView(BlueUi.iconButton(this,"back","返回",this::leave));
        LinearLayout titles=BlueUi.column(this);titles.addView(BlueUi.label(this,"限制应用",23,BlueUi.INK,true));BlueUi.gap(titles,5);
        titles.addView(BlueUi.label(this,"按类别挑选，夜间自动限制",12,BlueUi.MUTED,false));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        LinearLayout searchBox=BlueUi.row(this);searchBox.setPadding(dp(14),0,dp(8),0);searchBox.setBackground(BlueUi.background(this,Color.WHITE,BlueUi.LINE,12));
        searchBox.addView(new BlueUi.Glyph(this,"search",BlueUi.MUTED),new LinearLayout.LayoutParams(dp(19),dp(19)));
        search=new EditText(this);search.setSingleLine();search.setTextSize(15);search.setTextColor(BlueUi.INK);search.setHintTextColor(BlueUi.MUTED);
        search.setHint("搜索应用名称");search.setContentDescription("搜索应用");search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(10),dp(12),0,dp(12));
        searchBox.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        searchBox.addView(BlueUi.link(this,"清除",()->search.setText("")));
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,-2);searchLp.setMargins(dp(20),dp(12),dp(20),dp(8));page.addView(searchBox,searchLp);
        search.setText(query);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();refresh();}public void afterTextChanged(Editable e){}});
        filters=BlueUi.column(this);page.addView(filters);
        HorizontalScrollView categories=new HorizontalScrollView(this);categories.setHorizontalScrollBarEnabled(false);
        chips=BlueUi.row(this);chips.setPadding(dp(20),dp(4),dp(12),dp(10));categories.addView(chips);filters.addView(categories);
        LinearLayout tools=BlueUi.row(this);tools.setPadding(dp(20),0,dp(12),dp(4));
        selectedTab=BlueUi.link(this,"只看已选",()->{onlySelected=!onlySelected;refresh();});tools.addView(selectedTab);
        View stretch=new View(this);tools.addView(stretch,new LinearLayout.LayoutParams(0,1,1));
        tools.addView(BlueUi.link(this,"全选结果",()->bulk(true)));tools.addView(BlueUi.link(this,"清空结果",()->bulk(false)));filters.addView(tools);
        results=BlueUi.label(this,"正在读取应用…",12,BlueUi.MUTED,false);results.setPadding(dp(24),dp(4),dp(24),dp(10));filters.addView(results);
        FrameLayout content=new FrameLayout(this);list=new ListView(this);list.setDivider(null);list.setClipToPadding(false);list.setPadding(dp(16),0,dp(16),dp(12));list.setAdapter(adapter);
        content.addView(list,new FrameLayout.LayoutParams(-1,-1));empty=BlueUi.label(this,"正在读取应用…",15,BlueUi.MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),0,dp(24),0);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=BlueUi.column(this);footer.setBackgroundColor(Color.WHITE);footer.setPadding(dp(20),dp(12),dp(20),dp(16));
        summary=BlueUi.label(this,"正在读取应用…",13,BlueUi.INK,true);footer.addView(summary);BlueUi.gap(footer,5);
        footer.addView(BlueUi.label(this,store.night()?"夜间减少限制，将在08:00后生效":"浏览器自动限制；电话、闹钟等核心应用已保留",11,BlueUi.MUTED,false));BlueUi.gap(footer,12);
        save=BlueUi.button(this,"保存限制清单",true,this::save);save.setEnabled(false);footer.addView(save,new LinearLayout.LayoutParams(-1,-2));page.addView(footer);
        page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);filters.setVisibility(insets.isVisible(WindowInsets.Type.ime())?View.GONE:View.VISIBLE);return WindowInsets.CONSUMED;});
    }
    /** Production data-binding seam also used by emulator UI tests with explicit fixtures. */
    void showEntries(List<AppCatalog.Entry> data){
        entries=new ArrayList<>(data);Set<String> mandatory=new HashSet<>();for(AppCatalog.Entry e:entries)if(e.browser)mandatory.add(e.pkg);
        Set<String> baseline=store.desired();selection=new AppSelection(baseline,restored!=null?restored:baseline,mandatory);
        rebuildChips();refresh();save.setEnabled(true);
    }
    private void rebuildChips(){
        chips.removeAllViews();List<String> groups=new ArrayList<>();groups.add(AppCategory.ALL);groups.addAll(AppCategory.CATEGORIES);
        for(String group:groups){
            int count=0;for(AppCatalog.Entry e:entries)if(group.equals(AppCategory.ALL)||group.equals(e.category))count++;
            TextView chip=BlueUi.label(this,group+" "+count,13,group.equals(filter)?Color.WHITE:BlueUi.MUTED,group.equals(filter));
            chip.setGravity(Gravity.CENTER);chip.setMinHeight(dp(38));chip.setPadding(dp(14),dp(8),dp(14),dp(8));
            chip.setBackground(BlueUi.ripple(this,group.equals(filter)?BlueUi.INK:Color.WHITE,10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(8);chips.addView(chip,lp);
            chip.setOnClickListener(v->{filter=group;rebuildChips();refresh();list.setSelection(0);});
        }
    }
    private void refresh(){
        if(selection==null)return;filtered=new ArrayList<>();int unknown=0;Set<String> installed=new HashSet<>();
        for(AppCatalog.Entry e:entries){installed.add(e.pkg);if(e.category.equals(AppCategory.OTHER))unknown++;
            if(AppCategory.matches(e.label,e.pkg,e.category,filter,query)&&(!onlySelected||selection.checked(e.pkg)))filtered.add(e);}
        adapter.notifyDataSetChanged();empty.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(query.isEmpty()?(onlySelected?"此分类还没有选中的应用":"此分类暂无应用"):"没有找到应用\n试试其他名称，或切换到“全部”");
        results.setText(filtered.size()+" 个结果"+(unknown>0?" · "+unknown+" 个待分类，可点右侧菜单调整":" · 点击整行即可选择"));
        summary.setText("已选 "+selection.visibleCount(installed)+" 个应用（含自动限制的浏览器）");
        selectedTab.setText(onlySelected?"✓ 只看已选":"只看已选");selectedTab.setTextColor(onlySelected?BlueUi.BLUE:BlueUi.MUTED);
    }
    private void bulk(boolean checked){if(selection==null)return;List<String> pkgs=new ArrayList<>();for(AppCatalog.Entry e:filtered)pkgs.add(e.pkg);selection.setAll(pkgs,checked);refresh();}
    private void save(){if(selection==null)return;store.save(selection.snapshot());Toast.makeText(this,store.night()?"已保存，减少的限制将在08:00后生效":"限制清单已保存",Toast.LENGTH_SHORT).show();finish();}
    private void leave(){
        if(selection!=null&&selection.dirty())new AlertDialog.Builder(this).setTitle("保存应用选择？").setMessage("这次选择尚未保存。")
            .setPositiveButton("保存",(d,w)->save()).setNegativeButton("放弃修改",(d,w)->finish()).setNeutralButton("继续选择",null).show();else finish();
    }
    @Override public void onBackPressed(){leave();}
    private void category(AppCatalog.Entry entry){
        List<String> choices=new ArrayList<>(AppCategory.CATEGORIES);choices.remove(AppCategory.BROWSER);
        new AlertDialog.Builder(this).setTitle("调整“"+entry.label+"”的分类").setSingleChoiceItems(choices.toArray(new String[0]),choices.indexOf(entry.category),(dialog,which)->{
            entry.category=choices.get(which);store.p.edit().putString("category:"+entry.pkg,entry.category).apply();dialog.dismiss();rebuildChips();refresh();
        }).setNeutralButton("应用信息",(d,w)->new AlertDialog.Builder(this).setTitle(entry.label).setMessage(entry.pkg).setPositiveButton("知道了",null).show()).setNegativeButton("取消",null).show();
    }
    private final class Rows extends BaseAdapter {
        public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View reusable,android.view.ViewGroup parent){
            Holder h;
            if(reusable==null){h=new Holder();LinearLayout outer=BlueUi.column(AppPickerActivity.this);outer.setPadding(dp(4),dp(4),dp(4),dp(4));
                h.row=BlueUi.row(AppPickerActivity.this);h.row.setPadding(dp(14),dp(14),dp(6),dp(14));h.row.setMinimumHeight(dp(80));outer.addView(h.row);
                h.icon=new ImageView(AppPickerActivity.this);h.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.row.addView(h.icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
                LinearLayout labels=BlueUi.column(AppPickerActivity.this);LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);labelLp.setMargins(dp(14),0,dp(4),0);h.row.addView(labels,labelLp);
                h.name=BlueUi.label(AppPickerActivity.this,"",16,BlueUi.INK,true);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(h.name);BlueUi.gap(labels,6);
                h.meta=BlueUi.label(AppPickerActivity.this,"",12,BlueUi.MUTED,false);labels.addView(h.meta);
                h.more=BlueUi.iconButton(AppPickerActivity.this,"more","调整分类",()->{});h.row.addView(h.more);
                h.check=new CheckBox(AppPickerActivity.this);h.check.setClickable(false);h.check.setFocusable(false);h.check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);h.check.setButtonTintList(android.content.res.ColorStateList.valueOf(BlueUi.BLUE));h.row.addView(h.check,new LinearLayout.LayoutParams(dp(40),dp(48)));
                outer.setTag(h);reusable=outer;
            }else h=(Holder)reusable.getTag();
            AppCatalog.Entry e=filtered.get(position);boolean checked=selection.checked(e.pkg);
            Drawable icon=e.icon!=null?e.icon:icons.get(e.pkg);if(icon==null){icon=AppCatalog.icon(AppPickerActivity.this,e.pkg);icons.put(e.pkg,icon);}h.icon.setImageDrawable(icon);
            h.name.setText(e.label);h.meta.setText(e.browser?"浏览器 · 自动限制":e.category);h.check.setChecked(checked);h.check.setEnabled(!e.browser);
            h.row.setBackground(BlueUi.ripple(AppPickerActivity.this,checked?0xffe5effa:Color.WHITE,14));
            h.row.setContentDescription(e.label+"，"+h.meta.getText()+"，"+(checked?"已选":"未选"));
            h.row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo node){super.onInitializeAccessibilityNodeInfo(v,node);node.setCheckable(!e.browser);node.setChecked(checked);}});
            h.row.setOnClickListener(v->{if(e.browser)Toast.makeText(AppPickerActivity.this,"浏览器在夜间自动限制，无需手动选择",Toast.LENGTH_SHORT).show();else{selection.set(e.pkg,!selection.checked(e.pkg));refresh();}});
            h.more.setVisibility(e.browser?View.INVISIBLE:View.VISIBLE);h.more.setContentDescription("调整"+e.label+"分类");h.more.setOnClickListener(v->category(e));return reusable;
        }
    }
    private static final class Holder {LinearLayout row;ImageView icon;TextView name,meta;CheckBox check;View more;}
}
