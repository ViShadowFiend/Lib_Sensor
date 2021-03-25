package com.ronds.eam.lib_sensor

import com.clj.fastble.data.BleDevice
import com.ronds.eam.lib_sensor.adapters.rh205.SampleResultAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SelfCheckAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SystemParamsAdapter

/**
 * 指令失败回调
 */
interface OnFailCallback {
  /**
   * 回调失败提示信息
   *
   * @param msg 提示信息
   */
  fun onFail(msg: String?)
}

/**
 * 无回调参数接口, 仅回调成功状态
 */
interface ActionCallback : OnFailCallback {
  /**
   * 指令执行成功
   */
  fun onSuccess()
}

/**
 * 扫描回调接口
 */
interface ScanCallback {
  /**
   * 扫描开始
   */
  fun onScanStart()

  /**
   * 扫描结束
   */
  fun onScanEnd()

  /**
   * 扫描结果
   *
   * @param devices ble 设备列表
   */
  fun onScanResult(devices: List<BleDevice?>?)
}

/**
 * 断开连接回调接口
 */
interface DisconnectCallback {
  /**
   * 断开连接开始
   */
  fun onDisconnectStart()

  /**
   * 断开连接结束
   */
  fun onDisconnectEnd()
}

/**
 * 连接 516 时, 状态回调接口
 */
interface ConnectStatusCallback {
  /**
   * 开始连接
   */
  fun onConnectStart()

  /**
   * 与已连接的 516 断开连接时回调此方法
   *
   * @param device 516
   */
  fun onDisconnected(device: BleDevice?)

  /**
   * 连接失败
   *
   * @param device 连接失败的 516, 可能为空
   * @param msg 失败信息
   */
  fun onConnectFail(device: BleDevice?, msg: String?)

  /**
   * 连接成功
   *
   * @param device 连接成功的 516
   */
  fun onConnectSuccess(device: BleDevice?)
}

/**
 * 测温回调接口
 */
interface SampleTempCallback : OnFailCallback {
  /**
   * 回调温度值
   *
   * @param temp 温度, 4 字节浮点型
   */
  fun onReceiveTemp(temp: Float)
}

/**
 * 测振回调接口
 */
interface SampleVibCallback : OnFailCallback {
  /**
   * 回调测振结果
   *
   * @param vibData 原始加速度数据, short[]
   * @param coe 转换系数
   */
  fun onReceiveVibData(vibData: ShortArray?, coe: Float)
}

/**
 * 获取传感器系统参数回调接口
 */
interface GetSystemParamsCallback : OnFailCallback {
  fun onCallback(data: SystemParamsAdapter)
}

/**
 * 获取硬件自检测结果回调接口
 */
interface SelfCheckCallback : OnFailCallback {
  fun onCallback(data: SelfCheckAdapter)
}

/**
 * 振动校准回调接口
 */
interface CalibrationCallback : OnFailCallback {
  /**
   * 回调校准系数
   *
   * @param coe 校准系数
   */
  fun onCallback(coe: Float)
}

/**
 * 获取温度校准系数的回调接口
 */
interface GetTemperatureCalibrationCoefficientCallback : OnFailCallback {
  /**
   * 回调获取到的温度校准系数
   *
   * @param off 4 字节浮点型, 偏移.
   * @param env_temp 4 字节浮点型, 环境温度.
   * @param tar 4 字节浮点型, 目标值
   */
  fun onCallback(off: Float, env_temp: Float, tar: Float)
}

/**
 * 获取温度线性系数的回调接口
 */
interface GetTemperatureLinearCoefficientCallback : OnFailCallback {
  /**
   * 回调温度线性系数
   *
   * @param coe1 系数 1, 7 个 4 字节浮点型的数组
   * @param coe2 系数 2, 7 个 4 字节浮点型的数组
   * @param off 补偿值, 7 个 4 字节浮点型的数组
   */
  fun onCallback(coe1: FloatArray?, coe2: FloatArray?, off: FloatArray?)
}

interface SampleResultCallback: OnFailCallback {
  fun onCallback(result: SampleResultAdapter)
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
  fun onUpgradeResult(success: Boolean, msg: String?)
}