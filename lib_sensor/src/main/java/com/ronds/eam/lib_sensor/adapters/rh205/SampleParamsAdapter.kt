package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.Utils

data class SampleParamsAdapter(
  // 采集长度, 单位 K个点, 1/2/4/8/16/32. 如 2K, 即 2 * 1024 个点. 每个点 2 个字节, 即 4096 Byte
  var len: Short = 0,
  // 分析频率, 单位 100hz, 5, 10, 20, 50, 100, 200,
  var freq: Short = 0,
  // 轴向, 0 - z, 1 - x, 2 -y, 4 - 采集温度
  var axis: Byte = 0,
  // 测温发射率
  var tempEmi: Float = 0.97f,
): Encoder {
  // 下达给下位机不需要以下属性. 以下属性为转换原始波形数据使用
  // 0 - 加速度, 1 - 速度, 2 - 位移
  var signalType: Int = 0

  // 0 - 时域波形, 1 - 频谱
  var samplingMode: Int = 0

  // 0 - 有效值, 1 - 峰值, 2 - 峰峰值, 3 - 峭度值
  var paraType: Int = 0

  override val cmdTo: Byte
    get() = RH205Consts.CMD_SAMPLING_PARAMS

  override fun encode(): ByteArray {
    return listOf(
      2 to len,
      2 to freq,
      1 to axis,
      4 to tempEmi,
    )
      .let { Utils.buildBytes(9, it) }
      .run { pack() }
  }
}