package com.ronds.eam.lib_sensor.exceptions

open class RException constructor(val msg: String? = null, val throwable: Throwable? = null) :
  Exception(msg, throwable) {
  override fun toString(): String {
    return "$msg. ${throwable?.message}"
  }
}

object SizeIncorrectException : RException("size incorrect.")
object HeadIncorrectException : RException("head incorrect.")
object CmdIncorrectException : RException("cmd incorrect.")