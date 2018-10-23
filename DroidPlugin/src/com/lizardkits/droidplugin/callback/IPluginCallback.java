package com.lizardkits.droidplugin.callback;

/**
 * 创建者：GitLqr
 * 描述：插件 安装/卸载 回调
 */
public interface IPluginCallback {

    void onResult(int code, Object... others);
}
