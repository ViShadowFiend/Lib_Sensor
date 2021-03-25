package com.ronds.eam.lib_sensor.utils

import com.ronds.eam.lib_sensor.exceptions.CmdIncorrectException
import com.ronds.eam.lib_sensor.exceptions.HeadIncorrectException
import com.ronds.eam.lib_sensor.exceptions.SizeIncorrectException
import kotlin.jvm.Throws

internal object Utils {
  fun buildBytes(totalSize: Int, data: List<Pair<Int, Any>>): ByteArray {
    val ret = ByteArray(totalSize)
    var i = 0
    for (e in data) {
      val size = e.first
      var v = e.second
      when (v) {
        is Short -> v = ByteUtil.shortToBytes(v)
        is Int -> v = ByteUtil.intToBytes(v)
        is Float -> v = ByteUtil.floatToBytes(v)
        is String -> v = v.toByteArray()
      }
      System.arraycopy(v, 0, ret, i, size)
      i += size
    }
    return ret
  }
}

internal fun ByteArray.getString(index: Int, len: Int) = String(this.copyOfRange(index, index + len))
internal fun ByteArray.getByte(index: Int) = this[index]
internal fun ByteArray.getChar(index: Int) = this[index].toChar()
internal fun ByteArray.getInt(index: Int) = ByteUtil.getIntFromByteArray(this, index)
internal fun ByteArray.getShort(index: Int) = ByteUtil.getShortFromByteArray(this, index)
internal fun ByteArray.getFloat(index: Int) = ByteUtil.getFloatFromByteArray(this, index)

internal fun ByteArray?.pack(head: Byte, cmd: Byte): ByteArray {
  val src = this
  val originSize = src?.size ?: 0

  val length: Short = (originSize + 5).toShort() // length

  val lengthB: ByteArray = ByteUtil.shortToBytes(length) // lengthB

  val bytesWithoutCs = ByteArray(originSize + 4)

  bytesWithoutCs[0] = head
  bytesWithoutCs[1] = cmd
  System.arraycopy(lengthB, 0, bytesWithoutCs, 2, 2)
  if (src != null && originSize > 0) {
    System.arraycopy(src, 0, bytesWithoutCs, 4, originSize)
  }

  val cs: Byte = ByteUtil.makeCheckSum(bytesWithoutCs) // cs
  val bytes = bytesWithoutCs.copyOf(bytesWithoutCs.size + 1)
  bytes[bytes.size - 1] = cs

  return bytes
}

@Throws(Exception::class)
internal fun ByteArray?.unpack(headFrom: Byte, cmdFrom: Byte, packSize: Int) : ByteArray {
  val size = this?.size ?: 0
  if (this == null || size != packSize || size < 5) {
    throw SizeIncorrectException
  }
  val head = this[0]
  if (head != headFrom) {
    throw HeadIncorrectException
  }
  val cmd = this[1]
  if (cmd != cmdFrom) {
    throw CmdIncorrectException
  }
  return this.copyOfRange(4, packSize - 1)
}