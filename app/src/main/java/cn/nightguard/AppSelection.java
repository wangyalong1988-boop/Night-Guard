package cn.nightguard;

import java.util.*;

/** Draft selection is independent of the current search/category filter. */
final class AppSelection {
    private final Set<String> original,chosen,mandatory;
    AppSelection(Set<String> baseline,Set<String> draft,Set<String> mandatory) {
        this.mandatory=new HashSet<>(mandatory);
        original=new HashSet<>(baseline);original.addAll(mandatory);
        chosen=new HashSet<>(draft);chosen.addAll(mandatory);
    }
    boolean checked(String pkg){return chosen.contains(pkg);}
    void set(String pkg,boolean selected){if(selected||mandatory.contains(pkg))chosen.add(pkg);else chosen.remove(pkg);}
    void setAll(Collection<String> packages,boolean selected){for(String pkg:packages)set(pkg,selected);}
    Set<String> snapshot(){return new HashSet<>(chosen);}
    boolean dirty(){return !chosen.equals(original);}
    int visibleCount(Collection<String> installed){int n=0;for(String pkg:installed)if(checked(pkg))n++;return n;}
}
