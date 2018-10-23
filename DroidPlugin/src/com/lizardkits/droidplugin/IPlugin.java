package com.lizardkits.droidplugin;

import com.lizardkits.droidplugin.callback.IPluginCallback;

import java.util.Map;

/**
 * 创建者：GitLqr
 * 描述：常规插件操作接口
 */
public interface IPlugin {

    /**
     * 系统安装 apk
     *
     * @param filePath apk绝对路径
     */
    void nativeInstall(String filePath);

    /**
     * 系统卸载
     *
     * @param packageName apk包名
     */
    void nativeUninstall(String packageName);

    /**
     * 插件化安装
     *
     * @param filePath apk绝对路径
     * @param callback 结果回调接口
     */
    void pluginInstall(String filePath, IPluginCallback callback);

    /**
     * 插件化卸载
     *
     * @param packageName apk包名
     */
    void pluginUninstall(String packageName);

    /**
     * 获取所有插件化安装的apk信息
     *
     * @return [packageName，versionCode]
     */
    Map<String, Integer> getPluginInstalled();

    /**
     * 查询插件是否安装
     *
     * @param packageName apk包名
     */
    boolean isPluginInstalled(String packageName);

    /**
     * 查询app是否已经系统安装（以插件形式安装也会返回true）
     *
     * @param packageName apk 包名
     */
    boolean isNativeInstalled(String packageName);

    /**
     * 启动App
     *
     * @param packageName apk包名
     * @param params      intent参数
     */
    void launchApp(String packageName, Map<String, Object> params);

    /**
     * 启动App
     *
     * @param packageName  apk包名
     * @param activityName activity类名
     * @param params       intent参数
     */
    void launchApp(String packageName, String activityName, Map<String, Object> params);

    /**
     * 杀掉所有插件进程
     */
    void killAllPluginProcesses();

    /**
     * 杀掉指定插件进程
     *
     * @param packageName apk包名
     */
    void killPluginProcess(String packageName);

    /**
     * 完全退出插件化框架
     * <p>
     * 用于完全退出宿主进程
     */
    void exitPluginFramework();
}
