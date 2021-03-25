package com.ronds.eam.lib_sensor.adapters

interface Wrapper {
  val cmd: Byte

  fun ByteArray?.wrap(): ByteArray
}

interface RH205Wrapper : Wrapper {
  override fun ByteArray?.wrap(): ByteArray {
    TODO("Not yet implemented")
  }
}