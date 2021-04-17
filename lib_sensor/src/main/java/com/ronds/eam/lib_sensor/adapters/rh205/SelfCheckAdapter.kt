package com.ronds.eam.lib_sensor.adapters.rh205

import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.utils.getByte
import com.ronds.eam.lib_sensor.utils.getInt
import com.ronds.eam.lib_sensor.utils.getString

data class SelfCheckAdapter(
  // 节点类型
  var nodeType: Byte = 0,
  // sn
  var sn: Int = 0,
  // 主晶振状态
  var mainStatus: Byte = 0,
  // rtc 晶振状态
  var rtcStatus: Byte = 0,
  // ad 模块状态
  var adStatus: Byte = 0,
  // fram 状态
  var framStatus: Byte = 0,
  // 外部 flash 状态
  var flashStatus: Byte = 0,
  // rf 状态
  var rfStatus: Byte = 0,
): Decoder<SelfCheckAdapter> {
  override val cmdFrom: Byte = RH205Consts.CMD_SELF_CHECK

  override fun decode(bytes: ByteArray?): SelfCheckAdapter {
    val d = bytes.unpack(20)
    return SelfCheckAdapter().apply {
      nodeType = d.getByte(0)
      sn = d.getInt(1)
    }
  }

  fun getMainStatus(): Status {
    return Status.of(mainStatus)
  }

  fun getRtcStatus(): Status {
    return Status.of(rtcStatus)
  }

  fun getAdStatus(): Status {
    return Status.of(adStatus)
  }

  fun getFramStatus(): Status {
    return Status.of(framStatus)
  }

  fun getFlashStatus(): Status {
    return Status.of(flashStatus)
  }

  fun getRfStatus(): Status {
    return Status.of(rfStatus)
  }

  enum class Status(val status: Byte) {
    NORMAL(0x00) {
      override val display: String
        get() = "正常"
    },
    ABNORMAL(0x01) {
      override val display: String
        get() = "异常"
    },
    ;
    abstract val display: String

    companion object {
      fun of(status: Byte): Status {
        return values().find { it.status == status } ?: NORMAL
      }
    }
  }
}