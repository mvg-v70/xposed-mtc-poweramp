package com.mvgv70.xposed_mtc_poweramp;

import com.mvgv70.utils.Utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage
{
  private static Activity playerActivity = null;
  private static String title = "";
  private static String artist = "";
  private static String album = "";
  private static String filename = "";
  private static boolean mPlaying = false;
  private static final String BLUETOOTH_STATE = "connect_state";
  private static final int BLUETOOTH_CALL_OUT = 2;
  private static final int BLUETOOTH_CALL_IN = 3;
  private static final int CMD_API_PAUSE = 2;
  private static final int CMD_API_PLAY_PAUSE = 1;
  private static final int CMD_API_NEXT = 4;
  private static final int CMD_API_PREVIOUS = 5;
  private final static String TAG = "xposed-mtc-poweramp";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // PlayerUIActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        playerActivity = (Activity)param.thisObject;
        // показать версию модуля
        try 
        {
          Context context = playerActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
          Log.d(TAG,"android "+Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {}
        // обработчик com.android.music.playstatusrequest
        IntentFilter qi = new IntentFilter();
        qi.addAction("com.android.music.playstatusrequest");
        playerActivity.registerReceiver(tagsQueryReceiver, qi);
        // информация
        IntentFilter pi = new IntentFilter();
        pi.addAction("com.maxmpz.audioplayer.TRACK_CHANGED");
        pi.addAction("com.maxmpz.audioplayer.STATUS_CHANGED");
        playerActivity.registerReceiver(powerampReceiver, pi);
        // запуск штатных приложений
        IntentFilter mi = new IntentFilter();
        mi.addAction("com.microntek.canbusdisplay");
        playerActivity.registerReceiver(mtcAppReceiver, mi);
        // bluetooth
        IntentFilter bi = new IntentFilter();
        bi.addAction("com.microntek.bt.report");
        playerActivity.registerReceiver(bluetoothReceiver, bi);
        // sleep
        IntentFilter si = new IntentFilter();
        si.addAction("com.microntek.bootcheck");
        playerActivity.registerReceiver(sleepReceiver, si);
        // keys
        IntentFilter ki = new IntentFilter();
        ki.addAction("com.microntek.irkeyDown");
        playerActivity.registerReceiver(keysReceiver, ki);
        Log.d(TAG,"receivers created");
      }
    };
    
    // PlayerUIActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {

       @Override
       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
         Log.d(TAG,"onDestroy");
         // выключаем Receivers
         playerActivity.unregisterReceiver(tagsQueryReceiver);
         playerActivity.unregisterReceiver(powerampReceiver);
         playerActivity.unregisterReceiver(mtcAppReceiver);
         playerActivity.unregisterReceiver(bluetoothReceiver);
         playerActivity.unregisterReceiver(sleepReceiver);
         playerActivity.unregisterReceiver(keysReceiver);
         title = "";
         album = "";
         artist = "";
         filename = "";
         playerActivity = null;
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("com.maxmpz.audioplayer")) return;
    Log.d(TAG,"com.maxxt.pcradio");
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Utils.findAndHookMethod("com.maxmpz.audioplayer.PlayerUIActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    Utils.findAndHookMethod("com.maxmpz.audioplayer.PlayerUIActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.maxmpz.audioplayer hook OK");
  }
  
  // отправка информации о воспроизведении
  private void sendNotifyIntent(Context context)
  {
    Intent intent = new Intent("com.android.music.playstatechanged");
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, title);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
    intent.putExtra("filename", filename);
    intent.putExtra("source", "poweramp");
    context.sendBroadcast(intent);
  }
  
  // обработчик com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      // отправить информацию
      Log.d(TAG,"PowerAmp: tags query receiver");
      if (mPlaying) sendNotifyIntent(context);
    }
  };
  
  // com.maxmpz.audioplayer.TRACK_CHANGED & com.maxmpz.audioplayer.STATUS_CHANGED
  private BroadcastReceiver powerampReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction();
      Log.d(TAG,"PowerAmp: track receiver: "+action);
      if (action.equals("com.maxmpz.audioplayer.TRACK_CHANGED"))
      {
        Bundle mCurrentTrack = intent.getBundleExtra("track");
        // сохраним информацию о треке
        title = mCurrentTrack.getString("title");
        artist = mCurrentTrack.getString("artist");
        album = mCurrentTrack.getString("album");
        filename = mCurrentTrack.getString("path");
        // 
        Log.d(TAG,"title="+title);
        Log.d(TAG,"album="+album);
        Log.d(TAG,"artist="+artist);
        Log.d(TAG,"filename="+filename);
        sendNotifyIntent(context);
      }
      else if (action.equals("com.maxmpz.audioplayer.STATUS_CHANGED"))
      {
        if (intent.hasExtra("paused"))
        {
          // определяем состояние проигрывания
          mPlaying = !intent.getBooleanExtra("paused",false);
          Log.d(TAG,"mPlaying="+mPlaying);
          if (mPlaying)
          {
            Log.d(TAG,"send com.microntek.bootcheck");
            // разослать интент о закрытии штатных приложений
            Intent mtcIntent = new Intent("com.microntek.bootcheck");
            mtcIntent.putExtra("class", playerActivity.getPackageName());
            playerActivity.sendBroadcast(mtcIntent);
          }
        }
      }
    }
  };
  
  // команда API PowerAmp
  private void commandPowerAmp(int cmd)
  {
    Log.d(TAG,"send com.maxmpz.audioplayer.API_COMMAND, cmd="+cmd);
    Intent pintent = new Intent("com.maxmpz.audioplayer.API_COMMAND");
    pintent.setComponent(new ComponentName("com.maxmpz.audioplayer","com.maxmpz.audioplayer.player.PlayerService"));
    pintent.putExtra("cmd",cmd);
    playerActivity.startService(pintent);
  }
  
  // запуск штатных приложений
  private BroadcastReceiver mtcAppReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String type = intent.getStringExtra("type");
      Log.d(TAG,"com.microntek.canbusdisplay: type="+type);
      if (type.endsWith("-on"))
      {
        if (mPlaying)
        {
          // выключим PCRadio
          commandPowerAmp(CMD_API_PAUSE);
        }
      }
    }
  };
  
  // входящий или исходящий звонок
  private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      int state = intent.getIntExtra(BLUETOOTH_STATE,-1);
      Log.d(TAG,"com.microntek.bt.report: state="+state);
      if (((state == BLUETOOTH_CALL_IN) || (state == BLUETOOTH_CALL_OUT)) && mPlaying)
      {
        // выключим PCRadio
        commandPowerAmp(CMD_API_PAUSE);
      }
    }
  };
  
  // уход ГУ в сон
  private BroadcastReceiver sleepReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String iclass = intent.getStringExtra("class");
      Log.d(TAG,"com.microntek.bootcheck: class="+iclass);
      if (iclass.equals("poweroff") && mPlaying)
      {
        // выключим PCRadio
        commandPowerAmp(CMD_API_PAUSE);
      }
    }
  };
  
  // нажатие кнопок
  private BroadcastReceiver keysReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", 0);
      Log.d(TAG,"com.microntek.irkeyDown: keyCoce="+keyCode);
      // TODO: или на экране или mPlaying
      if (playerActivity.hasWindowFocus() || mPlaying)
      {
        // если PoerAmp на экране или в режиме проигрывания
        if (keyCode == 3)
          commandPowerAmp(CMD_API_PLAY_PAUSE);
        else if (keyCode == 46)
          commandPowerAmp(CMD_API_NEXT);
        else if (keyCode == 45)
          commandPowerAmp(CMD_API_PREVIOUS);
      }
    }
  };

}
