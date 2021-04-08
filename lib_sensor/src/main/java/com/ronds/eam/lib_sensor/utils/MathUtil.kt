package com.ronds.eam.lib_sensor.utils

import java.math.BigDecimal
import kotlin.math.pow
import kotlin.math.sqrt

object MathUtil {
  private const val DEF_DIV_SCALE = 1 // 默认小数精度位数

  /**
   * 提供精确的加法运算。
   *
   * @param v1 被加数
   * @param v2 加数
   *
   * @return 两个参数的和
   */
  fun add(v1: Double, v2: Double): Double {
    val b1 = BigDecimal(v1.toString())
    val b2 = BigDecimal(v2.toString())
    return b1.add(b2).toDouble()
  }

  /**
   * 提供精确的减法运算。
   *
   * @param v1 被减数
   * @param v2 减数
   *
   * @return 两个参数的差
   */
  fun sub(v1: Double, v2: Double): Double {
    val b1 = BigDecimal(v1.toString())
    val b2 = BigDecimal(v2.toString())
    return b1.subtract(b2).toDouble()
  }

  /**
   * 提供精确的乘法运算。
   *
   * @param v1 被乘数
   * @param v2 乘数
   *
   * @return 两个参数的积
   */
  fun mul(v1: Double, v2: Double): Double {
    val b1 = BigDecimal(v1.toString())
    val b2 = BigDecimal(v2.toString())
    return b1.multiply(b2).toDouble()
  }

  /**
   * 提供（相对）精确的除法运算。当发生除不尽的情况时，由scale参数指
   * 定精度，以后的数字四舍五入。
   *
   * @param v1 被除数
   * @param v2 除数
   * @param scale 表示表示需要精确到小数点以后几位。
   *
   * @return 两个参数的商
   */
  fun div(v1: Double, v2: Double, scale: Int = DEF_DIV_SCALE): Double {
    require(scale >= 0) { "The scale must be a positive integer or zero" }
    val b1 = BigDecimal(v1.toString())
    val b2 = BigDecimal(v2.toString())
    return b1.divide(b2, scale, BigDecimal.ROUND_HALF_UP).toDouble()
  }

  /**
   * 提供精确的小数位四舍五入处理。
   *
   * @param v 需要四舍五入的数字
   * @param scale 小数点后保留几位
   *
   * @return 四舍五入后的结果
   */
  fun round(v: Double, scale: Int): Double {
    require(scale >= 0) { "The scale must be a positive integer or zero" }
    val b = BigDecimal(v.toString())
    val one = BigDecimal("1")
    return b.divide(one, scale, BigDecimal.ROUND_HALF_UP).toDouble()
  }

  fun roundHalfUp(d: Double, scale: Int): String? {
    require(scale >= 0) { "The scale must be a positive integer or zero" }
    return try {
      val bd = BigDecimal(d)
      bd.setScale(scale, BigDecimal.ROUND_HALF_UP).toString()
    } catch (e: Exception) {
      null
    }
  }

  fun roundHalfUp(s: String?, scale: Int): Double {
    require(scale >= 0) { "The scale must be a positive integer or zero" }
    return try {
      val bd = BigDecimal(s)
      bd.setScale(scale, BigDecimal.ROUND_HALF_UP).toDouble()
    } catch (e: Exception) {
      BigDecimal(0).setScale(scale, BigDecimal.ROUND_HALF_UP).toDouble()
    }
  }

  /**
   * 最大值
   */
  fun max(data: DoubleArray?): Double {
    return data?.maxOrNull() ?: 0.0
  }

  /**
   * 最小值
   */
  fun min(data: DoubleArray?): Double {
    return data?.minOrNull() ?: 0.0
  }

  /**
   * 平均值
   */
  fun mean(data: DoubleArray?): Double {
    if (data == null || data.isEmpty()) {
      return 0.0
    }
    val unit = data.average()
    var correction = 0.0
    for (e in data) {
      correction += e - unit
    }
    return unit + correction / data.size
  }

  /**
   * 累加和
   */
  fun sum(data: DoubleArray?): Double {
    if (data == null || data.isEmpty()) {
      return 0.0
    }
    return data.sum()
  }

  /**
   * 乘积
   */
  fun product(data: DoubleArray?): Double {
    if (data == null || data.isEmpty()) {
      return 0.0
    }
    var product = 1.0
    for (e in data) {
      product *= e
    }
    return product
  }

  /**
   * 标准方差
   */
  fun variance(data: DoubleArray?): Double {
    if (data == null || data.size <= 1) {
      return 0.0
    }
    val mean = mean(data)
    var accum = 0.0
    var dev = 0.0
    var accum2 = 0.0
    for (e in data) {
      dev = e - mean
      accum += dev * dev
      accum2 += dev
    }
    val len = data.size.toDouble()
    return (accum - accum2 * accum2 / len) / (len - 1.0)
  }

  /**
   * 平方和
   */
  fun sumOfSquares(data: DoubleArray?): Double {
    if (data == null || data.isEmpty()) {
      return 0.0
    }
    return data.sumByDouble { it * it }
  }

  fun effective(data: DoubleArray): Double {
    var endavge = 0.0
    var startavge = 0.0
    var startsum = 0.0
    var endsum = 0.0
    for (i in data.indices) {
      startsum += data[i]
    }
    startavge = startsum / data.size
    for (i in data.indices) {
      data[i] = (data[i] - startavge).pow(2.0)
    }
    for (i in data.indices) {
      endsum += data[i]
    }
    endavge = endsum / data.size
    return endavge
  }

  /**
   * 有效值
   */
  fun value(data: DoubleArray): Double {
    return try {
      sqrt(sumOfSquares(data) / (data.size * 1.0))
    } catch (ex: Exception) {
      0.0
    }
  }

  /**
   * 峰峰值
   */
  fun ffValue(data: DoubleArray?): Double {
    return try {
      max(data) - min(data)
    } catch (ex: Exception) {
      0.0
    }
  }

  /**
   * 峰值
   */
  fun fValue(data: DoubleArray?): Double {
    return try {
      Math.max(Math.abs(max(data) - mean(data)), Math.abs(min(data) - mean(data)))
    } catch (ex: Exception) {
      0.0
    }
  }

  /**
   * 绝对最大值
   */
  fun absMaxValue(data: DoubleArray?): Double {
    return try {
      Math.max(Math.abs(max(data)), Math.abs(min(data)))
    } catch (ex: Exception) {
      0.0
    }
  }

  /**
   * 峭度值
   */
  fun qdValue(data: DoubleArray): Double {
    return try {
      val newData = DoubleArray(data.size)
      for (i in data.indices) {
        newData[i] = Math.pow(data[i], 4.0)
      }
      Math.sqrt(Math.pow(mean(newData), 4.0))
    } catch (ex: Exception) {
      0.0
    }
  }
}