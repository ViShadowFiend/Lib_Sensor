package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts

data class GetDataListParams(
  // 1 - 测量定义, 2 - 长波形, 3 - 黑匣子
  var dataType: Byte
) : Encoder {
  override val cmdTo: Byte
    get() = RH205Consts.CMD_SAMPLING_PARAMS

  override fun encode(): ByteArray {
    return byteArrayOf(dataType).pack()
  }
}