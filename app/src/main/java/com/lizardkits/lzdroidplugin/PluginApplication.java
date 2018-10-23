package com.lizardkits.lzdroidplugin;

import android.app.Application;
import android.content.Context;

import com.lizardkits.droidplugin.LzDroidPlugin;


public class PluginApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LzDroidPlugin.initOnCreate(this, "com.lqr.wechat.pluginauthority");
    }

    @Override
    protected void attachBaseContext(Context base) {
        LzDroidPlugin.initOnAttachBaseContext(base);
        super.attachBaseContext(base);
    }
}
