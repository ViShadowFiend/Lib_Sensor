package com.ronds.eam.lib_sensor.adapters

import com.ronds.eam.lib_sensor.consts.HEAD_TO_SENSOR
import com.ronds.eam.lib_sensor.utils.pack

interface Packer {
  val cmdTo: Byte
  val headTo: Byte
  fun ByteArray?.pack(): ByteArray
}

interface Encoder : Packer {

  override val headTo: Byte
    get() = HEAD_TO_SENSOR

  fun encode(): ByteArray

  override fun ByteArray?.pack(): ByteArray = this.pack(headTo, cmdTo)
}