package com.lizardkits.droidplugin;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import com.lizardkits.droidplugin.callback.IPluginCallback;
import com.morgoo.droidplugin.PluginHelper;
import com.morgoo.droidplugin.PluginManagerService;
import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.helper.compat.PackageManagerCompat;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创建者：GitLqr
 * 描述：DroidPlugin使用封装实现类
 */
public class LzDroidPlugin implements IPlugin {

    private static final String TAG = "LzDroidPlugin";
    private static Context sContext;

    private LzDroidPlugin() {
        if (sContext == null) {
            throw new RuntimeException("Please Init LzDroidPlugin In Your Application");
        }
    }

    private static final class Holder {
        private static final LzDroidPlugin INSTANCE = new LzDroidPlugin();
    }

    public static LzDroidPlugin getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 在 项目自定义Application的 super.onCreate() 后调用。
     *
     * @param application         application
     * @param pluginAuthorityName 插件主机名（不同项目必须使用不同的主机名，否则无法在同个设备上并存）
     *                            如：
     *                            public void onCreate() {
     *                            super.onCreate();
     *                            LzDroidPlugin.initOnCreate(this, "com.lqr.wechat.pluginauthority");
     *                            }
     */
    public static void initOnCreate(Application application, String pluginAuthorityName) {
        sContext = application;
        PluginManager.STUB_AUTHORITY_NAME = pluginAuthorityName;
        PluginHelper.getInstance().applicationOnCreate(application.getBaseContext());
    }

    /**
     * 在 项目自定义Application的 super.attachBaseContext() 前调用。
     *
     * @param baseContext context
     *                    <p>
     *                    如：
     *                    protected void attachBaseContext(Context base) {
     *                    LzDroidPlugin.initOnAttachBaseContext(base);
     *                    super.attachBaseContext(base);
     *                    }
     */
    public static void initOnAttachBaseContext(Context baseContext) {
        PluginHelper.getInstance().applicationAttachBaseContext(baseContext);
    }


    private Map<String, IPluginCallback> mWaitInstallCallbackMap = new ConcurrentHashMap<>();


    @Override
    public void nativeInstall(String filePath) {
        File file = new File(filePath);
        if (null != file && file.exists()) {
            // apk 文件不是在SD卡目录上，如 data/data/xxx/files/youxi.apk
            if (!(Environment.MEDIA_MOUNTED.equalsIgnoreCase(Environment.getExternalStorageState()))
                    && filePath.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())
                    ) {
                Log.e(TAG, "apk exist on private dir, invoke chomd. ");
                chmod(file);
            }
        }
    }

    @Override
    public void nativeUninstall(String packageName) {
        Uri uri = Uri.parse("package:" + packageName);
        Intent intent = new Intent(Intent.ACTION_DELETE, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(intent);
    }

    @Override
    public void pluginInstall(String filePath, IPluginCallback callback) {
        mWaitInstallCallbackMap.put(filePath, callback);
        try {
            if (isPluginServiceRunning()) {
                File apkFile = new File(filePath);
                if (!apkFile.exists()) {
                    onPluginResult(filePath, PluginErrorCode.APK_FILE_NOT_FOUND);
                    Log.e(TAG, "install plugin error : apk file not found");
                } else {
                    int code = PluginManager.getInstance().installPackage(filePath, PackageManagerCompat.INSTALL_REPLACE_EXISTING);
                    Log.e(TAG, "install plugin smoothly : code = " + code);
                    onPluginResult(filePath, code);
                }
            } else {
                startPluginService();
                Log.e(TAG, "install plugin smoothly : start and waiting for service running to install");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            onPluginResult(filePath, PluginErrorCode.LOCAL_ERROR_HAPPEN);
            Log.e(TAG, "install plugin error : " + e.getMessage());
        }
    }

    private void onPluginResult(String filePath, int code) {
        IPluginCallback callback = mWaitInstallCallbackMap.get(filePath);
        if (null != callback) {
            callback.onResult(code, filePath);
            mWaitInstallCallbackMap.remove(filePath);
        }
    }

    @Override
    public void pluginUninstall(String packageName) {
        try {
            PluginManager.getInstance().deletePackage(packageName, 0);
            Log.e(TAG, "uninstall plugin smoothly");
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "uninstall plugin error : " + e.getMessage());
        }
    }

    @Override
    public Map<String, Integer> getPluginInstalled() {
        return null;
    }

    @Override
    public boolean isPluginInstalled(String packageName) {
        Map<String, Integer> installed = getPluginInstalled();
        if (installed.containsKey(packageName)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isNativeInstalled(String packageName) {
        final PackageManager packageManager = sContext.getPackageManager();
        List<PackageInfo> infos = packageManager.getInstalledPackages(0);
        if (null != infos && infos.size() > 0) {
            for (PackageInfo info : infos) {
                if (info.packageName.equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void launchApp(String packageName, Map<String, Object> params) {
        Intent intent = sContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (null != intent) {
            fitIntent(intent, params);
            sContext.startActivity(intent);
        }
    }

    @Override
    public void launchApp(String packageName, String activityName, Map<String, Object> params) {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName(packageName, activityName);
        intent.setComponent(componentName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fitIntent(intent, params);
        sContext.startActivity(intent);
    }

    @Override
    public void killAllPluginProcesses() {
        try {
            List<PackageInfo> packageInfos = PluginManager.getInstance().getInstalledPackages(0);
            if (null != packageInfos && packageInfos.size() > 0) {
                for (PackageInfo packageInfo : packageInfos) {
                    PluginManager.getInstance().forceStopPackage(packageInfo.packageName);
                    PluginManager.getInstance().killBackgroundProcesses(packageInfo.packageName);
                    PluginManager.getInstance().killApplicationProcess(packageInfo.packageName);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void killPluginProcess(String packageName) {
        try {
            List<PackageInfo> packagePatchInfos = PluginManager.getInstance().getInstalledPackages(0);
            if (packagePatchInfos != null && packagePatchInfos.size() > 0) {
                for (PackageInfo packagePatchInfo : packagePatchInfos) {
                    if (packagePatchInfo.packageName.equalsIgnoreCase(packageName)) {
                        PluginManager.getInstance().forceStopPackage(packagePatchInfo.packageName);
                        PluginManager.getInstance().killBackgroundProcesses(packagePatchInfo.packageName);
                        PluginManager.getInstance().killApplicationProcess(packagePatchInfo.packageName);
                        break;
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exitPluginFramework() {
        // 关闭所有的插件进程
        killAllPluginProcesses();
        // 关闭DroidPlugin服务，否则宿主apk会驻留进程
        stopPluginServer();
    }

    /**
     * 修改文件访问权限
     *
     * @param file
     */
    private void chmod(File file) {
        try {
            String[] command = {"chmod", "777", file.getAbsolutePath()};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.start();
            if (null != file.getParentFile()) {
                chmod(file.getParentFile());
            }
        } catch (IOException e) {
            Log.e(TAG, "chmod fail : " + e.getMessage());
        }
    }

    public boolean isPluginServiceRunning() {
        if (PluginManager.getInstance().isConnected()) {
            Log.e(TAG, "-------plugin service is running");
            return true;
        } else {
            Log.e(TAG, "-------plugin service isn't running");
            startPluginService();
            return false;
        }
    }

    public void startPluginService() {
        PluginManager.getInstance().addServiceConnection(mServiceConnection);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "plugin service connected");
            if (mWaitInstallCallbackMap != null && mWaitInstallCallbackMap.size() > 0) {
                for (Map.Entry<String, IPluginCallback> entry : mWaitInstallCallbackMap.entrySet()) {
                    pluginInstall(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "plugin service disconnected");
            PluginManager.getInstance().removeServiceConnection(mServiceConnection);
        }
    };

    private void stopPluginServer() {
        Intent intent = new Intent();
        intent.setClass(PluginManager.getInstance().getHostContext(), PluginManagerService.class);
        sContext.getApplicationContext().stopService(intent);
    }

    /**
     * 填充Intent，将Map参数存进Intent。
     */
    private Intent fitIntent(Intent intent, Map<String, Object> params) {
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() instanceof Bundle) {
                    intent.putExtra(entry.getKey(), (Bundle) entry.getValue());
                } else if (entry.getValue() instanceof Parcelable) {
                    intent.putExtra(entry.getKey(), (Parcelable) entry.getValue());
                } else if (entry.getValue() instanceof Parcelable[]) {
                    intent.putExtra(entry.getKey(), (Parcelable[]) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    intent.putExtra(entry.getKey(), (boolean) entry.getValue());
                } else if (entry.getValue() instanceof boolean[]) {
                    intent.putExtra(entry.getKey(), (boolean[]) entry.getValue());
                } else if (entry.getValue() instanceof Byte) {
                    intent.putExtra(entry.getKey(), (byte) entry.getValue());
                } else if (entry.getValue() instanceof byte[]) {
                    intent.putExtra(entry.getKey(), (byte[]) entry.getValue());
                } else if (entry.getValue() instanceof Character) {
                    intent.putExtra(entry.getKey(), (char) entry.getValue());
                } else if (entry.getValue() instanceof char[]) {
                    intent.putExtra(entry.getKey(), (char[]) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    intent.putExtra(entry.getKey(), (double) entry.getValue());
                } else if (entry.getValue() instanceof double[]) {
                    intent.putExtra(entry.getKey(), (double[]) entry.getValue());
                } else if (entry.getValue() instanceof Float) {
                    intent.putExtra(entry.getKey(), (float) entry.getValue());
                } else if (entry.getValue() instanceof float[]) {
                    intent.putExtra(entry.getKey(), (float[]) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    intent.putExtra(entry.getKey(), (int) entry.getValue());
                } else if (entry.getValue() instanceof int[]) {
                    intent.putExtra(entry.getKey(), (int[]) entry.getValue());
                } else if (entry.getValue() instanceof Serializable) {
                    intent.putExtra(entry.getKey(), (Serializable) entry.getValue());
                } else if (entry.getValue() instanceof CharSequence) {
                    intent.putExtra(entry.getKey(), (CharSequence) entry.getValue());
                } else if (entry.getValue() instanceof CharSequence[]) {
                    intent.putExtra(entry.getKey(), (CharSequence[]) entry.getValue());
                } else if (entry.getValue() instanceof String) {
                    intent.putExtra(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof String[]) {
                    intent.putExtra(entry.getKey(), (String[]) entry.getValue());
                }
            }
        }
        return intent;
    }
}
