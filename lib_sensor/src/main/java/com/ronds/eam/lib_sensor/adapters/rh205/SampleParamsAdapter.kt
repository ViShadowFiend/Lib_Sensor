package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.Utils

data class SampleParamsAdapter(
  // 采集长度, 单位 K, 1/2/4/8/16/32
  var len: Short = 0,
  // 分析频率, 单位 100hz, 5, 10, 20, 50, 100, 200,
  var freq: Short = 0,
  // 轴向, 0 - z, 1 - x, 2 -y, 4 - 采集温度
  var axis: Byte = 0,
  // 测温发射率
  var coe: Float = 0.97f,
): Encoder {
  override val cmdTo: Byte
    get() = RH205Consts.CMD_SAMPLING_PARAMS

  override fun encode(): ByteArray {
    return listOf(
      2 to len,
      2 to freq,
      1 to axis,
      4 to coe,
    )
      .let { Utils.buildBytes(9, it) }
      .run { pack() }
  }
}