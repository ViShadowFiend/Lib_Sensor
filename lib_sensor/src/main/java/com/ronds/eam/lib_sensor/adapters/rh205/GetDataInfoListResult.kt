package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.getByte
import com.ronds.eam.lib_sensor.utils.getInt
import com.ronds.eam.lib_sensor.utils.getShort

private const val ITEM_LEN = 10

data class GetDataInfoListItem(
  // 数据类型
  var dataType: Byte = 0,
  // 采集轴向
  var axis: Byte = 0,
  var dataLen: Int = 0,
  var freq: Short = 0,
  var len: Short = 0,
)

class GetDataInfoListResult(
  // 采集时间, 年高位固定 20
  var year: Byte = 0,
  var month: Byte = 0,
  var day: Byte = 0,
  var hour: Byte = 0,
  var minute: Byte = 0,
  var second: Byte = 0,
  var ms: Byte = 0,
  // 数据总条数
  var totalCount: Byte = 0,
  var itemsData: List<GetDataInfoListItem> = emptyList()
) : Decoder<GetDataInfoListResult> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_DATA_INFO_LIST

  override fun decode(bytes: ByteArray?): GetDataInfoListResult {
    val ret = GetDataInfoListResult()
    if (bytes == null || bytes.isEmpty()) {
      return ret
    }
    val d = bytes.unpack(bytes.size)
    ret.apply {
      year = d.getByte(0)
      month = d.getByte(1)
      day = d.getByte(2)
      hour = d.getByte(3)
      minute = d.getByte(4)
      second = d.getByte(5)
      ms = d.getByte(6)
      totalCount = d.getByte(7)
    }
    val items = mutableListOf<GetDataInfoListItem>()
    for (i in 0 until ret.totalCount) {
      val item = GetDataInfoListItem()
        .apply {
          dataType = d.getByte(8 + i * ITEM_LEN)
          axis = d.getByte(9 + i * ITEM_LEN)
          dataLen = d.getInt(10 * ITEM_LEN)
          freq = d.getShort(14 * ITEM_LEN)
          len = d.getShort(16 * ITEM_LEN)
        }
      items.add(item)
    }
    ret.apply {
      itemsData = items
    }
    return ret
  }
}