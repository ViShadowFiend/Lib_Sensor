package com.ronds.eam.lib_sensor

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleMtuChangedCallback
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.ronds.eam.lib_sensor.BleInterfaces.ActionCallback
import com.ronds.eam.lib_sensor.BleInterfaces.CalibrationCallback
import com.ronds.eam.lib_sensor.BleInterfaces.ConnectStatusCallback
import com.ronds.eam.lib_sensor.BleInterfaces.DisconnectCallback
import com.ronds.eam.lib_sensor.BleInterfaces.GetSystemParamsCallback
import com.ronds.eam.lib_sensor.BleInterfaces.GetTemperatureCalibrationCoefficientCallback
import com.ronds.eam.lib_sensor.BleInterfaces.GetTemperatureLinearCoefficientCallback
import com.ronds.eam.lib_sensor.BleInterfaces.SampleTempCallback
import com.ronds.eam.lib_sensor.BleInterfaces.SampleVibCallback
import com.ronds.eam.lib_sensor.BleInterfaces.ScanCallback
import com.ronds.eam.lib_sensor.BleInterfaces.UpgradeCallback
import com.ronds.eam.lib_sensor.utils.ByteUtil
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
import kotlin.concurrent.thread
import kotlin.experimental.or

/**
 * 蓝牙 ble manager
 *
 * @author An.Wang 2019/4/25 17:40
 */
object BleMgr {
  private const val TAG = "BleMgr"
  private var isDebug: Boolean = false

  const val TIMEOUT = 1000 * 10L

  private const val TIP_DISCONNECT = "当前未与516连接, 请连接516后重试"
  private const val TIP_TIMEOUT = "响应超时, 请检查与516的连接"

  private const val UUID_SERVICE = "11111111-1111-1111-1111-100000000000"
  private const val UUID_DOWN = "11111111-1111-1111-1111-100000000001"
  private const val UUID_UP = "11111111-1111-1111-1111-100000000002"

  private const val HEAD_TO_SENSOR = 0x50.toByte()
  private const val HEAD_FROM_SENSOR = 0xA0.toByte()

  private const val CMD_SAMPLING_PARAMS = 0x01.toByte() // 采样参数
  private const val CMD_DATA_NUM = 0x02.toByte() // 数据条数
  private const val CMD_DATA_INFO = 0x03.toByte() // 数据信息
  private const val CMD_SAMPLING_DATA_WAVE = 0x04.toByte() // 采样数据波形
  private const val CMD_WAVE_DATA_RESULT = 0x14.toByte() // 波形传输结果
  private const val CMD_SAMPLING_DATA_TEMP = 0x05.toByte() // 采样数据温度
  private const val CMD_SET_SYSTEM_PARAMS = 0x06.toByte() // 设置系统参数
  private const val CMD_GET_SYSTEM_PARAMS = 0x0c.toByte() // 获取系统参数
  private const val CMD_PREPARE_UPGRADE = 0x07.toByte() // 准备升级
  private const val CMD_UPGRADE_DATA = 0x08.toByte() // 升级
  private const val CMD_UPGRADE_DATA_RESULT = 0x18.toByte() // 升级包传输状态
  private const val CMD_CALIBRATION_VIBRATION = 0x09.toByte() // 振动校准
  private const val CMD_GET_TEMPERATURE_CALIBRATION_COEFFICIENT = 0x0A.toByte() // 获取温度校准系数
  private const val CMD_SET_TEMPERATURE_CALIBRATION_COEFFICIENT = 0x0B.toByte() // 设置温度校准系数
  private const val CMD_GET_TEMPERATURE_LINEAR_COEFFICIENT = 0x0D.toByte() // 获取温度线性系数
  private const val CMD_SET_TEMPERATURE_LINEAR_COEFFICIENT = 0x0E.toByte() // 设置温度线性系数

  private val mainHandler = Handler(Looper.getMainLooper())

  val bleDevices = mutableListOf<BleDevice>()
  var curBleDevice: BleDevice? = null

  private var curFuture: Future<*>? = null
  private val singleExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  var sdf_ = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())

  var isScanning = false

  var bytesWave: ByteArray? = null // 波形原始数据 byte[]

  var mainVersionSensor: Int? = null // 传感器当前的主版本号
  var mainVersionRemote: Int? = null // 服务器获取的的主版本号
  var tempVersionSensor: Int? = null // 传感器当前的测温模块版本号
  var tempVersionRemote: Int? = null // 服务器获取的测温模块版本号

  val ruleBuilder: BleScanRuleConfig.Builder = BleScanRuleConfig.Builder()
      //      .setServiceUuids(serviceUuids)      // 只扫描指定的服务的设备，可选
      //      .setDeviceName(true, names)         // 只扫描指定广播名的设备，可选
      //      .setDeviceMac(mac)                  // 只扫描指定mac的设备，可选
      //      .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
      .setScanTimeOut(TIMEOUT)              // 扫描超时时间，可选，默认10秒

  private val isRunning = AtomicBoolean(false) // 标识ble设备是否执行命令
  private var runningStatus: String? = null // 描述当前 ble 设备正在执行的命令

  /**
   * 初始化
   *
   * @param application
   */
  fun init(application: Application) {
    isDebug = BuildConfig.DEBUG
    BleManager.getInstance().run {
      init(application)
      enableLog(isDebug)
      setReConnectCount(1, 5000)
      operateTimeout = 20 * 1000
      initScanRule(ruleBuilder.build())
    }
  }

  fun scan(scanCallback: ScanCallback?) {
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      mainHandler.post {
        scanCallback?.onScanStart()
      }
      doSleep(1000)
      BleManager.getInstance().scan(object : BleScanCallback() {
        override fun onScanFinished(scanResultList: MutableList<BleDevice>?) {
          isScanning = false
          mainHandler.post { scanCallback?.onScanEnd() }
        }

        override fun onScanStarted(success: Boolean) {
          if (success) isScanning = true
          bleDevices.clear()
          val connectedDevices = getAllConnectDevices()
          bleDevices.addAll(connectedDevices)
          mainHandler.post { scanCallback?.onScanResult(bleDevices) }
        }

        override fun onScanning(bleDevice: BleDevice?) {
          if (bleDevice?.name?.startsWith("RH516_", true) == true) {
            bleDevices.add(bleDevice)
            mainHandler.post { scanCallback?.onScanResult(bleDevices) }
          }
        }
      })
    }
  }

  fun stopScan() {
    isScanning = false
    singleExecutor.submit {
      try {
        doSleep(1000)
        BleManager.getInstance().cancelScan()
        doSleep(1000)
      }
      catch (e: Exception) {
        dTag("stop_scan", e)
      }
    }
  }

  fun isConnected(bleDevice: BleDevice?): Boolean {
    return bleDevice != null && BleManager.getInstance().isConnected(bleDevice)
  }

  fun isConnected(): Boolean {
    return isConnected(curBleDevice)
  }

  fun isConnected(mac: String?): Boolean {
    return mac != null && BleManager.getInstance().isConnected(mac)
  }

  fun disConnectAllDevices(disconnectCallback: DisconnectCallback?) {
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      mainHandler.post { disconnectCallback?.onDisconnectStart() }
      doSleep(1000)
      BleManager.getInstance().disconnectAllDevice()
      doSleep(1000)
      mainHandler.post { disconnectCallback?.onDisconnectEnd() }
    }
  }

  fun disconnect(bleDevice: BleDevice?, disconnectCallback: DisconnectCallback?) {
    singleExecutor.submit {
      mainHandler.post {
        disconnectCallback?.onDisconnectStart()
      }
      doSleep(1000)
      if (!isConnected(bleDevice)) {
        mainHandler.post {
          disconnectCallback?.onDisconnectEnd()
        }
        return@submit
      }
      BleManager.getInstance().disconnect(bleDevice)
      doSleep(1000)
      mainHandler.post {
        disconnectCallback?.onDisconnectEnd()
      }
    }
  }

  fun getAllConnectDevices(): MutableList<BleDevice> {
    return BleManager.getInstance().allConnectedDevice ?: mutableListOf()
  }

  fun connect(bleDevice: BleDevice?, connectStatusCallback: ConnectStatusCallback?) {
    if (bleDevice == null) {
      connectStatusCallback?.onConnectFail(null, "ble 设备为空")
      return
    }
    curFuture?.cancel(true)
    mainHandler.removeCallbacksAndMessages(null)
    //		disposableReconnect?.dispose()
    curFuture = singleExecutor.submit {
      if (isConnected()) {
        mainHandler.post {
          connectStatusCallback?.onConnectSuccess(curBleDevice)
        }
        return@submit
      }
      doSleep(300)
      if (curBleDevice == null) {
        curBleDevice = bleDevice
      }
      //			isReconnecting.set(false)
      //			canReconnect.set(false)
      BleManager.getInstance().connect(curBleDevice, object : BleGattCallback() {
        override fun onStartConnect() {
          //					toast("正在连接")
          connectStatusCallback?.onConnectStart()
          dTag("connect", "开始连接${curBleDevice?.mac}")
        }

        override fun onDisConnected(isActiveDisConnected: Boolean, device: BleDevice?, gatt: BluetoothGatt?, status: Int) {
          connectStatusCallback?.onDisconnected(device)
        }

        override fun onConnectSuccess(bleDevice: BleDevice?, gatt: BluetoothGatt?, status: Int) {
          //						canReconnect.set(true)
          singleExecutor.execute {
            curBleDevice = bleDevice
            doSleep(200)
            BleManager.getInstance().setMtu(curBleDevice, 250, object : BleMtuChangedCallback() {
              override fun onMtuChanged(mtu: Int) {
                dTag("setMtu_success", mtu)
              }

              override fun onSetMTUFailure(exception: BleException?) {
                dTag("setMtu_fail", exception?.description)
              }
            })
            doSleep(200)
            mainHandler.post {
              connectStatusCallback?.onConnectSuccess(bleDevice)
            }
          }
        }

        override fun onConnectFail(bleDevice: BleDevice?, exception: BleException?) {
          curBleDevice = null
          connectStatusCallback?.onConnectFail(bleDevice, exception?.description)
        }
      })
    }
  }

  fun isNeedUpgrade(sensorVersion: Int?, remoteVersion: Int?): Boolean {
    if (sensorVersion == null || remoteVersion == null) return false
    return remoteVersion > sensorVersion
  }

  private fun write(bleDevice: BleDevice?, uuid_service: String?, uuid_write: String?, write_data: ByteArray?, bleWriteCallback: BleWriteCallback?) {
    if (bleDevice == null || uuid_service == null || uuid_write == null || write_data == null) return
    BleManager.getInstance().write(bleDevice, uuid_service, uuid_write, write_data, false,
        object : BleWriteCallback() {
          override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray?) {
            bleWriteCallback?.onWriteSuccess(current, total, justWrite)
          }

          override fun onWriteFailure(exception: BleException?) {
            bleWriteCallback?.onWriteFailure(exception)
          }
        })
  }

  private fun write(write_data: ByteArray?, bleWriteCallback: BleWriteCallback? = null) {
    write(curBleDevice, UUID_SERVICE, UUID_DOWN, write_data, bleWriteCallback)
  }

  private fun read(bleDevice: BleDevice?, uuid_service: String?, uuid_read: String?, bleReadCallback: BleReadCallback?) {
    if (bleDevice == null) return
    BleManager.getInstance().read(bleDevice, uuid_service, uuid_read, object : BleReadCallback() {
      override fun onReadSuccess(data: ByteArray?) {
        bleReadCallback?.onReadSuccess(data)
      }

      override fun onReadFailure(exception: BleException?) {
        bleReadCallback?.onReadFailure(exception)
      }
    })
  }

  private fun addHeadCmdLengthAndCs(src: ByteArray?, _cmd: Byte): ByteArray {
    val originSize = src?.size ?: 0

    val head: Byte = HEAD_TO_SENSOR // head
    val cmd: Byte = _cmd // cmd
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
    val bytes = Arrays.copyOf(bytesWithoutCs, bytesWithoutCs.size + 1)
    bytes[bytes.size - 1] = cs

    return bytes
  }

  /**
   * @param _dingShiCaiJi  byte 是否定时采集 (0-不采集, 1-启动定时采集, 2-临时采集)
   * @param _caiJiChangDu1 int 第 1 组测量定义采集长度 AD点(1K、2K、4K、8K、16K、32K)
   * @param _caiJiChangDu2 int 第 2 组测量定义采集长度 AD点(1K、2K、4K、8K、16K、32K)
   * @param _caiJiChangDu3 int 第 3 组测量定义采集长度 AD点(1K、2K、4K、8K、16K、32K)
   * @param _fenXiPinLv1 int 第 1 组测量定义分析频率 HZ（500、1000、2000、5000、10000、20000）
   * @param _fenXiPinLv2 int 第 2 组测量定义分析频率 HZ（500、1000、2000、5000、10000、20000）
   * @param _fenXiPinLv3 int 第 3 组测量定义分析频率 HZ（500、1000、2000、5000、10000、20000）
   * @param _zhouQi int 测量定义周期 分钟（1、5、10、1小时、2小时、4小时、8小时、12小时、24小时）
   * @param _changBoXingCaiJiChangDu int 长波形采集长度 AD点（128K、256K、512K）
   * @param _changBoXingFenXiPinLv int 长波形分析频率 HZ（500、1000、2000、5000、10000、20000
   * @param _changBoXingZhouQi int 长波形周期 小时（1、2、4、8、12、24、48、72）
   * @param _caiYangZhiCaiJi byte 是否进行采样值采集, (0-不采集, 1-温度临时采集, 2-指标临时采集, 3-定时采集, 4-停止温度采集)
   * @param _caiYangZhiCaiJiChangDu int 采样值采集长度 AD点（1K、2K、4K）
   * @param _caiYangZhiFenXiPinLv int 采样值分析频率 HZ（500、1000、2000、5000、10000、20000）
   * @param _caiYangZhiZhouQi int 采样值周期 分钟（1、2、5、10、30）
   * @param _ceWenFaSheLv float 测温发射率， 默认 0.97
   * 获取采集参数包装后的 byte[]
   */
  private fun bytesSetSampleParams(
    _dingShiCaiJi: Byte, _caiJiChangDu1: Int, _caiJiChangDu2: Int, _caiJiChangDu3: Int, _fenXiPinLv1: Int, _fenXiPinLv2: Int, _fenXiPinLv3: Int,
    _zhouQi: Int, _changBoXingCaiJiChangDu: Int, _changBoXingFenXiPinLv: Int, _changBoXingZhouQi: Int, _caiYangZhiCaiJi: Byte, _caiYangZhiCaiJiChangDu: Int,
    _caiYangZhiFenXiPinLv: Int, _caiYangZhiZhouQi: Int, _ceWenFaSheLv: Float = 0.97f
  ): ByteArray {
    val date = Date()
    val dateStr = sdf_.format(date)
    val dateStrArr = dateStr.split("_")
    // 系统时间
    val year: Short = dateStrArr[0].toShort() // 年
    val month: Byte = dateStrArr[1].toByte() // 月
    val day: Byte = dateStrArr[2].toByte() // 日
    val hour: Byte = dateStrArr[3].toByte() // 时
    val min: Byte = dateStrArr[4].toByte() // 分
    val second: Byte = dateStrArr[5].toByte() // 秒
    // 是否定时采集 0-不采集, 1-启动定时采集, 2-临时采集
    val dingShiCaiJi: Byte = _dingShiCaiJi
    // 测量定义采集长度, 3 组, 1K
    val caiJiChangdu1: Int = _caiJiChangDu1
    val caiJiChangdu2: Int = _caiJiChangDu2
    val caiJiChangdu3: Int = _caiJiChangDu3
    // 测量定义分析频率, 3 组, hz
    val fenXiPinLv1: Int = _fenXiPinLv1
    val fenXiPinLv2: Int = _fenXiPinLv2
    val fenxiPinLv3: Int = _fenXiPinLv3
    // 测量定义周期, 分钟
    val ceLiangDingYiZhouQi: Int = _zhouQi
    // 长波形采集长度, 128K / 256K / 512K
    val changBoXingCaiJiChangDu: Int = _changBoXingCaiJiChangDu
    // 长波形分析频率, hz
    val changBoXingFenXiPinLv: Int = _changBoXingFenXiPinLv
    // 长波形周期, 小时
    val changBoXingZhouQi: Int = _changBoXingZhouQi
    // 是否进行采样值采集
    val caiYangZhiCaiJi = _caiYangZhiCaiJi
    // 采样值采集长度, 1K / 2K / 4K
    val caiYangZhiCaiJiChangDu: Int = _caiYangZhiCaiJiChangDu
    // 采样值分析频率, hz
    val caiYangZhiFenXiPinLv: Int = _caiYangZhiFenXiPinLv
    // 采样值周期, 分钟
    val caiYangZhiZhouQi: Int = _caiYangZhiZhouQi
    // 测温发射率
    val ceWenFaSheLv: Float = _ceWenFaSheLv

    val yearB = ByteUtil.shortToBytes(year) // 年
    val caiJiChangdu1B = ByteUtil.intToBytes(caiJiChangdu1) // 测量定义采集长度1
    val caiJiChangdu2B = ByteUtil.intToBytes(caiJiChangdu2) // 测量定义采集长度2
    val caiJiChangdu3B = ByteUtil.intToBytes(caiJiChangdu3) // 测量定义采集长度3
    val fenXiPinLv1B = ByteUtil.intToBytes(fenXiPinLv1) // 测量定义分析频率1
    val fenXiPinLv2B = ByteUtil.intToBytes(fenXiPinLv2) // 测量定义分析频率2
    val fenXiPinLv3B = ByteUtil.intToBytes(fenxiPinLv3) // 测量定义分析频率3
    val ceLiangDingYiZhouQiB = ByteUtil.intToBytes(ceLiangDingYiZhouQi) // 测量定义周期
    val changBoXingCaiJiChangDuB = ByteUtil.intToBytes(changBoXingCaiJiChangDu) // 长波形采集长度
    val changBoXingFenXiPinLvB = ByteUtil.intToBytes(changBoXingFenXiPinLv) // 长波形分析频率
    val changBoXingZhouQiB = ByteUtil.intToBytes(changBoXingZhouQi) // 长波形周期
    val caiYangZhiCaiJiChangDuB = ByteUtil.intToBytes(caiYangZhiCaiJiChangDu) // 采样值采集长度
    val caiYangZhiFenXiPinLvB = ByteUtil.intToBytes(caiYangZhiFenXiPinLv) // 采样值分析频率
    val caiYangZhiZhouQiB = ByteUtil.intToBytes(caiYangZhiZhouQi) // 采样值周期
    val ceWenFaSheLvB = ByteUtil.floatToBytes(ceWenFaSheLv) // 测温发射率

    val bytesOrigin = ByteArray(65)
    System.arraycopy(yearB, 0, bytesOrigin, 0, 2) // 年
    bytesOrigin[2] = month // 月
    bytesOrigin[3] = day // 日
    bytesOrigin[4] = hour // 时
    bytesOrigin[5] = min // 分
    bytesOrigin[6] = second // 秒
    bytesOrigin[7] = dingShiCaiJi // 是否定时采集
    System.arraycopy(caiJiChangdu1B, 0, bytesOrigin, 8, 4) // 测量定义采集长度1
    System.arraycopy(caiJiChangdu2B, 0, bytesOrigin, 12, 4) // 测量定义采集长度2
    System.arraycopy(caiJiChangdu3B, 0, bytesOrigin, 16, 4) // 测量定义采集长度3
    System.arraycopy(fenXiPinLv1B, 0, bytesOrigin, 20, 4) // 测量定义分析频率1
    System.arraycopy(fenXiPinLv2B, 0, bytesOrigin, 24, 4) // 测量定义分析频率2
    System.arraycopy(fenXiPinLv3B, 0, bytesOrigin, 28, 4) // 测量定义分析频率3
    System.arraycopy(ceLiangDingYiZhouQiB, 0, bytesOrigin, 32, 4) // 测量定义周期
    System.arraycopy(changBoXingCaiJiChangDuB, 0, bytesOrigin, 36, 4) // 长波形采集长度
    System.arraycopy(changBoXingFenXiPinLvB, 0, bytesOrigin, 40, 4) // 长波形分析频率
    System.arraycopy(changBoXingZhouQiB, 0, bytesOrigin, 44, 4) // 长波形周期
    bytesOrigin[48] = caiYangZhiCaiJi // 是否进行采样值采集
    System.arraycopy(caiYangZhiCaiJiChangDuB, 0, bytesOrigin, 49, 4) // 采样值采集长度
    System.arraycopy(caiYangZhiFenXiPinLvB, 0, bytesOrigin, 53, 4) // 采样值分析频率
    System.arraycopy(caiYangZhiZhouQiB, 0, bytesOrigin, 57, 4) // 采样值周期
    System.arraycopy(ceWenFaSheLvB, 0, bytesOrigin, 61, 4) // 测温发射率

    // 添加 head cmd length 和 cs
    return addHeadCmdLengthAndCs(bytesOrigin, CMD_SAMPLING_PARAMS)!!
  }

  // 是否正在测温
  private val isTempProcessing = AtomicBoolean(false)

  // 测温回调
  private var sampleTempCallback: SampleTempCallback? = null

  /**
   * 开始温度采集
   * @param ceWenFaSheLv 测温发射率
   */
  fun startSampleTemp(ceWenFaSheLv: Float = 0.97f, callback: SampleTempCallback) {
    sampleTempCallback = callback
    if (!isConnected()) {
      sampleTempCallback?.onFail(TIP_DISCONNECT)
      return
    }
    isTempProcessing.set(true)
    val response = addHeadCmdLengthAndCs(null, CMD_SAMPLING_DATA_TEMP)
    val isTimeoutSample = AtomicBoolean(false)
    val isReceivedSample = AtomicBoolean(false)
    val isTimeoutTemp = AtomicBoolean(false)
    val isReceivedTemp = AtomicBoolean(false)

    fun responseTemp() {
      singleExecutor.submit {
        isTimeoutTemp.set(false)
        isReceivedTemp.set(false)
        if (!isTempProcessing.get()) return@submit
        doTimeout(200L, {
          dTag("temp_516_send", response)
          write(response)
        }, 200L, 2, 500, isReceivedTemp, isTimeoutTemp) {
          mainHandler.post {
            if (isTempProcessing.get()) {
              sampleTempCallback?.onFail("采集失败, 超时")
            }
            isTempProcessing.set(false)
          }
        }
      }
    }

    fun notifyTemp() {
      singleExecutor.submit {
        doSleep(200)
        notify { data ->
          dTag("notify_temp", data)
          isReceivedTemp.set(true)
          if (!isTempProcessing.get()) return@notify
          if (isTimeoutTemp.get()) return@notify
          if (data == null || data.size != 16 || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_SAMPLING_DATA_TEMP) {
            mainHandler.post {
              if (isTempProcessing.get()) {
                sampleTempCallback?.onFail("采集失败, 返回格式有误")
              }
              isTempProcessing.set(false)
            }
            return@notify
          }
          val year: Short = ByteUtil.getShortFromByteArray(data, 4)
          val month: Byte = data[6]
          val day: Byte = data[7]
          val hour: Byte = data[8]
          val min: Byte = data[9]
          val second: Byte = data[10]
          val bytesTemp: ByteArray = Arrays.copyOfRange(data, 11, 15)
          val temp: Float = ByteUtil.bytesToFloat(bytesTemp)
          sampleTempCallback?.onReceiveTemp(temp)
          responseTemp()
        }
      }
    }

    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      notify { data ->
        dTag("notify_set_sample_params", data)
        if (data == null || data.size != 10) {
          return@notify
        }
        isReceivedSample.set(true)
        if (!isTempProcessing.get()) return@notify
        if (isTimeoutSample.get()) {
          mainHandler.removeCallbacksAndMessages(null)
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          return@notify
        }
        if (data[4] != 1.toByte()) {
          mainHandler.post {
            sampleTempCallback?.onFail("下达采集参数失败")
            mainHandler.removeCallbacksAndMessages(null)
          }
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
        }
        else {
          dTag("temp", "下达采集参数成功")
          notifyTemp()
          responseTemp()
        }
      }
      val action: () -> Unit = {
        val data = bytesSetSampleParams(
            0x00.toByte(), 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 1, 0, 0, 0, ceWenFaSheLv
        )
        write(data)
      }

      doTimeout(
          200L, action, 0, 2, 500, isReceivedSample,
          isTimeoutSample
      ) {
        mainHandler.post {
          if (isTempProcessing.get()) {
            sampleTempCallback?.onFail("下达温度采集参数失败, 超时")
          }
          isTempProcessing.set(false)
        }
      }
    }
  }

  fun removeSampleTempCallback() {
    sampleTempCallback = null
  }

  /**
   * 停止温度采集
   */
  fun stopSampleTemp(callback: ActionCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    isTempProcessing.set(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("stop_temp_516_receive", ByteUtil.byteArray2HexString(data))
        if (data == null || data.size != 10) {
          return@notify
        }
        isReceived.set(true)
        if (isTimeout.get()) {
          mainHandler.removeCallbacksAndMessages(null)
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          return@notify
        }
        if (data[4] != 1.toByte()) {
          mainHandler.post {
            callback.onFail("停止测温失败")
            mainHandler.removeCallbacksAndMessages(null)
          }
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
        }
        else {
          mainHandler.post {
            callback.onSuccess()
            mainHandler.removeCallbacksAndMessages(null)
          }
          BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
        }
      }
      doTimeout(200L, {
        val data = bytesSetSampleParams(
            0x00.toByte(), 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 4, 0, 0, 0, 0f
        )
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

  // 收到的每包数据, 与 516 的协议规定, 振动数据不超过 320 包
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
  fun sampleVib(caiJiChangDu: Int, fenXiPinLv: Int, callback: SampleVibCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }

    val collectTime = (caiJiChangDu * 1024 / fenXiPinLv / 2.56 * 1000).toLong()
    // caiJiChangDu * 1024 为多少个点, * 2 因为每个点(short) 2个字节, / 4 是预估传输速度为 4b/ms
    val transferTime = (caiJiChangDu * 1024 * 2 / 4).toLong()

    val isTimeoutSample = AtomicBoolean(false)
    val isReceivedSample = AtomicBoolean(false)
    val isTimeoutResultFirst = AtomicBoolean(false)
    val isReceivedResultFirst = AtomicBoolean(false)
    val isTimeoutResultOthers = AtomicBoolean(false)
    val isReceivedResultOthers = AtomicBoolean(false)
    val isTimeoutWaveData = AtomicBoolean(false)
    val isReceivedWaveData = AtomicBoolean(false)

    waveData.fill(null)
    response.fill(0)
    var ratio: Float = 0f
    var crc: Int = 0
    var totalBagCount = 0
    val dataLength = caiJiChangDu * 1024 * 2

    BleManager.getInstance().clearCharacterCallback(curBleDevice)
    doSleep(50)
    mainHandler.removeCallbacksAndMessages(null)
    BleManager.getInstance().notify(curBleDevice, UUID_SERVICE, UUID_UP, object : BleNotifyCallback() {
      override fun onCharacteristicChanged(data: ByteArray?) {
        if (data == null || data.size < 5 || data[0] != HEAD_FROM_SENSOR) {
          //					BleMgr.mainHandler.post { callback.onEnd("采集失败, 返回格式有误") }
          //					BleMgr.mainHandler.removeCallbacksAndMessages(null)
          //					BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
          return
        }
        when (data[1]) {
          CMD_SAMPLING_PARAMS -> { // 收到下达测振的回复
            if (data.size != 10) {
              return
            }
            isReceivedSample.set(true)
            if (isTimeoutSample.get()) {
              //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
              //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
              return
            }
            if (data[4] != 1.toByte()) {
              mainHandler.post {
                callback.onFail("下达采集参数失败")
                mainHandler.removeCallbacksAndMessages(null)
                BleManager.getInstance().clearCharacterCallback(curBleDevice)
              }
            }
            else {
              // 系数
              ratio = ByteUtil.getFloatFromByteArray(data, 5)
              // 等待 采集时间, 若还未收到波形数据, 则超时
              doRetry(0, {}, collectTime, 0, 5000, isReceivedWaveData, isTimeoutWaveData) {
                mainHandler.post {
                  callback.onFail("等待波形数据超时")
                  mainHandler.removeCallbacksAndMessages(null)
                  BleManager.getInstance().clearCharacterCallback(curBleDevice)
                }
              }
              // 等待 采集时间 + 传输时间, 若还未收到结果确认, 则超时
              doRetry(0, {}, collectTime + transferTime, 0, 5000, isReceivedResultFirst, isTimeoutResultFirst) {
                mainHandler.post {
                  callback.onFail("等待结果确认超时")
                  mainHandler.removeCallbacksAndMessages(null)
                  BleManager.getInstance().clearCharacterCallback(curBleDevice)
                }
              }
            }
          }
          CMD_SAMPLING_DATA_WAVE -> { // 收到测振数据
            if (data.size < 8 || data.size > 247) {
              //							BleMgr.mainHandler.post { callback.onEnd("采集失败, 返回格式有误") }
              return
            }
            isReceivedWaveData.set(true)
            if (isTimeoutWaveData.get()) {
              //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
              //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
              return
            }
            // 数据 CRC
            //						crc = ByteUtil.getIntFromByteArray(data, 4)
            //						val crcL: Long = crc.toLong() and 0xffffffffL
            // 数据长度
            //						dataLength = ByteUtil.getIntFromByteArray(data, 8)
            //						val dataLengthL: Long = dataLength.toLong() and 0xffffffffL
            // 系数
            //						ratio = ByteUtil.getFloatFromByteArray(data, 12)
            // 总包数
            //						totalBagCount = ByteUtil.getIntFromByteArray(data, 16)
            //						val totalBagCountL: Long = totalBagCount.toLong() and 0xffffffffL
            // 当前包编号
            //						val curBagNum: Int = ByteUtil.getIntFromByteArray(data, 20)
            //						val curBagNumL: Long = curBagNum.toLong() and 0xffffffffL
            val curBagNum: Int = ByteUtil.getShortFromByteArray(data, 4).toInt()
            val bytes = Arrays.copyOfRange(data, 6, data.size - 1)
            waveData[curBagNum] = bytes
          }
          CMD_WAVE_DATA_RESULT -> {
            if (data.size != 9) {
              //							BleMgr.mainHandler.post { callback.onEnd("格式有误") }
              return
            }
            isReceivedResultFirst.set(true)
            if (isTimeoutResultFirst.get()) {
              //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
              //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
              return
            }
            isReceivedResultOthers.set(true)
            if (isTimeoutResultOthers.get()) {
              //							BleMgr.mainHandler.removeCallbacksAndMessages(null)
              //							BleManager.getInstance().clearCharacterCallback(curBleDevice)
              return
            }
            totalBagCount = ByteUtil.getIntFromByteArray(data, 4)
            var isEnd = true
            for (i in 0 until totalBagCount) {
              if (waveData[i] != null) {
                updateBit(response, i)
              }
              else {
                isEnd = false
              }
            }
            if (isEnd) {
              val bytes: MutableList<Byte> = mutableListOf()
              for (i in 0 until totalBagCount) {
                waveData[i]!!.forEach { bytes.add(it) }
              }
              val realBytes = ByteArray(dataLength)
              for (i in 0 until dataLength) {
                realBytes[i] = bytes[i]
              }
              val shorts: ShortArray = ByteUtil.bytesToShorts(realBytes)
              val res = addHeadCmdLengthAndCs(response, CMD_WAVE_DATA_RESULT)
              doSleep(50)
              write(res)
              mainHandler.post {
                callback.onReceiveVibData(shorts, ratio)
                mainHandler.removeCallbacksAndMessages(null)
                BleManager.getInstance().removeNotifyCallback(curBleDevice, UUID_UP)
              }
            }
            else {
              doRetry(50L, {
                val res = addHeadCmdLengthAndCs(response, CMD_WAVE_DATA_RESULT)
                write(res)
              }, transferTime, 2, 5000, isReceivedResultOthers, isTimeoutResultOthers) {
                mainHandler.post {
                  callback.onFail("回复结果超时")
                  mainHandler.removeCallbacksAndMessages(null)
                  BleManager.getInstance().clearCharacterCallback(curBleDevice)
                }
              }
            }
          }
        }
      }

      override fun onNotifyFailure(exception: BleException?) {
        mainHandler.post {
          callback.onFail(exception?.description ?: "notify 失败")
          mainHandler.removeCallbacksAndMessages(null)
          BleManager.getInstance().clearCharacterCallback(curBleDevice)
        }
      }

      override fun onNotifySuccess() {
        doRetry(50, {
          val data = bytesSetSampleParams(
              2, caiJiChangDu, 0, 0, fenXiPinLv,
              0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0f
          )
          write(data)
        }, 0, 2, 400, isReceivedSample, isTimeoutSample) {
          mainHandler.post {
            callback.onFail("下达采集参数超时")
            mainHandler.removeCallbacksAndMessages(null)
            BleManager.getInstance().clearCharacterCallback(curBleDevice)
          }
        }
      }
    })
  }

  /**
   * 下达系统参数
   * @param sn 传感器序列号
   * @param jiaSuDuXiShu 加速度系数
   * @param ceWenFaSheLv 测温发射率
   * @param daiJiShiChang 待机时长
   * @param yingJianBanBenHao 硬件版本号
   */
  fun setSystemParams(sn: Int, jiaSuDuXiShu: Float, ceWenFaSheLv: Float, daiJiShiChang: Int, yingJianBanBenHao: Float, callback: ActionCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isTimeout = AtomicBoolean(false)
    val isReceived = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
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
        val bytesOrigin = ByteArray(36)
        val snB = ByteUtil.intToBytes(sn)
        val xiShuB = ByteUtil.floatToBytes(jiaSuDuXiShu)
        val faSheLvB = ByteUtil.floatToBytes(ceWenFaSheLv)
        val timeB = ByteUtil.intToBytes(daiJiShiChang)
        val yingJianBanBenHaoB = ByteUtil.floatToBytes(yingJianBanBenHao)

        System.arraycopy(snB, 0, bytesOrigin, 0, 4)
        System.arraycopy(xiShuB, 0, bytesOrigin, 4, 4)
        System.arraycopy(faSheLvB, 0, bytesOrigin, 8, 4)
        System.arraycopy(timeB, 0, bytesOrigin, 12, 4)
        System.arraycopy(yingJianBanBenHaoB, 0, bytesOrigin, 16, 4)

        val data = addHeadCmdLengthAndCs(bytesOrigin, CMD_SET_SYSTEM_PARAMS)!!
        write(data)
      }, 200L, 2, 500, isReceived, isTimeout) {
        callback?.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 获取系统参数
   */
  fun getSystemParams(callback: GetSystemParamsCallback) {
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
        if (data == null || data.size < 41 || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_GET_SYSTEM_PARAMS) {
          callback.onFail("获取系统参数失败")
          return@notify
        }
        // sn
        val sn: Long = ByteUtil.getIntFromByteArray(data, 4).toLong() and 0xFFFFFFFFL
        // 加速度系数
        val jiaSuDuXiShu: Float = ByteUtil.getFloatFromByteArray(data, 8)
        // 测温发射率
        val ceWenFaSheLv: Float = ByteUtil.getFloatFromByteArray(data, 12)
        // 待机时长 （分钟）
        val daiJiShiChang: Long = ByteUtil.getIntFromByteArray(data, 16).toLong() and 0xFFFFFFFFL
        // 硬件版本号
        val yingJianBanBenHao: Float = ByteUtil.getFloatFromByteArray(data, 20)
        // 主版本号
        val zhuBanBenHao: Int = ByteUtil.getShortFromByteArray(data, 24).toInt() and 0xFFFF
        // 次版本号
        val ciBanBenHao: Int = data[26].toInt() and 0xFF
        mainVersionSensor = zhuBanBenHao * 100 + ciBanBenHao
        // 版本号: 以 . 隔开
        val banBenHao: String = "$zhuBanBenHao.$ciBanBenHao"
        // 温度模块主版本号
        val wenDuZhuBanBenHao: Int = ByteUtil.getShortFromByteArray(data, 27).toInt() and 0xFFFF
        // 温度模块次版本号
        val wenDuCiBanBenHao: Int = data[29].toInt() and 0xFF
        tempVersionSensor = wenDuZhuBanBenHao * 100 + wenDuCiBanBenHao
        // 温度模块版本号, 以 . 隔开
        val wenDuBanBenHao: String = "$wenDuZhuBanBenHao.$wenDuCiBanBenHao"

        val t = "SN: $sn\n" +
                "加速度系数: $jiaSuDuXiShu\n" +
                "测温发射率: $ceWenFaSheLv\n" +
                "待机时长: $daiJiShiChang\n" +
                "硬件版本号: $yingJianBanBenHao\n" +
                "版本号: $banBenHao\n" +
                "温度模块版本号: $wenDuBanBenHao"
        dTag("获取系统参数为", t)
        callback.onCallback(sn, jiaSuDuXiShu, ceWenFaSheLv, daiJiShiChang, yingJianBanBenHao, zhuBanBenHao, ciBanBenHao, wenDuZhuBanBenHao, wenDuCiBanBenHao)
      }
      doTimeout(200L, {
        val data = addHeadCmdLengthAndCs(null, CMD_GET_SYSTEM_PARAMS)
        dTag("getSystemParams_send", data)
        write(data)
      }, 200L, 2, 500, isReceived, isTimeout) {
        callback.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 振动校准
   */
  fun calibrate(_jiaoZhunCaiYangChangDu: Long, _jiaoZhunCaiYangPinLv: Long, callback: CalibrationCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
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
        val jiaoZhunXiShu: Float = ByteUtil.getFloatFromByteArray(data, 4)
        callback?.onCallback(jiaoZhunXiShu)
      }
      // 校准需要的时间(s)为 长度(K) * 1024 / 频率(HZ) / 2.56, 预留 1000 ms
      val timeout = _jiaoZhunCaiYangChangDu * 1024 / _jiaoZhunCaiYangPinLv / 2.56 * 1000 + 1000
      val action = {
        val jiaoZhunCaiYangChangDu: Int = (_jiaoZhunCaiYangChangDu and 0xFFFFFFFF).toInt()
        val jiaoZhunCaiYangPinLv: Int = (_jiaoZhunCaiYangPinLv and 0xFFFFFFFF).toInt()
        val jiaoZhunCaiYangChangDuB: ByteArray = ByteUtil.intToBytes(jiaoZhunCaiYangChangDu)
        val jiaoZhunCaiYangPinLvB: ByteArray = ByteUtil.intToBytes(jiaoZhunCaiYangPinLv)
        val dataOrigin = ByteArray(8)
        System.arraycopy(jiaoZhunCaiYangChangDuB, 0, dataOrigin, 0, 4)
        System.arraycopy(jiaoZhunCaiYangPinLvB, 0, dataOrigin, 4, 4)
        val data = addHeadCmdLengthAndCs(dataOrigin, CMD_CALIBRATION_VIBRATION)
        dTag("calibration_data", data)
        write(data)
      }
      doTimeout(200L, action, timeout.toLong(), 1, retryTimeout = timeout.toInt(),
          isTimeout = isTimeout, isReceived = isReceived, toDoWhenTimeout = {
        callback?.onFail("超时")
      })
    }
  }

  /**
   * 获取温度校准系数
   */
  fun getTemperatureCalibrationCoefficient(callback: GetTemperatureCalibrationCoefficientCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_get_temp_calibration_coefficient", data)
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        if (data == null || data.size < 21 || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_GET_TEMPERATURE_CALIBRATION_COEFFICIENT) {
          callback.onFail("读取温度校准系数失败")
          return@notify
        }
        // 偏移
        val pianYi: Float = ByteUtil.getFloatFromByteArray(data, 4)
        // 环境温度
        val huanJingWenDu: Float = ByteUtil.getFloatFromByteArray(data, 8)
        // 目标值
        val muBiaoZhi: Float = ByteUtil.getFloatFromByteArray(data, 12)

        val toast = "偏移: $pianYi\n" +
                    "环境温度: $huanJingWenDu\n" +
                    "目标值: $muBiaoZhi"
        dTag("读取温度校准系数", toast)
        callback.onCallback(pianYi, huanJingWenDu, muBiaoZhi)
      }
      doTimeout(200L, {
        val data = addHeadCmdLengthAndCs(null, CMD_GET_TEMPERATURE_CALIBRATION_COEFFICIENT)
        write(data)
      }, 200L, isReceived = isReceived, isTimeout = isTimeout) {
        callback.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 读取温度线性系数
   */
  fun getTemperatureLinearCoefficient(callback: GetTemperatureLinearCoefficientCallback) {
    if (!isConnected()) {
      callback.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      notify { data ->
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        dTag("notify_get_temp_linear_coefficient", data)
        if (data == null || data.size < 97 || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_GET_TEMPERATURE_LINEAR_COEFFICIENT) {
          callback.onFail("读取温度线性系数失败")
          return@notify
        }
        // 系数 1， 7 * float
        val xiShu1B: ByteArray = Arrays.copyOfRange(data, 4, 32)
        val xiShu1: FloatArray = ByteUtil.bytesToFloats(xiShu1B)
        val xiShu1Str: String = xiShu1.joinToString(",")
        // 补偿值， 7 * float
        val buChangZhiB: ByteArray = Arrays.copyOfRange(data, 32, 60)
        val buChangZhi: FloatArray = ByteUtil.bytesToFloats(buChangZhiB)
        val buChangZhiStr: String = buChangZhi.joinToString(",")
        // 系数 2， 7 * float
        val xiShu2B: ByteArray = Arrays.copyOfRange(data, 60, 88)
        val xiShu2: FloatArray = ByteUtil.bytesToFloats(xiShu2B)
        val xiShu2Str: String = xiShu2.joinToString(",")
        val toast = "系数1: $xiShu1Str\n" +
                    "补偿值: $buChangZhiStr\n" +
                    "系数2: $xiShu2Str"
        dTag("读取温度线性系数", toast)
        callback.onCallback(xiShu1, xiShu2, buChangZhi)
      }
      doTimeout(200L, {
        val data = addHeadCmdLengthAndCs(null, CMD_GET_TEMPERATURE_LINEAR_COEFFICIENT)
        write(data)
      }, 200L, isTimeout = isTimeout, isReceived = isReceived) {
        callback.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 写温度校准系数
   */
  fun setTemperatureCalibrationCoefficient(pianYi: Float, huanJingWenDu: Float, muBiaoZhi: Float, callback: ActionCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_set_temp_calibration_coefficient", data)
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        if (data != null && data.size == 6 && data[4] == 0x01.toByte() && data[0] == HEAD_FROM_SENSOR && data[1] == CMD_SET_TEMPERATURE_CALIBRATION_COEFFICIENT) {
          callback?.onSuccess()
        }
        else {
          callback?.onFail("设置温度校准系数失败")
        }
      }
      doTimeout(200L, {
        val pianYiB = ByteUtil.floatToBytes(pianYi)
        val huanJingWenDuB = ByteUtil.floatToBytes(huanJingWenDu)
        val muBiaoZhiB = ByteUtil.floatToBytes(muBiaoZhi)
        val dataOrigin = ByteArray(16)
        System.arraycopy(pianYiB, 0, dataOrigin, 0, 4)
        System.arraycopy(huanJingWenDuB, 0, dataOrigin, 4, 4)
        System.arraycopy(muBiaoZhiB, 0, dataOrigin, 8, 4)
        val data = addHeadCmdLengthAndCs(dataOrigin, CMD_SET_TEMPERATURE_CALIBRATION_COEFFICIENT)
        dTag("set_temperature_calibration_coefficient", data)
        write(data)
      }, 200L, isTimeout = isTimeout, isReceived = isReceived) {
        callback?.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 写温度线性系数
   */
  fun setTemperatureLinearCoefficient(xiShu1: FloatArray, xiShu2: FloatArray, buChangZhi: FloatArray, callback: ActionCallback?) {
    if (!isConnected()) {
      callback?.onFail(TIP_DISCONNECT)
      return
    }
    val isReceived = AtomicBoolean(false)
    val isTimeout = AtomicBoolean(false)
    curFuture?.cancel(true)
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      doSleep(200)
      notify { data ->
        dTag("notify_set_temp_linear_coefficient", data)
        isReceived.set(true)
        if (isTimeout.get()) return@notify
        if (data != null && data.size == 6 && data[4] == 0x01.toByte() && data[0] == HEAD_FROM_SENSOR && data[1] == CMD_SET_TEMPERATURE_LINEAR_COEFFICIENT) {
          callback?.onSuccess()
        }
        else {
          callback?.onFail("设置温度线性系数失败")
        }
      }
      doTimeout(200L, {
        val xiShu1B = ByteUtil.floatsToBytes(xiShu1)
        val buChangZhiB = ByteUtil.floatsToBytes(buChangZhi)
        val xiShu2B = ByteUtil.floatsToBytes(xiShu2)
        val dataOrigin = ByteArray(92)
        System.arraycopy(xiShu1B, 0, dataOrigin, 0, 28)
        System.arraycopy(buChangZhiB, 0, dataOrigin, 28, 28)
        System.arraycopy(xiShu2B, 0, dataOrigin, 56, 28)
        val data = addHeadCmdLengthAndCs(dataOrigin, CMD_SET_TEMPERATURE_LINEAR_COEFFICIENT)
        dTag("set_temperature_linear_coefficient", data)
        write(data)
      }, 200L, isTimeout = isTimeout, isReceived = isReceived) {
        callback?.onFail(TIP_TIMEOUT)
      }
    }
  }

  /**
   * 升级
   * @param sn sn 号
   * @param file 升级文件
   * @param type 0-rh516, 1-测温模块
   */
  fun upgrade(sn: Int, byteArray: ByteArray?, type: Byte, callback: UpgradeCallback) {
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

    val totalBagCount = length.toBigDecimal().divide(236.toBigDecimal(), 0, BigDecimal.ROUND_UP).toInt()
    if (totalBagCount < 1) {
      mainHandler.post { callback.onUpgradeResult(false, "升级文件出错, 请重新下载升级文件") }
      return
    }
    val response = addHeadCmdLengthAndCs(null, CMD_UPGRADE_DATA_RESULT)
    val bagCountB: ByteArray = ByteUtil.intToBytes(totalBagCount)
    val dataOrigin = ByteArray(17)
    val transferInterval = 100L // 每包的传输间隔
    val maxRetryCount = 50 // 最大重传次数
    var mills: Long = 0L // 用来计时用的
    val retryCount: AtomicInteger = AtomicInteger(0) // 用来统计重传次数的
    System.arraycopy(snB, 0, dataOrigin, 0, 4)
    System.arraycopy(lengthB, 0, dataOrigin, 4, 4)
    System.arraycopy(crcB, 0, dataOrigin, 8, 4)
    System.arraycopy(bagCountB, 0, dataOrigin, 12, 4)
    dataOrigin[16] = type
    val dataPrepare = addHeadCmdLengthAndCs(dataOrigin, CMD_PREPARE_UPGRADE)

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
        val upgradeBag: ByteArray = Arrays.copyOfRange(bytesUpgrade, _bagIndex * 236, _bagIndex * 236 + 236)
        val upgradeDataOrigin = Arrays.copyOf(bagIndexB, bagIndexB.size + upgradeBag.size)
        System.arraycopy(upgradeBag, 0, upgradeDataOrigin, 4, upgradeBag.size)
        val upgradeData = addHeadCmdLengthAndCs(upgradeDataOrigin, CMD_UPGRADE_DATA)

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
        if (data != null && data.size == 125 && data[0] == HEAD_FROM_SENSOR && data[1] == CMD_UPGRADE_DATA_RESULT) {
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
    mainHandler.removeCallbacks(null)
    curFuture = singleExecutor.submit {
      notify { data ->
        dTag("notify_prepare_upgrade", data?.toString())
        isReceivedPrepare.set(true)
        if (isTimeoutPrepare.get()) return@notify
        if (data == null || data.size != 6 || data[4] != 0x01.toByte() || data[0] != HEAD_FROM_SENSOR || data[1] != CMD_PREPARE_UPGRADE) {
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

  private fun notify(onReceived: (data: ByteArray?) -> Unit) {
    BleManager.getInstance().clearCharacterCallback(curBleDevice)
    BleManager.getInstance().notify(curBleDevice, UUID_SERVICE, UUID_UP, object : BleNotifyCallback() {
      override fun onCharacteristicChanged(data: ByteArray?) {
        onReceived(data)
      }

      override fun onNotifyFailure(exception: BleException?) {
        dTag("notify_", exception)
      }

      override fun onNotifySuccess() {
      }
    })
  }

  private fun doRetry(
    delayBefore: Long,
    action: (() -> Unit),
    delayAfter: Long,
    retryCount: Int,
    retryTimeout: Long,
    isReceived: AtomicBoolean,
    isTimeout: AtomicBoolean,
    toDoWhenTimeout: (() -> Unit)
  ) {
    isReceived.set(false)
    isTimeout.set(false)
    thread {
      if (delayBefore > 0) {
        doSleep(delayBefore)
      }
      mainHandler.post(action)
      if (delayAfter > 0) {
        doSleep(delayAfter)
      }

      doSleep(retryTimeout)
      if (retryCount <= 0) {
        if (!isReceived.get()) {
          isTimeout.set(true)
          toDoWhenTimeout()
        }
      }
      else {
        for (a in 0 until retryCount) {
          if (!isReceived.get()) {
            action()
            doSleep(retryTimeout)
          }
        }
        if (!isReceived.get()) {
          isTimeout.set(true)
          toDoWhenTimeout()
        }
      }
    }
  }

  private var timerFuture: Future<*>? = null

  private fun doTimeout(
    delayBefore: Long,
    action: (() -> Unit)? = null,
    delayAfter: Long,
    retryCount: Int = 2,
    retryTimeout: Int = 500,
    isReceived: AtomicBoolean = AtomicBoolean(false),
    isTimeout: AtomicBoolean = AtomicBoolean(false),
    toDoWhenTimeout: (() -> Unit)? = null
  ) {
    timerFuture?.cancel(true)
    timerFuture = singleExecutor.submit {
      isReceived.set(false)
      isTimeout.set(false)
      if (delayBefore > 0) {
        doSleep(delayBefore)
      }

      action?.invoke()

      if (delayAfter > 0) {
        doSleep(delayAfter)
      }

      val sleepUnit = 10L // 每次休眠10 ms
      val sleepCount: Int = retryTimeout / sleepUnit.toInt()

      var index = 0
      var count = retryCount

      while (!isReceived.get() && !isTimeout.get()) {
        index++
        doSleep(sleepUnit)
        if (index == sleepCount) {
          if (count <= 0) {
            isTimeout.set(true)
            //                        stopNotify()
            //                        toastLong("响应超时")
            toDoWhenTimeout?.invoke()
            break
          }
          else {
            count--
            action?.invoke()
            index = 0
          }
        }
      }
    }
  }

  fun isLocationEnable(context: Context?): Boolean {
    context ?: return false
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    val gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    return networkProvider || gpsProvider
  }

  private fun updateBit(bytes: ByteArray?, bitIndex: Int) {
    if (bytes == null) {
      dTag("updateBit_", "bytes null")
      return
    }
    if (bitIndex < 0) {
      dTag("updateBit_", "index < 0")
      return
    }
    if (bitIndex > bytes.size * 8 - 1) {
      dTag("updateBit_", "out of bounds")
      return
    }
    val byteIndex = (bitIndex / 8).toInt()
    val bitIndexPerByte = (bitIndex % 8).toInt()
    bytes[byteIndex] = bytes[byteIndex] or flags[bitIndexPerByte]
  }

  private fun getBagIndexFromBit(bytes: ByteArray, totalBagCount: Int): List<Int> {
    if (totalBagCount <= 0 || totalBagCount > bytes.size * 8) {
      dTag("getBagIndexFromBit", "out of bounds")
    }
    val ret = mutableListOf<Int>()
    var index = 0
    for (i in 0 until bytes.size) {
      for (j in 0 until 8) {
        if (bytes[i].toInt().shr(j) and 0x01 == 0x00) {
          ret.add(index)
        }
        index++
        if (index >= totalBagCount) {
          return ret
        }
      }
    }
    return ret
  }

  private val flags = byteArrayOf(
      0x01.toByte(), 0x02.toByte(), 0x04.toByte(), 0x08.toByte(),
      0x10.toByte(), 0x20.toByte(), 0x40.toByte(), 0x80.toByte()
  )

  private fun stopNotify() {
    BleManager.getInstance().stopNotify(curBleDevice, UUID_SERVICE, UUID_UP)
  }

  private fun doSleep(mills: Long = 100) {
    try {
      Thread.sleep(mills)
    }
    catch (e: Exception) {
      d(e)
    }
  }

  fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    BleManager.getInstance().clearCharacterCallback(curBleDevice)
  }

  private fun d(log: String?) {
    if (isDebug && log != null) {
      Log.d(TAG, log)
    }
  }

  private fun dTag(tag: String?, log: Any?) {
    if (isDebug && log != null) {
      Log.d("${TAG}_$tag", log.toString())
    }
  }

  private fun d(exception: java.lang.Exception?) {
    if (isDebug && exception != null) {
      Log.d(TAG, exception.message)
    }
  }
}