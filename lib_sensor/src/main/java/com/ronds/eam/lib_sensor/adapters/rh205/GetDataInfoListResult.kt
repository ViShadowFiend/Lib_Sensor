package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.getByte
import com.ronds.eam.lib_sensor.utils.getInt
import com.ronds.eam.lib_sensor.utils.getShort

class GetDataInfoListResult(
  var year: Short = 0,
  var month: Byte = 0,
  var day: Byte = 0,
  var hour: Byte = 0,
  var minute: Byte = 0,
  var second: Byte = 0,
  // 数据总条数
  var totalCount: Byte = 0,
  // 数据类型
  var dataType: Byte = 0,
  // 采集轴向
  var axis: Byte = 0,
  var dataLen: Int = 0,
  var freq: Short = 0,
  var len: Short = 0,
) : Decoder<GetDataInfoListResult> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_DATA_INFO_LIST
  override val packSize: Int
    get() = 23

  override fun decode(bytes: ByteArray?): GetDataInfoListResult {
    return bytes.unpack().let {
      GetDataInfoListResult().apply {
        year = it.getShort(0)
        month = it.getByte(2)
        day = it.getByte(3)
        hour = it.getByte(4)
        minute = it.getByte(5)
        second = it.getByte(6)
        totalCount = it.getByte(7)
        dataType = it.getByte(8)
        axis = it.getByte(9)
        dataLen = it.getInt(10)
        freq = it.getShort(14)
        len = it.getShort(16)
      }
    }
  }
}