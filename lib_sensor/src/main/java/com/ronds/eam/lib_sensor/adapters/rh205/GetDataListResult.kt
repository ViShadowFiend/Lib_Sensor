package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.ByteUtil
import com.ronds.eam.lib_sensor.utils.getByte
import com.ronds.eam.lib_sensor.utils.getShort

private const val ITEM_LEN = 8 //

data class GetDataListResult(
  // 数据总条数
  var totalCount: Short = 0,
  var itemsData: List<GetDataListItem> = emptyList(),
) : Decoder<GetDataListResult> {
  override val cmdFrom: Byte
    get() = RH205Consts.CMD_DATA_LIST

  override fun decode(bytes: ByteArray?): GetDataListResult {
    val ret = GetDataListResult()
    if (bytes == null || bytes.isEmpty()) {
      return ret
    }
    val len = bytes.size
    val d = bytes.unpack(len)
    val total = d.getShort(0)
    val items = mutableListOf<GetDataListItem>()
    for (i in 0 until total) {
      val item = GetDataListItem()
        .apply {
          dataSource = d.getByte(2 + i * ITEM_LEN)
          year = d.getByte(3 + i * ITEM_LEN)
          month = d.getByte(4 + i * ITEM_LEN)
          day = d.getByte(5 + i * ITEM_LEN)
          hour = d.getByte(6 + i * ITEM_LEN)
          minute = d.getByte(7 + i * ITEM_LEN)
          second = d.getByte(8 + i * ITEM_LEN)
          ms = d.getByte(9 + i * ITEM_LEN)
        }
      items.add(item)
    }
    ret.apply {
      totalCount = total
      itemsData = items
    }
    return ret
  }
}

data class GetDataListItem(
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
)