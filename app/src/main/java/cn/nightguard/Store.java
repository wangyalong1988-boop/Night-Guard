package cn.nightguard;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.LocalTime;
import java.util.*;
final class Store {
 final Context c; final SharedPreferences p;
 Store(Context c) { this.c=c; p=c.getSharedPreferences("guard",0); resetOnBoot(); }
 void resetOnBoot() { int boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1); if(p.getInt("boot",-2)!=boot) p.edit().putInt("boot",boot).remove("grant").remove("until").remove("request").remove("start").remove("phrase").commit(); }
 boolean night(){ return Policy.night(LocalTime.now()); }
 Set<String> selected(){
  if(p.contains("pending") && System.currentTimeMillis()>=p.getLong("pendingAt",Long.MAX_VALUE)) { Set<String> pending=new HashSet<>(p.getStringSet("pending",Set.of())); p.edit().putStringSet("apps",pending).remove("pending").remove("pendingAt").commit(); }
  return new HashSet<>(p.getStringSet("apps",Set.of()));
 }
 Set<String> desired(){ selected(); return new HashSet<>(p.getStringSet(p.contains("pending")?"pending":"apps",Set.of())); }
 void save(Set<String> apps){ if(night()) { Set<String> immediate=selected(); immediate.addAll(apps); long at=java.time.ZonedDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli(); p.edit().putStringSet("apps",immediate).putStringSet("pending",new HashSet<>(apps)).putLong("pendingAt",at).commit(); } else p.edit().putStringSet("apps",new HashSet<>(apps)).remove("pending").remove("pendingAt").commit(); }
 boolean allowed(String pkg){ return Policy.granted(pkg,p.getString("grant",null),p.getLong("until",0),SystemClock.elapsedRealtime()); }
 void begin(String pkg, String reason){ p.edit().putString("request",pkg).putString("reason",reason).putLong("start",SystemClock.elapsedRealtime()).putString("phrase","我选择暂时使用并在五分钟后停止 " + String.format(java.util.Locale.ROOT,"%06d",new java.security.SecureRandom().nextInt(1000000))).commit(); }
 boolean unlock(String pkg,String phrase){ if(!pkg.equals(p.getString("request","")) || !Policy.ready(p.getLong("start",-1),SystemClock.elapsedRealtime()) || !phrase.equals(p.getString("phrase",""))) return false; p.edit().putString("grant",pkg).putLong("until",SystemClock.elapsedRealtime()+Policy.GRANT_MS).putString("last",java.time.LocalDateTime.now()+" · "+pkg+" · "+p.getString("reason","")).remove("request").remove("start").remove("phrase").commit(); return true; }
}
