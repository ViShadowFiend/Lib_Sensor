package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.getByte
import com.ronds.eam.lib_sensor.utils.getInt
import com.ronds.eam.lib_sensor.utils.getShort

data class GetDataListResult(
  // 数据总条数
  var totalCount: Short = 0,
  // 数据源
  var dataSource: Byte = 0,
  // 采集时间
  var time: Int = 0,
) : Decoder<GetDataListResult> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_DATA_LIST
  override val packSize: Int
    get() = 12

  override fun decode(bytes: ByteArray?): GetDataListResult {
    return bytes.unpack().let {
      GetDataListResult().apply {
        totalCount = it.getShort(0)
        dataSource = it.getByte(2)
        time = it.getInt(3)
      }
    }
  }
}