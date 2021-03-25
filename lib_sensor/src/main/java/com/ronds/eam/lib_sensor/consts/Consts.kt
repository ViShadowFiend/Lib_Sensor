package com.ronds.eam.lib_sensor.consts

internal const val UUID_SERVICE = "11111111-1111-1111-1111-100000000000"
internal const val UUID_DOWN = "11111111-1111-1111-1111-100000000001"
internal const val UUID_UP = "11111111-1111-1111-1111-100000000002"

internal const val HEAD_TO_SENSOR = 0x50.toByte()
internal const val HEAD_FROM_SENSOR = 0xA0.toByte()

object RH205Consts {
  internal const val CMD_SAMPLING_PARAMS = 0x01.toByte() // 采样参数

  // internal const val CMD_DATA_NUM = 0x02.toByte() // 数据条数
  // internal const val CMD_DATA_INFO = 0x03.toByte() // 数据信息
  // internal const val CMD_SAMPLING_DATA_WAVE = 0x04.toByte() // 采样数据波形
  internal const val CMD_STOP_SAMPLE = 0x05.toByte() // 停止采集
  // internal const val CMD_WAVE_DATA_RESULT = 0x14.toByte() // 波形传输结果
  // internal const val CMD_SAMPLING_DATA_TEMP = 0x05.toByte() // 采样数据温度
  internal const val CMD_SET_SYSTEM_PARAMS = 0x06.toByte() // 设置系统参数
  internal const val CMD_CALIBRATION_VIBRATION = 0x09.toByte() // 振动校准
  internal const val CMD_SELF_CHECK = 0x0A.toByte() // 硬件自检测
  internal const val CMD_GET_SYSTEM_PARAMS = 0x0C.toByte() // 获取系统参数
  internal const val CMD_PREPARE_UPGRADE = 0x07.toByte() // 准备升级
  internal const val CMD_UPGRADE_DATA = 0x08.toByte() // 升级
  internal const val CMD_UPGRADE_DATA_RESULT = 0x18.toByte() // 升级包传输状态
  // internal const val CMD_GET_TEMPERATURE_CALIBRATION_COEFFICIENT = 0x0A.toByte() // 获取温度校准系数
  // internal const val CMD_SET_TEMPERATURE_CALIBRATION_COEFFICIENT = 0x0B.toByte() // 设置温度校准系数
  // internal const val CMD_GET_TEMPERATURE_LINEAR_COEFFICIENT = 0x0D.toByte() // 获取温度线性系数
  // internal const val CMD_SET_TEMPERATURE_LINEAR_COEFFICIENT = 0x0E.toByte() // 设置温度线性系数
}