package com.ronds.eam.lib_sensor.exceptions

sealed class RException constructor(val msg: String? = null, val throwable: Throwable? = null) :
  Exception(msg, throwable)

object SizeIncorrectException : RException("size incorrect.")
object HeadIncorrectException : RException("head incorrect.")
object CmdIncorrectException : RException("cmd incorrect.")