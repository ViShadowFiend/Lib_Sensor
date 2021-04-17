package com.ronds.eam.lib_sensor.adapters

import com.ronds.eam.lib_sensor.consts.HEAD_FROM_SENSOR
import com.ronds.eam.lib_sensor.exceptions.RException
import com.ronds.eam.lib_sensor.utils.unpack

interface UnPacker {
  val cmdFrom: Byte
  val headFrom: Byte

  @Throws(RException::class)
  fun ByteArray?.unpack(packSize: Int): ByteArray?
}

interface Decoder<out T> : UnPacker {

  override val headFrom: Byte
    get() = HEAD_FROM_SENSOR

  fun decode(bytes: ByteArray?): T

  @Throws(RException::class)
  override fun ByteArray?.unpack(packSize: Int): ByteArray = this.unpack(
    headFrom, cmdFrom, packSize
  )
}