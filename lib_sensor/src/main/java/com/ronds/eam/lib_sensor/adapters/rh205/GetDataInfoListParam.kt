package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.Utils

class GetDataInfoListParam(
// 1 - 测量定义, 2 - 长波形, 3 - 黑匣子
  var dataType: Byte = 0,
  // 数据源
  var dataSource: Byte = 0,
  // 采集时间
  var time: Int = 0,
  // 序号
  // var index: Byte = 0,
) : Encoder {
  override val cmdTo: Byte
    get() = RH205Consts.CMD_DATA_INFO_LIST

  override fun encode(): ByteArray {
    return listOf(
      1 to dataType,
      1 to dataSource,
      4 to time,
      1 to 0,
    ).let { Utils.buildBytes(7, it) }
      .run { pack() }
  }
}