package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.getFloat
import com.ronds.eam.lib_sensor.utils.getInt

data class SampleResultAdapter(
  var sn: Int = 0,
  // 计算系数
  var coe: Float = 0f,
  // 电池电压
  var v: Float = 0f,
  // 电池电流
  var a: Float = 0f,
) : Decoder<SampleResultAdapter> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_SAMPLING_PARAMS

  override fun decode(bytes: ByteArray?): SampleResultAdapter {
    return bytes.unpack(21).let {
      SampleResultAdapter().apply {
        sn = it.getInt(0)
        coe = it.getFloat(4)
        v = it.getFloat(8)
        a = it.getFloat(12)
      }
    }
  }
}

data class SampleTempResultAdapter(
  var sn: Int = 0,
  // 温度
  var temp: Float = 0f,
  // 电池电压
  var v: Float = 0f,
  // 电池电流
  var a: Float = 0f,
) : Decoder<SampleTempResultAdapter> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_SAMPLING_PARAMS

  override fun decode(bytes: ByteArray?): SampleTempResultAdapter {
    return bytes.unpack(21).let {
      SampleTempResultAdapter().apply {
        sn = it.getInt(0)
        temp = it.getFloat(4)
        v = it.getFloat(8)
        a = it.getFloat(12)
      }
    }
  }
}