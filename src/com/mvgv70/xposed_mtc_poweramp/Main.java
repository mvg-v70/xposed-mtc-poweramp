package com.mvgv70.xposed_mtc_poweramp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage
{
  private static Activity playerActivity = null;
  private static String title = "";
  private static String artist = "";
  private static String album = "";
  private static String filename = "";
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
          Activity launcher = (Activity)param.thisObject; 
          Context context = launcher.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
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
    XposedHelpers.findAndHookMethod("com.maxmpz.audioplayer.PlayerUIActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.maxmpz.audioplayer.PlayerUIActivity", lpparam.classLoader, "onDestroy", onDestroy);
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
      // TODO: только в режиме проигрывания
      sendNotifyIntent(context);
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
        String status = intent.getStringExtra("status");
        Log.d(TAG,"status="+status);
        Boolean pasued = intent.getBooleanExtra("paused", false);
        Log.d(TAG,"pasued="+pasued);
        // TODO: послать уведомление если play
      }
    }
  };
  
}
