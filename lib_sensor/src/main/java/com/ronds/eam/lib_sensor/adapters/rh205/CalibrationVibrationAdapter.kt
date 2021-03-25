package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.Utils

// 震动校准
data class CalibrationVibrationAdapter(
  // 采集长度, 单位 K, 1/2/4/8/16/32
  var len: Short = 0,
  // 分析频率, 单位 100hz, 5, 10, 20, 50, 100, 200,
  var freq: Short = 0,
): Encoder {
  override val cmdTo: Byte
    get() = RH205Consts.CMD_CALIBRATION_VIBRATION

  override fun encode(): ByteArray {
    return listOf<Pair<Int, Any>>(
      2 to len,
      2 to freq,
    )
      .let { Utils.buildBytes(4, it) }
      .run { pack() }
  }
}