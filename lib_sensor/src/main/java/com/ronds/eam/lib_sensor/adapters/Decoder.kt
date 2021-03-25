package com.ronds.eam.lib_sensor.adapters

import com.ronds.eam.lib_sensor.consts.HEAD_FROM_SENSOR
import com.ronds.eam.lib_sensor.exceptions.RException
import com.ronds.eam.lib_sensor.utils.unpack

interface UnPacker {
  val cmdFrom: Byte
  val headFrom: Byte

  // 原始包大小加上 head、cmd、len he cs 共 5 个字节
  val packSize: Int

  @Throws(RException::class)
  fun ByteArray?.unpack(): ByteArray?
}

interface Decoder<out T> : UnPacker {

  override val headFrom: Byte
    get() = HEAD_FROM_SENSOR

  fun decode(bytes: ByteArray?): T

  @Throws(RException::class)
  override fun ByteArray?.unpack(): ByteArray = this.unpack(headFrom, cmdFrom, packSize)
}