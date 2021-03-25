package com.ronds.eam.lib_sensor

import android.os.Handler
import android.os.Looper
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.ronds.eam.lib_sensor.adapters.Decoder
import com.ronds.eam.lib_sensor.adapters.Encoder
import com.ronds.eam.lib_sensor.adapters.rh205.CalibrationVibrationAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SampleParamsAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SampleResultAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SampleTempResultAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SelfCheckAdapter
import com.ronds.eam.lib_sensor.adapters.rh205.SystemParamsAdapter
import com.ronds.eam.lib_sensor.consts.HEAD_FROM_SENSOR
import com.ronds.eam.lib_sensor.consts.HEAD_TO_SENSOR
import com.ronds.eam.lib_sensor.consts.RH205Consts
import com.ronds.eam.lib_sensor.consts.RH205Consts.CMD_CALIBRATION_VIBRATION
import com.ronds.eam.lib_sensor.consts.RH205Consts.CMD_GET_SYSTEM_PARAMS
import com.ronds.eam.lib_sensor.consts.RH205Consts.CMD_SELF_CHECK
import com.ronds.eam.lib_sensor.consts.RH205Consts.CMD_SET_SYSTEM_PARAMS
import com.ronds.eam.lib_sensor.consts.UUID_UP
import com.ronds.eam.lib_sensor.utils.ByteUtil
import com.ronds.eam.lib_sensor.utils.pack
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object RH205Mgr: ABleMgr() {

  override val TIP_DISCONNECT: String
    get() = "当前未与205连接, 请连接205后重试"
  override val TIP_TIMEOUT: String
    get() = "响应超时, 请检查与205的连接"

  var sdf_ = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())

  var bytesWave: ByteArray? = null // 波形原始数据 byte[]

  var mainVersionSensor: Int? = null // 传感器当前的主版本号
  var mainVersionRemote: Int? = null // 服务器获取的的主版本号
  var tempVersionSensor: Int? = null // 传感器当前的测温模块版本号
  var tempVersionRemote: Int? = null // 服务器获取的测温模块版本号

  private val isRunning = AtomicBoolean(false) // 标识ble设备是否执行命令
  private var runningStatus: String? = null // 描述当前 ble 设备正在执行的命令

  fun isNeedUpgrade(sensorVersion: Int?, remoteVersion: Int?): Boolean {
    if (sensorVersion == null || remoteVersion == null) return false
    return remoteVersion > sensorVersion
  }

  // 是否正在采集
  private val isSampling = AtomicBoolean(false)

  // 是否正在测温
  private val isTempProcessing = AtomicBoolean(false)

  // 测温回调
  private var sampleTempCallback: BleInterfaces.SampleTempCallback? = null

  /**
   * ok
   * 开始温度采集
   * @param ceWenFaSheLv 测温发射率
   */
  fun startSampleTemp(ceWenFaSheLv: Float = 0.97f, callback: BleInterfaces.SampleTempCallback) {
    sampleTempCallback = callback
    if (!isConnected()) {
      sampleTempCallback?.onFail(TIP_DISCONNECT)
      return
    }
    isTempProcessing.set(true)
    // val response = addHeadCmdLengthAndCs(null, CMD_SAMPLING_DATA_TEMP)
    val isTimeoutSample = AtomicBoolean(false)
    val isReceivedSample = AtomicBoolean(false)
    val isTimeoutTemp = AtomicBoolean(false)
    val isReceivedTemp = AtomicBoolean(false)

    // fun responseTemp() {
    //   singleExecutor.submit {
    //     isTimeoutTemp.set(false)
    //     isReceivedTemp.set(false)
    //     if (!isTempProcessing.get()) return@submit
    //     doTimeout(200L, {
    //       dTag("temp_205_send", response)
    //       write(response)
    //     }, 200L, 2, 500, isReceivedTemp, isTimeoutTemp) {
    //       mainHandler.post {
    //         if (isTempProcessing.get()) {
    //           sampleTempCallback?.onFail("采集失败, 超时")
    //         }
    //         isTempProcessing.set(false)
    //       }
    //     }
    //   }
    // }

    fun notifyTemp() {
      singleExecutor.submit {
        doSleep(200)
        notify { data ->
          dTag("notify_temp", data)
          isReceivedTemp.set(true)
          if (!isTempProcessing.get()) return@notify
          if (isTimeoutTemp.get()) return@notify
          var r = SampleTempResultAdapter()
          try {
            r = r.decode(data)
          } catch (e: Exception) {
            mainHandler.post {
              if (isTempProcessing.get()) {
                sampleTempCallback?.onFail("采集失败, 返回格式有误")
              }
              isTempProcessing.set(false)
            }
            return@notify
          }
          sampleTempCallback?.onReceiveTemp(r.temp)
          // responseTemp()
        }
      }
    }

    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_temp", data)
        isReceivedTemp.set(true)
        if (!isTempProcessing.get()) return@notify
        if (isTimeoutTemp.get()) return@notify
        var r = SampleTempResultAdapter()
        try {
          r = r.decode(data)
        } catch (e: Exception) {
          mainHandler.post {
            if (isTempProcessing.get()) {
              sampleTempCallback?.onFail("采集失败, 返回格式有误")
            }
            isTempProcessing.set(false)
          }
          return@notify
        }
        sampleTempCallback?.onReceiveTemp(r.temp)
        // responseTemp()
      }
      val sampleTemp = SampleParamsAdapter().apply { coe = ceWenFaSheLv }.encode()
      write(sampleTemp)
      // notify { data ->
      //   dTag("notify_set_sample_params", data)
      //   if (data == null || data.size != 10) {
      //     return@notify
      //   }
      //   isReceivedSample.set(true)
      //   if (!isTempProcessing.get()) return@notify
      //   if (isTimeoutSample.get()) {
      //     mainHandler.removeCallbacksAndMessages(null)
      //     BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
      //     return@notify
      //   }
      //   if (data[4] != 1.toByte()) {
      //     mainHandler.post {
      //       sampleTempCallback?.onFail("下达采集参数失败")
      //       mainHandler.removeCallbacksAndMessages(null)
      //     }
      //     BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
      //   }
      //   else {
      //     dTag("temp", "下达采集参数成功")
      //     notifyTemp()
      //     responseTemp()
      //   }
      // }
      // val action: () -> Unit = {
      //   val data = bytesSetSampleParams(
      //     0x00.toByte(), 0, 0, 0,
      //     0, 0, 0, 0, 0, 0,
      //     0, 1, 0, 0, 0, ceWenFaSheLv
      //   )
      //   write(data)
      // }
      //
      // doTimeout(
      //   200L, action, 0, 2, 500, isReceivedSample,
      //   isTimeoutSample
      // ) {
      //   mainHandler.post {
      //     if (isTempProcessing.get()) {
      //       sampleTempCallback?.onFail("下达温度采集参数失败, 超时")
      //     }
      //     isTempProcessing.set(false)
      //   }
      // }
    }
  }

  fun removeSampleTempCallback() {
    sampleTempCallback = null
  }

  fun startSample(params: SampleParamsAdapter, callback: SampleResultCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    isSampling.set(true)

    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_sample", data)
        if (!isSampling.get()) return@notify
        var r = SampleResultAdapter()
        try {
          r = r.decode(data)
        } catch (e: Exception) {
          mainHandler.post {
            if (isTempProcessing.get()) {
              callback.onFail("采集失败, 返回格式有误")
            }
            isTempProcessing.set(false)
          }
          return@notify
        }
        callback.onCallback(r)
        // responseTemp()
      }
      val data = params.encode()
      write(data)
    }
  }

  /**
   * ok
   * 停止采集
   */
  fun stopSample(callback: BleInterfaces.ActionCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    isSampling.set(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("stop_sample_205_receive", ByteUtil.byteArray2HexString(data))
        if (data == null) {
          mainHandler.post {
            callback.onFail("停止采集失败")
            mainHandler.removeCallbacksAndMessages(null)
          }
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          return@notify
        }
        isReceived.set(true)
        if (isTimeout.get()) {
          mainHandler.removeCallbacksAndMessages(null)
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          return@notify
        }
        mainHandler.post {
          callback.onSuccess()
          mainHandler.removeCallbacksAndMessages(null)
        }
        BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
      }
      doTimeout(200L, {
        val data = byteArrayOf().pack(HEAD_TO_SENSOR, RH205Consts.CMD_STOP_SAMPLE)
        write(data)
      }, 200L, 2, 500, isReceived, isTimeout) {
        mainHandler.post {
          callback.onFail("超时")
          mainHandler.removeCallbacksAndMessages(null)
        }
        BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
      }
    }
  }

  // 收到的每包数据, 与 205 的协议规定, 振动数据不超过 320 包
  // list 中 index 对应收到的包的编号
  private val waveData: MutableList<ByteArray?> = MutableList(320) { null }

  // 回复下位机收包结果, 40 * 8bit, 总共 320 bit, 对应最多 320 包的收包情况.
  // 当收到包后, 将该包对应的 位 置 1
  private val response = ByteArray(40)

  /**
   * 测振
   *
   * 测振的流程为:
   * 1. 上位机下达采集命令
   * 2. 下位机回复下达采集命令的结果, 并开始采集
   * 3. 下位机采集完毕
   * 4. 下位机开始分包向上位机传输测振数据, 中途不管丢包与否. 下位机认为传输完所有包后(上位机可能并未收全)向上位机发送结果确认通知
   * 5. 上位机收到结果确认通知后, 检查收包情况, 并将结果传给下位机
   * 6. 下位机收到上位机的收包结果. 若有丢包, 反复多次进行 4、5 步骤补传; 若包全部收齐或超过重传次数限制, 测振结束.
   *
   * [caiJiChangDu] 采集长度, 单位 K
   * [fenXiPinLv] 分析频率, 单位 Hz
   */
  fun sampleVib(caiJiChangDu: Int, fenXiPinLv: Int, callback: BleInterfaces.SampleVibCallback) {
    // if (!isConnected()) {
    //   callback.onFail(TIP_DISCONNECT)
    //   return
    // }
    //
    // val collectTime = (caiJiChangDu * 1024 / fenXiPinLv / 2.56 * 1000).toLong()
    // // caiJiChangDu * 1024 为多少个点, * 2 因为每个点(short) 2个字节, / 4 是预估传输速度为 4b/ms
    // val transferTime = (caiJiChangDu * 1024 * 2 / 4).toLong()
    //
    // val isTimeoutSample = AtomicBoolean(false)
    // val isReceivedSample = AtomicBoolean(false)
    // val isTimeoutResultFirst = AtomicBoolean(false)
    // val isReceivedResultFirst = AtomicBoolean(false)
    // val isTimeoutResultOthers = AtomicBoolean(false)
    // val isReceivedResultOthers = AtomicBoolean(false)
    // val isTimeoutWaveData = AtomicBoolean(false)
    // val isReceivedWaveData = AtomicBoolean(false)
    //
    // waveData.fill(null)
    // response.fill(0)
    // var ratio: Float = 0f
    // var crc: Int = 0
    // var totalBagCount = 0
    // val dataLength = caiJiChangDu * 1024 * 2
    //
    // BleManager.getInstance().clearCharacterCallback(curBleDevice)
    // doSleep(50)
    // mainHandler.removeCallbacksAndMessages(null)
    // BleManager.getInstance().notify(curBleDevice, UUID_SERVICE, UUID_UP, object : BleNotifyCallback() {
    //   override fun onCharacteristicChanged(data: ByteArray?) {
    //     if (data == null || data.size < 5 || data[0] != HEAD_FROM_SENSOR) {
    //       //					BleMgr.mainHandler.post { callback.onEnd("采集失败, 返回格式有误") }
    //       //					BleMgr.mainHandler.removeCallbacksAndMessages(null)
    //       //					BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
    //       return
    //     }
    //     when (data[1]) {
    //       CMD_SAMPLING_PARAMS -> { // 收到下达测振的回复
    //         if (data.size != 10) {
    //           return
    //         }
    //         isReceivedSample.set(true)
    //         if (isTimeoutSample.get()) {
    //           //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
    //           //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //           return
    //         }
    //         if (data[4] != 1.toByte()) {
    //           mainHandler.post {
    //             callback.onFail("下达采集参数失败")
    //             mainHandler.removeCallbacksAndMessages(null)
    //             BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //           }
    //         }
    //         else {
    //           // 系数
    //           ratio = ByteUtil.getFloatFromByteArray(data, 5)
    //           // 等待 采集时间, 若还未收到波形数据, 则超时
    //           doRetry(0, {}, collectTime, 0, 5000, isReceivedWaveData, isTimeoutWaveData) {
    //             mainHandler.post {
    //               callback.onFail("等待波形数据超时")
    //               mainHandler.removeCallbacksAndMessages(null)
    //               BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //             }
    //           }
    //           // 等待 采集时间 + 传输时间, 若还未收到结果确认, 则超时
    //           doRetry(0, {}, collectTime + transferTime, 0, 5000, isReceivedResultFirst, isTimeoutResultFirst) {
    //             mainHandler.post {
    //               callback.onFail("等待结果确认超时")
    //               mainHandler.removeCallbacksAndMessages(null)
    //               BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //             }
    //           }
    //         }
    //       }
    //       CMD_SAMPLING_DATA_WAVE -> { // 收到测振数据
    //         if (data.size < 8 || data.size > 247) {
    //           //							BleMgr.mainHandler.post { callback.onEnd("采集失败, 返回格式有误") }
    //           return
    //         }
    //         isReceivedWaveData.set(true)
    //         if (isTimeoutWaveData.get()) {
    //           //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
    //           //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //           return
    //         }
    //         // 数据 CRC
    //         //						crc = ByteUtil.getIntFromByteArray(data, 4)
    //         //						val crcL: Long = crc.toLong() and 0xffffffffL
    //         // 数据长度
    //         //						dataLength = ByteUtil.getIntFromByteArray(data, 8)
    //         //						val dataLengthL: Long = dataLength.toLong() and 0xffffffffL
    //         // 系数
    //         //						ratio = ByteUtil.getFloatFromByteArray(data, 12)
    //         // 总包数
    //         //						totalBagCount = ByteUtil.getIntFromByteArray(data, 16)
    //         //						val totalBagCountL: Long = totalBagCount.toLong() and 0xffffffffL
    //         // 当前包编号
    //         //						val curBagNum: Int = ByteUtil.getIntFromByteArray(data, 20)
    //         //						val curBagNumL: Long = curBagNum.toLong() and 0xffffffffL
    //         val curBagNum: Int = ByteUtil.getShortFromByteArray(data, 4).toInt()
    //         val bytes = Arrays.copyOfRange(data, 6, data.size - 1)
    //         waveData[curBagNum] = bytes
    //       }
    //       CMD_WAVE_DATA_RESULT -> {
    //         if (data.size != 9) {
    //           //							BleMgr.mainHandler.post { callback.onEnd("格式有误") }
    //           return
    //         }
    //         isReceivedResultFirst.set(true)
    //         if (isTimeoutResultFirst.get()) {
    //           //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
    //           //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //           return
    //         }
    //         isReceivedResultOthers.set(true)
    //         if (isTimeoutResultOthers.get()) {
    //           //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
    //           //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //           return
    //         }
    //         totalBagCount = ByteUtil.getIntFromByteArray(data, 4)
    //         var isEnd = true
    //         for (i in 0 until totalBagCount) {
    //           if (waveData[i] != null) {
    //             updateBit(response, i)
    //           }
    //           else {
    //             isEnd = false
    //           }
    //         }
    //         if (isEnd) {
    //           val bytes: MutableList<Byte> = mutableListOf()
    //           for (i in 0 until totalBagCount) {
    //             waveData[i]!!.forEach { bytes.add(it) }
    //           }
    //           val realBytes = ByteArray(dataLength)
    //           for (i in 0 until dataLength) {
    //             realBytes[i] = bytes[i]
    //           }
    //           val shorts: ShortArray = ByteUtil.bytesToShorts(realBytes)
    //           val res = addHeadCmdLengthAndCs(response, CMD_WAVE_DATA_RESULT)
    //           doSleep(50)
    //           write(res)
    //           mainHandler.post {
    //             callback.onReceiveVibData(shorts, ratio)
    //             mainHandler.removeCallbacksAndMessages(null)
    //             BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
    //           }
    //         }
    //         else {
    //           doRetry(50L, {
    //             val res = addHeadCmdLengthAndCs(response, CMD_WAVE_DATA_RESULT)
    //             write(res)
    //           }, transferTime, 2, 5000, isReceivedResultOthers, isTimeoutResultOthers) {
    //             mainHandler.post {
    //               callback.onFail("回复结果超时")
    //               mainHandler.removeCallbacksAndMessages(null)
    //               BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //             }
    //           }
    //         }
    //       }
    //     }
    //   }
    //
    //   override fun onNotifyFailure(exception: BleException?) {
    //     mainHandler.post {
    //       callback.onFail(exception?.description ?: "notify 失败")
    //       mainHandler.removeCallbacksAndMessages(null)
    //       BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //     }
    //   }
    //
    //   override fun onNotifySuccess() {
    //     doRetry(50, {
    //       val data = bytesSetSampleParams(
    //         2, caiJiChangDu, 0, 0, fenXiPinLv,
    //         0, 0, 0, 0, 0, 0,
    //         0, 0, 0, 0, 0f
    //       )
    //       write(data)
    //     }, 0, 2, 400, isReceivedSample, isTimeoutSample) {
    //       mainHandler.post {
    //         callback.onFail("下达采集参数超时")
    //         mainHandler.removeCallbacksAndMessages(null)
    //         BleManager.getInstance().clearCharacterCallback(curBleDevice)
    //       }
    //     }
    //   }
    // })
  }

  /**
   * ok
   * 下达系统参数
   */
  fun setSystemParams(data: Encoder, callback: BleInterfaces.ActionCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isTimeout = AtomicBoolean(false)
    val isReceived = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_set_system_params", data)
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        if (data != null && data.size == 6 && data[4] == 1.toByte() && data[0] == HEAD_FROM_SENSOR && data[1] == CMD_SET_SYSTEM_PARAMS) {
          callback?.onSuccess()
        }
        else {
          callback?.onFail("下达系统参数失败")
        }
      }
      doTimeout(200L, {
        write(data.encode())
      }, 200L, 2, 500, isReceived, isTimeout) {
        callback?.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * ok
   * 获取系统参数
   */
  fun getSystemParams(decoder: Decoder<SystemParamsAdapter>, callback: GetSystemParamsCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_get_system_params", data)
        isReceived.set(true)
        if (isTimeout.get()) {
          callback.onFail(null)
          return@notify
        }
        try {
          val d = decoder.decode(data)
          callback.onCallback(d)
        } catch (e: Exception) {
          callback.onFail("获取系统参数失败")
          return@notify
        }
      }
      doTimeout(200L, {
        val data = byteArrayOf().pack(HEAD_TO_SENSOR, CMD_GET_SYSTEM_PARAMS)
        dTag("getSystemParams_send", data)
        write(data)
      }, 200L, 2, 500, isReceived, isTimeout) {
        callback.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * ok
   * 硬件自检测
   * ok
   */
  fun selfCheck(decoder: Decoder<SelfCheckAdapter>, callback: SelfCheckCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_self_check", data)
        isReceived.set(true)
        if (isTimeout.get()) {
          callback.onFail(null)
          return@notify
        }
        try {
          val d = decoder.decode(data)
          callback.onCallback(d)
        } catch (e: Exception) {
          callback.onFail("硬件自检测失败")
          return@notify
        }
      }
      doTimeout(200L, {
        val data = byteArrayOf().pack(HEAD_TO_SENSOR, CMD_SELF_CHECK)
        dTag("self_check_send", data)
        write(data)
      }, 200L, 2, 500, isReceived, isTimeout) {
        callback.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * ok
   * 振动校准
   */
  fun calibrate(encoder: CalibrationVibrationAdapter, callback: BleInterfaces.CalibrationCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_calibration", data)
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        if (data == null || data.size < 9 || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_CALIBRATION_VIBRATION) {
          callback?.onFail("振动校准失败")
          return@notify
        }
        val coe: Float = ByteUtil.getFloatFromByteArray(data, 4)
        callback?.onCallback(coe)
      }
      val len = encoder.len // 单位 K
      val freq = encoder.freq // 单位 100Hz
      // 校准需要的时间(s)为 长度(K) * 1024 / 频率(HZ) / 2.56, 预留 1000 ms
      val timeout = len * 1024 / (freq * 100) / 2.56 * 1000 + 1000
      val action = {
        val data = encoder.encode()
        dTag("calibration_data", data)
        write(data)
      }
      doTimeout(200L, action, timeout.toLong(), 1, retryTimeout = timeout.toInt(),
        isTimeout = isTimeout, isReceived = isReceived, toDoWhenTimeout = {
        callback?.onFail("超时")
      })
    }
  }

  private const val UPGRADE_BAG_SIZE = 236

  /**
   * ok
   * 升级
   * @param sn sn 号
   * @param byteArray 升级数据
   */
  fun upgrade(sn: Int, byteArray: ByteArray?, callback: BleInterfaces.UpgradeCallback) {
    if (!isConnected()) {
      callback.onUpgradeResult(false, TIP_DISCONNECT)
      return
    }
    if (byteArray == null) {
      callback.onUpgradeResult(false, "升级文件不存在, 请重新下载升级文件")
      return
    }
    val isTimeoutPrepare = AtomicBoolean(false)
    val isReceivedPrepare = AtomicBoolean(false)
    val isTimeoutResult = AtomicBoolean(false)
    val isReceivedResult = AtomicBoolean(false)

    val bytesUpgrade = byteArray
    val snB: ByteArray = ByteUtil.intToBytes(sn)
    val length: Int = bytesUpgrade.size
    val lengthB: ByteArray = ByteUtil.intToBytes(length)
    val crc: Int = ByteUtil.computeCRC32(bytesUpgrade)
    val crcB: ByteArray = ByteUtil.intToBytes(crc)
    val crcStr = ByteUtil.parseByte2HexStr(crcB)
    dTag("upgrade_crc_hex", crcStr)

    val totalBagCount = length.toBigDecimal().divide(UPGRADE_BAG_SIZE.toBigDecimal(), 0, BigDecimal.ROUND_UP).toInt()
    if (totalBagCount < 1) {
      mainHandler.post { callback.onUpgradeResult(false, "升级文件出错, 请重新下载升级文件") }
      return
    }
    val response = byteArrayOf().pack(HEAD_TO_SENSOR, RH205Consts.CMD_UPGRADE_DATA_RESULT)
    val bagCountB: ByteArray = ByteUtil.intToBytes(totalBagCount)
    val dataOrigin = ByteArray(16)
    val transferInterval = 100L // 每包的传输间隔
    val maxRetryCount = 50 // 最大重传次数
    var mills = 0L // 用来计时用的
    val retryCount = AtomicInteger(0) // 用来统计重传次数的
    System.arraycopy(snB, 0, dataOrigin, 0, 4)
    System.arraycopy(lengthB, 0, dataOrigin, 4, 4)
    System.arraycopy(crcB, 0, dataOrigin, 8, 4)
    System.arraycopy(bagCountB, 0, dataOrigin, 12, 4)
    val dataPrepare = dataOrigin.pack(HEAD_TO_SENSOR, RH205Consts.CMD_PREPARE_UPGRADE)

    /**
     * 传送升级文件
     */
    fun upgradeData(_bagIndex: Int) {
      mainHandler.post {
        if (mills == 0L) {
          mills = System.currentTimeMillis()
        }
        dTag("upgrade_index", _bagIndex.toString())
        val bagIndexB = ByteUtil.intToBytes(_bagIndex)
        val upgradeBag: ByteArray = Arrays.copyOfRange(bytesUpgrade, _bagIndex * UPGRADE_BAG_SIZE, (_bagIndex + 1) * UPGRADE_BAG_SIZE)
        val upgradeDataOrigin = Arrays.copyOf(bagIndexB, bagIndexB.size + upgradeBag.size)
        System.arraycopy(upgradeBag, 0, upgradeDataOrigin, 4, upgradeBag.size)
        // addHeadCmdLengthAndCs(upgradeDataOrigin, CMD_UPGRADE_DATA)
        val upgradeData =upgradeDataOrigin.pack(HEAD_TO_SENSOR, RH205Consts.CMD_UPGRADE_DATA)

        dTag("upgradeX", "start send $_bagIndex")
        write(upgradeData, object : BleWriteCallback() {
          override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
            dTag("upgradeX", "send $_bagIndex success")
          }

          override fun onWriteFailure(exception: BleException?) {
            dTag("upgradeX", "send $_bagIndex fail_${exception?.description}")
          }
        })
      }
    }

    fun notifyResult() {
      notify { data ->
        dTag("notify_upgrade_result", data?.toString())
        isReceivedResult.set(true)
        if (isTimeoutResult.get()) return@notify
        if (data != null && data.size == 125 && data[0] == HEAD_FROM_SENSOR && data[1] == RH205Consts.CMD_UPGRADE_DATA_RESULT) {
          val bytesResult = Arrays.copyOfRange(data, 4, 124)
          dTag("upgrade_result", ByteUtil.bytes2BitStr(bytesResult))
          val indexsResult = getBagIndexFromBit(bytesResult, totalBagCount)
          if (indexsResult.isNotEmpty()) {
            singleExecutor.submit {
              retryCount.getAndIncrement()
              dTag("升级重试", "第${retryCount.get()}次")
              if (retryCount.get() > maxRetryCount) {
                mainHandler.post {
                  callback.onUpgradeResult(false, "升级失败, 请重试")
                  mainHandler.removeCallbacksAndMessages(null)
                  BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
                }
                return@submit
              }
              doSleep(200)
              indexsResult.forEach {
                doSleep(transferInterval)
                upgradeData(it)
              }
              doRetry(200, { write(response) }, 0, 2, 5000, isReceivedResult, isTimeoutResult) {
                mainHandler.post {
                  callback.onUpgradeResult(false, "请求结果1超时, 升级失败")
                  mainHandler.removeCallbacksAndMessages(null)
                  BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
                }
              }
            }
          }
          else {
            val time = System.currentTimeMillis() - mills
            dTag("升级耗时", (time / 1000).toString())
            mainHandler.post { callback.onUpgradeResult(true, "升级成功") }
          }
        }
        else {
          dTag("upgradeResult", "失败")
          mainHandler.post {
            callback.onUpgradeResult(false, "升级失败, 返回格式有误")
            mainHandler.removeCallbacksAndMessages(null)
            BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          }
        }
      }
    }

    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    curFuture = singleExecutor.submit {
      notify { data ->
        dTag("notify_prepare_upgrade", data?.toString())
        isReceivedPrepare.set(true)
        if (isTimeoutPrepare.get()) return@notify
        if (data == null || data.size != 6 || data[4] != 0x01.toByte() || data[0] != HEAD_FROM_SENSOR || data[1] != RH205Consts.CMD_PREPARE_UPGRADE) {
          mainHandler.post { callback.onUpgradeResult(false, "准备升级失败") }
        }
        else {
          singleExecutor.submit {
            doSleep(200)
            notifyResult()
            doSleep(200)
            mills = System.currentTimeMillis()
            for (i in 0 until totalBagCount) {
              doSleep(transferInterval)
              upgradeData(i)
            }
            doRetry(200, { write(response) }, 0, 2, 1000, isReceivedResult, isTimeoutResult) {
              mainHandler.post {
                callback.onUpgradeResult(false, "请求结果0超时, 升级失败")
                mainHandler.removeCallbacksAndMessages(null)
                BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
              }
            }
          }
        }
      }
      doRetry(200, { write(dataPrepare) }, 0, 2, 5000, isReceivedPrepare, isTimeoutPrepare) {
        mainHandler.post {
          callback.onUpgradeResult(false, "升级失败, 超时")
          mainHandler.removeCallbacksAndMessages(null)
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
        }
      }
    }
  }
}