package com.ronds.eam.lib_sensor

import com.ronds.eam.lib_sensor.utils.MathUtil
import com.ronds.eambletoolkit.Spectrum
import com.ronds.eambletoolkit.VibDataProcessUtil

class VibResult private constructor(private val builder: Builder) {

  /**
   * 去平均
   *
   * @param accCoe 加速度系数
   */
  private fun List<Short>.avg(accCoe: Float): DoubleArray {
    if (isEmpty()) {
      return doubleArrayOf()
    }
    val unit: Double = average()
    return map { (it - unit) * accCoe }.toDoubleArray()
  }

  /**
   * 求总值
   * @param paraType 0 - 有效值
   *                 1 - 峰值
   *                 2 - 峰峰值
   *                 3 - 峭度值
   */
  private fun DoubleArray.totalValue(paraType: Int): Double {
    return when (paraType) {
      0 -> MathUtil.value(this)
      1 -> MathUtil.fValue(this)
      2 -> MathUtil.ffValue(this)
      3 -> MathUtil.qdValue(this)
      else -> 0.0
    }
  }

  /**
   * 加速度波形转为速度波形
   *
   * @param f 采样频率
   * @param fMin 下限频率
   * @param fMax 上限频率
   */
  private fun DoubleArray.toVel(f: Float, fMin: Float, fMax: Float): DoubleArray {
    if (isEmpty()) {
      return doubleArrayOf()
    }
    val r = VibDataProcessUtil.accToVel(this, f.toDouble(), fMin.toDouble(), fMax.toDouble())
    return r.map { it * 1000 }.toDoubleArray()
  }

  /**
   * 加速度波形转为位移波形
   *
   * @param f 采样频率
   * @param fMin 下限频率
   * @param fMax 上限频率
   */
  private fun DoubleArray.toDist(f: Float, fMin: Float, fMax: Float): DoubleArray {
    if (isEmpty()) {
      return doubleArrayOf()
    }
    var r: DoubleArray
    if (fMax == 100f || fMax == 200f) {
      r = VibDataProcessUtil.accToDist(this, f.toDouble() * 5, fMin.toDouble(), fMax.toDouble() * 5)
      r = DoubleArray(r.size / 5) { r[5 * it] }
    } else {
      r = VibDataProcessUtil.accToDist(this, f.toDouble(), fMin.toDouble(), fMax.toDouble())
    }
    return r.map { it * 100_0000 }.toDoubleArray()
  }

  /**
   * 波形数据转换为频谱
   *
   * @param freq 采样频率
   */
  private fun DoubleArray.toSpectrum(freq: Float): DoubleArray {
    if (isEmpty()) {
      return doubleArrayOf()
    }
    val spectrum = Spectrum()
    VibDataProcessUtil.fft(spectrum, this, freq.toDouble())
    var r = spectrum.amplitude
    // val df = freqUpper * 1.28 / (size - 1)
    r = r.copyOfRange(0, ((size - 1) / 1.28).toInt())
    return r
  }

  /**
   * 转换系数
   */
  private fun DoubleArray.convertCoe(): Double {
    return MathUtil.absMaxValue(this) / 16384
  }

  private fun DoubleArray.toSavedBytes(): ByteArray {
    val coe = convertCoe()
    val ret = ByteArray(size * 2)
    for ((i, e) in this.withIndex()) {
      val s = (e / coe).toFloat().toInt()
      //注意在此处因服务端格式问题进行了高低位转换
      ret[2 * i + 0] = (s shr 8 and 0xff).toByte()
      ret[2 * i + 1] = (s and 0xff).toByte()
    }
    return ret
  }

  // 波形数据
  private var waveData: DoubleArray = doubleArrayOf()

  // 频谱数据
  private var spectrumData: DoubleArray = doubleArrayOf()

  // 波形数据
  private fun waveData(): DoubleArray {
    if (waveData.isNotEmpty()) {
      return waveData
    }
    waveData = with(builder) {
      val d: DoubleArray = data.avg(accCoe)
      when (signalType) {
        1 -> d.toVel(freq, freqLower, freqUpper)
        2 -> d.toDist(freq, freqLower, freqUpper)
        else -> d
      }
    }
    return waveData
  }

  // 频谱数据
  private fun spectrumData(waveData: DoubleArray): DoubleArray {
    if (spectrumData.isNotEmpty()) {
      return spectrumData
    }
    spectrumData = waveData.toSpectrum(builder.freq)
    return spectrumData
  }

  /**
   * 总值
   */
  fun totalValue(): Double {
    return waveData().totalValue(builder.paraType)
  }

  /**
   * 波形或频谱数据, 根据 builder 中设置的 samplingMode
   */
  fun data(): DoubleArray {
    val waveData = waveData()
    return when (builder.samplingMode) {
      1 -> spectrumData(waveData)
      else -> waveData
    }
  }

  /**
   * 需上传给服务器的格式, 经过一定的变换, 用来保存为本地文件
   */
  fun savedBytes(): ByteArray {
    return data().toSavedBytes()
  }

  /**
   * 转换系数, 即 savedBytes 使用的变换系数
   */
  fun convertCoe(): Double {
    return data().convertCoe()
  }

  class Builder {
    // 原始数据
    var data: List<Short> = listOf()

    // 加速度系数
    var accCoe: Float = 0f

    // 0 - 加速度, 1 - 速度, 2 - 位移
    var signalType: Int = 0

    // 0 - 时域波形, 1 - 频谱
    var samplingMode: Int = 0

    // 0 - 有效值, 1 - 峰值, 2 - 峰峰值, 3 - 峭度值
    var paraType: Int = 0

    // 采样频率
    var freq: Float = 0f

    // 上限频率
    var freqLower: Float = 100f

    // 下限频率
    var freqUpper: Float = 5f

    fun build(): VibResult = VibResult(this)
  }
}