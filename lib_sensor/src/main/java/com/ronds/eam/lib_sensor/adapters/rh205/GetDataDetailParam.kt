package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.Utils

class GetDataDetailParam(
// 1 - 测量定义, 2 - 长波形, 3 - 黑匣子
  var dataType: Byte = 0,
  // 数据源
  var dataSource: Byte = 0,
  // 采集时间, 年高位固定 20
  var year: Byte = 0,
  var month: Byte = 0,
  var day: Byte = 0,
  var hour: Byte = 0,
  var minute: Byte = 0,
  var second: Byte = 0,
  var ms: Byte = 0,
  // 序号
  var index: Byte = 0,
  var dataLen: Int = 0,
) : Encoder {
  override val cmdTo: Byte
    get() = RH205Consts.CMD_DATA_DETAIL

  override fun encode(): ByteArray {
    return listOf(
      1 to dataType,
      1 to dataSource,
      1 to year,
      1 to month,
      1 to day,
      1 to hour,
      1 to minute,
      1 to second,
      1 to ms,
      1 to index,
    ).let { Utils.buildBytes(10, it) }
      .run { pack() }
  }
}