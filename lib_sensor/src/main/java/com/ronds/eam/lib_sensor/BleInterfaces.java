package com.ronds.eam.lib_sensor;

import com.clj.fastble.data.BleDevice;
import java.util.List;

/**
 * 回调集合
 *
 * @author An.Wang 2019/8/21 14:16.
 */
public interface BleInterfaces {

  /**
   * 指令失败回调
   */
  interface OnFailCallback {

    /**
     * 回调失败提示信息
     *
     * @param msg 提示信息
     */
    void onFail(String msg);
  }

  /**
   * 无回调参数接口, 仅回调成功状态
   */
  interface ActionCallback extends OnFailCallback {

    /**
     * 指令执行成功
     */
    void onSuccess();
  }

  /**
   * 扫描回调接口
   */
  interface ScanCallback {

    /**
     * 扫描开始
     */
    void onScanStart();

    /**
     * 扫描结束
     */
    void onScanEnd();

    /**
     * 扫描结果
     *
     * @param devices ble 设备列表
     */
    void onScanResult(List<BleDevice> devices);
  }

  /**
   * 断开连接回调接口
   */
  interface DisconnectCallback {

    /**
     * 断开连接开始
     */
    void onDisconnectStart();

    /**
     * 断开连接结束
     */
    void onDisconnectEnd();
  }

  /**
   * 连接 516 时, 状态回调接口
   */
  interface ConnectStatusCallback {

    /**
     * 开始连接
     */
    void onConnectStart();

    /**
     * 与已连接的 516 断开连接时回调此方法
     *
     * @param device 516
     */
    void onDisconnected(BleDevice device);

    /**
     * 连接失败
     *
     * @param device 连接失败的 516, 可能为空
     * @param msg 失败信息
     */
    void onConnectFail(BleDevice device, String msg);

    /**
     * 连接成功
     *
     * @param device 连接成功的 516
     */
    void onConnectSuccess(BleDevice device);
  }

  /**
   * 测温回调接口
   */
  interface SampleTempCallback extends OnFailCallback {

    /**
     * 回调温度值
     *
     * @param temp 温度, 4 字节浮点型
     */
    void onReceiveTemp(float temp);
  }

  /**
   * 测振回调接口
   */
  interface SampleVibCallback extends OnFailCallback {

    /**
     * 回调测振结果
     *
     * @param vibData 原始加速度数据, short[]
     * @param coe 转换系数
     */
    void onReceiveVibData(short[] vibData, float coe);
  }

  /**
   * 获取传感器系统参数回调接口
   */
  interface GetSystemParamsCallback extends OnFailCallback {

    /**
     * 回调系统参数
     *
     * @param sn 传感器的 sn
     * @param coe 加速度系数
     * @param emi 测温发射率
     * @param dur 待机时长
     * @param hdw_ver 硬件版本号
     * @param main_ver_516 516 主版本号
     * @param sub_ver_516 516 次版本号
     * @param main_ver_temp 测温模块主版本号
     * @param sub_ver_temp 测温模块次版本号
     */
    void onCallback(long sn, float coe, float emi, long dur, float hdw_ver, int main_ver_516, int sub_ver_516, int main_ver_temp, int sub_ver_temp);
  }

  /**
   * 振动校准回调接口
   */
  interface CalibrationCallback extends OnFailCallback {

    /**
     * 回调校准系数
     *
     * @param coe 校准系数
     */
    void onCallback(float coe);
  }

  /**
   * 获取温度校准系数的回调接口
   */
  interface GetTemperatureCalibrationCoefficientCallback extends OnFailCallback {

    /**
     * 回调获取到的温度校准系数
     *
     * @param off 4 字节浮点型, 偏移.
     * @param env_temp 4 字节浮点型, 环境温度.
     * @param tar 4 字节浮点型, 目标值
     */
    void onCallback(float off, float env_temp, float tar);
  }

  /**
   * 获取温度线性系数的回调接口
   */
  interface GetTemperatureLinearCoefficientCallback extends OnFailCallback {

    /**
     * 回调温度线性系数
     *
     * @param coe1 系数 1, 7 个 4 字节浮点型的数组
     * @param coe2 系数 2, 7 个 4 字节浮点型的数组
     * @param off 补偿值, 7 个 4 字节浮点型的数组
     */
    void onCallback(float[] coe1, float[] coe2, float[] off);
  }

  /**
   * 升级回调接口
   */
  interface UpgradeCallback {

    /**
     * 回调升级结果
     *
     * @param success true - 升级成功, false - 升级失败
     * @param msg 升级成功或失败后的回调信息
     */
    void onUpgradeResult(boolean success, String msg);
  }
}
