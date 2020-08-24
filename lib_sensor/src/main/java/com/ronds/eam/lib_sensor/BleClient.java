package com.ronds.eam.lib_sensor;

import android.app.Application;
import android.content.Context;
import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;
import com.ronds.eambletoolkit.VibDataProcessUtil;
import java.util.List;
import java.util.UUID;
import com.ronds.eambletoolkit.Spectrum;

/**
 * Android ble 版 516 管理类.
 *
 * <p>需注意的是: ble 存在连接间隔 (CI) 这个参数, 且 Android 上各种手机支持的最低间隔各不相同.</p>
 * <p>受此影响, 你对 ble 设备下的每条指令之间都需要有所延迟. 本管理类已对此做出处理, 但关于 UI 方面, 需要额外做出限制.</p>
 * <p>比如: 开启扫描/停止扫描, 中间需要加入限制, 不可短时间内反复切换. 否则, 很容易出现失败的情况</p>
 *
 * @author An.Wang 2019/8/20 9:28.
 */
public class BleClient {
  private BleClient() {
  }

  private static class BleClientHolder {
    private static final BleClient INSTANCE = new BleClient();
  }

  /**
   * 获取 ble client 的单例
   *
   * @return {@linkplain BleClient BleClient的单例}
   */
  public static BleClient getInstance() {
    return BleClientHolder.INSTANCE;
  }

  /**
   * 初始化时可供配置的选项 builder 类
   */
  public static class InitOptions {
    private UUID[] filterServiceUUIDs;
    private String[] filterDeviceNames;
    private boolean isFilterByDeviceNames;
    private String filterMac;
    private long scanTimeOut;

    private InitOptions() {
    }

    /**
     * 配置扫描时的过滤项(按 service uuids 过滤扫描)
     *
     * @param uuids 蓝牙设备的 Service UUID 数组
     * @return {@link InitOptions}
     */
    public InitOptions filterServiceUUIDs(UUID[] uuids) {
      this.filterServiceUUIDs = uuids;
      return this;
    }

    /**
     * 配置扫描时的过滤项(按蓝牙设备 name 过滤扫描)
     *
     * @param isFilterByDeviceNames true - 按给定设备名过滤, false - 禁用按设备名扫描
     * @param names 蓝牙设备名数组
     * @return {@link InitOptions}
     */
    public InitOptions filterDeviceNames(boolean isFilterByDeviceNames, String... names) {
      this.isFilterByDeviceNames = isFilterByDeviceNames;
      this.filterDeviceNames = names;
      return this;
    }

    /**
     * 配置扫描时的过滤项(按蓝牙设备的 mac 过滤扫描)
     *
     * @param mac ble 设备的 mac
     * @return {@link InitOptions}
     */
    public InitOptions filterMac(String mac) {
      this.filterMac = mac;
      return this;
    }

    /**
     * 配置扫描时的过滤项(扫描的 timeOut)
     *
     * @param timeOut 大于 0, 单位 ms
     * @return {@link InitOptions}
     */
    public InitOptions scanTimeOut(long timeOut) {
      this.scanTimeOut = timeOut;
      return this;
    }

    /**
     * 根据配置的选项进行初始化
     *
     * @param app {@linkplain Application 应用全局上下文 }
     */
    public void init(Application app) {
      BleMgr.INSTANCE.getRuleBuilder()
          .setScanTimeOut(scanTimeOut)
          .setServiceUuids(filterServiceUUIDs)
          .setDeviceName(isFilterByDeviceNames, filterDeviceNames)
          .setDeviceMac(filterMac);
      BleMgr.INSTANCE.init(app);
    }
  }

  /**
   * 配置初始化可选项
   *
   * <p>一般情况下直接使用 {@link #init(Application)} 进行初始化</p>
   *
   * @return {@linkplain InitOptions 初始化可选项配置类}
   */
  public InitOptions initOptions() {
    return new InitOptions();
  }

  /**
   * 按默认参数初始化 {@link BleClient}
   *
   * @param application {@linkplain Application 应用全局上下文 }
   */
  public void init(final Application application) {
    BleMgr.INSTANCE.init(application);
  }

  /**
   * 判断蓝牙是否开启
   *
   * @return true - 启用, false - 关闭
   */
  public boolean isBluetoothEnabled() {
    return BleManager.getInstance().isBlueEnable();
  }

  /**
   * 判断 Android 手机是否支持 ble
   *
   * @return true - 支持, false - 不支持
   */
  public boolean isSupportBle() {
    return BleManager.getInstance().isSupportBle();
  }

  /**
   * 判断位置权限是否开启
   *
   * <p>Android 6.0 及以后, 扫描 ble 设备可能需要开启定位, 否则可能扫描不到 ble 设备</p>
   *
   * @param context android 上下文
   * @return true - 定位开启, false - 定位关闭
   */
  public boolean isLocationEnable(Context context) {
    return BleMgr.INSTANCE.isLocationEnable(context);
  }

  /**
   * 扫描.
   *
   * <p>需注意开启扫描与停止扫描都需要时间, 需对短时间内反复开启/停止做出限制</p>
   *
   * @param scanCallback 扫描回调, 扫描开始/结束和扫描到的设备列表会用此回调
   */
  public void scan(BleInterfaces.ScanCallback scanCallback) {
    BleMgr.INSTANCE.scan(scanCallback);
  }

  /**
   * 停止扫描.
   *
   * <p>需注意开启扫描与停止扫描都需要时间, 需对短时间内反复开启/停止做出限制</p>
   */
  public void stopScan() {
    BleMgr.INSTANCE.stopScan();
  }

  /**
   * 给定蓝牙设备是否连接
   *
   * @param bleDevice 蓝牙设备. 可为 null, 为 null 时返回 false
   * @return true - 已连接, false - 为空或者未连接
   */
  public boolean isConnected(BleDevice bleDevice) {
    return BleMgr.INSTANCE.isConnected(bleDevice);
  }

  /**
   * 给定 mac 的蓝牙设备 是否连接
   *
   * @param mac 蓝牙设备的 mac 地址. 可为 null, 为 null 时返回 false
   * @return true - 已连接, false - 为空或者未连接
   */
  public boolean isConnected(String mac) {
    return BleMgr.INSTANCE.isConnected(mac);
  }

  /**
   * 当前设备是否连接.
   *
   * <p>
   *   当前设备指最后一次 {@linkplain #connect(BleDevice, BleInterfaces.ConnectStatusCallback) connect} 中
   *   {@linkplain BleInterfaces.ConnectStatusCallback#onConnectSuccess(BleDevice) onConnectSuccess} 时的设备
   * </p>
   *
   * @return true - 已连接, false - 为空或者未连接
   */
  public boolean isConnect() {
    return BleMgr.INSTANCE.isConnected();
  }

  /**
   * 断开所有连接
   *
   * @param disconnectCallback 断开连接回调
   */
  public void disconnectAllDevices(BleInterfaces.DisconnectCallback disconnectCallback) {
    BleMgr.INSTANCE.disConnectAllDevices(disconnectCallback);
  }

  /**
   * 断开连接
   *
   * @param bleDevice 516 设备
   * @param disconnectCallback 断开连接回调
   */
  public void disconnect(BleDevice bleDevice, BleInterfaces.DisconnectCallback disconnectCallback) {
    BleMgr.INSTANCE.disconnect(bleDevice, disconnectCallback);
  }

  /**
   * 获取所有已连接的设备
   *
   * @return 所有已连接的设备
   */
  public List<BleDevice> getAllConnectDevices() {
    return BleMgr.INSTANCE.getAllConnectDevices();
  }

  /**
   * 连接 516
   *
   * @param bleDevice 516 设备
   * @param connectStatusCallback 连接状态回调
   */
  public void connect(BleDevice bleDevice, BleInterfaces.ConnectStatusCallback connectStatusCallback) {
    BleMgr.INSTANCE.connect(bleDevice, connectStatusCallback);
  }

  /**
   * 测温
   *
   * @param emi 测温发射率, 4 字节浮点型, 给值范围 [0.01, 1.0]
   * @param sampleTempCallback 回调,
   */
  public void sampleTemp(float emi, BleInterfaces.SampleTempCallback sampleTempCallback) {
    BleMgr.INSTANCE.startSampleTemp(emi, sampleTempCallback);
  }

  /**
   * 移除测温回调
   */
  public void removeSampleTempCallback() {
    BleMgr.INSTANCE.removeSampleTempCallback();
  }

  /**
   * 停止测温
   *
   * <p>
   *   停止测温后, 并不会马上停止, 因向 516 下达指令及 516 做出处理都需要一定的时间, 应先调用 {@link #removeSampleTempCallback()}
   *   来清除回调
   * </p>
   *
   * @param callback 回调停止结果
   */
  public void stopSampleTemp(BleInterfaces.ActionCallback callback) {
    BleMgr.INSTANCE.stopSampleTemp(callback);
  }

  /**
   * 测振
   *
   * @param len 采集长度, 4 字节整型, 单位 K, 不要超过 256K
   * @param freq 分析频率, 4 字节整型, 单位 Hz, 不要超过 40000Hz
   * @param sampleVibCallback 测振结果回调
   */
  public void sampleVib(int len, int freq, BleInterfaces.SampleVibCallback sampleVibCallback) {
    BleMgr.INSTANCE.sampleVib(len, freq, sampleVibCallback);
  }

  /**
   * 配置传感器系统参数
   *
   * <p>
   *   注意: sn、加速度系数、测温发射率、硬件版本号一般情况下无法配置. 仅当 sn 有效位数为 8, 首位为 9 时 (如91234567), 才可以修改成功 sn、
   *   加速度系数、测温发射率、硬件版本号
   * </p>
   *
   * @param sn 4 字节整型. 最大有效位数 8.
   * @param coe 4 字节浮点型. 加速度系数.
   * @param emi 4 字节浮点型, 测温发射率
   * @param dur 4 字节整型, 待机时长.
   * @param ver 4 字节浮点型, 硬件版本号
   * @param callback 回调设置系统参数的结果
   */
  public void setSystemParams(int sn, float coe, float emi, int dur, float ver, BleInterfaces.ActionCallback callback) {
    BleMgr.INSTANCE.setSystemParams(sn, coe, emi, dur, ver, callback);
  }

  /**
   * 读取传感器系统参数
   *
   * @param callback 回调系统参数
   */
  public void getSystemParams(BleInterfaces.GetSystemParamsCallback callback) {
    BleMgr.INSTANCE.getSystemParams(callback);
  }

  /**
   * 测振校准
   *
   * @param len 采集长度
   * @param freq 分析频率
   * @param callback 校准成功回调校准系数 (4 字节浮点型), 失败回调失败信息
   */
  public void vibCalibrate(long len, long freq, BleInterfaces.CalibrationCallback callback) {
    BleMgr.INSTANCE.calibrate(len, freq, callback);
  }

  /**
   * 获取温度校准系数
   *
   * @param callback 回调获取结果
   */
  public void getTemperatureCalibrationCoefficient(BleInterfaces.GetTemperatureCalibrationCoefficientCallback callback) {
    BleMgr.INSTANCE.getTemperatureCalibrationCoefficient(callback);
  }

  /**
   * 设置温度校准系数
   *
   * @param off 偏移, float
   * @param env_temp 环境温度, float
   * @param tar 目标值, float
   * @param callback 回调设置结果, 成功或失败信息
   */
  public void setTemperatureCalibrationCoefficient(float off, float env_temp, float tar, BleInterfaces.ActionCallback callback) {
    BleMgr.INSTANCE.setTemperatureCalibrationCoefficient(off, env_temp, tar, callback);
  }

  /**
   * 获取温度线性系数
   *
   * @param callback 线性系数结果回调
   */
  public void getTemperatureLinearCoefficient(BleInterfaces.GetTemperatureLinearCoefficientCallback callback) {
    BleMgr.INSTANCE.getTemperatureLinearCoefficient(callback);
  }

  /**
   * 设置温度线性系数
   *
   * @param coe1 系数 1, 7 * float
   * @param coe2 系数 2, 7 * float
   * @param off 补偿值, 7 * float
   * @param callback 回调设置结果, 成功或失败信息
   */
  public void setTemperatureLinearCoefficient(float[] coe1, float[] coe2, float[] off, BleInterfaces.ActionCallback callback) {
    BleMgr.INSTANCE.setTemperatureLinearCoefficient(coe1, coe2, off, callback);
  }

  /**
   * 升级
   *
   * @param sn 设备 sn 号
   * @param data 升级数据, 字节数组
   * @param type 0 - 升级 RH516, 1 - 升级测温模块
   * @param callback 回调升级结果
   */
  public void upgrade(int sn, byte[] data, byte type, BleInterfaces.UpgradeCallback callback) {
    BleMgr.INSTANCE.upgrade(sn, data, type, callback);
  }

  /**
   * 加速度转位移
   *
   * @param acc 源加速度数据
   * @param f 采集频率, Hz
   * @param fMin 下限频率, Hz
   * @param fMax 上限频率, Hz
   * @return 位移 double[]
   */
  public double[] accToDist(double[] acc, double f, double fMin, double fMax) {
    return VibDataProcessUtil.accToDist(acc, f, fMin, fMax);
  }

  /**
   * 加速度转速度
   *
   * @param acc 源加速度数据
   * @param f 采集频率, Hz
   * @param fMin 下限频率, Hz
   * @param fMax 上限频率, Hz
   * @return 速度 double[]
   */
  public double[] accToVel(double[] acc, double f, double fMin, double fMax) {
    return VibDataProcessUtil.accToVel(acc, f, fMin, fMax);
  }

  /**
   * 加速度/速度/位移波形转频谱
   *
   * @param data 加速度/速度/位移源数据
   * @param f 采集频率, Hz
   * @return {@link Spectrum}
   */
  public Spectrum fft(double[] data, double f) {
    Spectrum spectrum = new Spectrum();
    VibDataProcessUtil.fft(spectrum, data, f);
    return spectrum;
  }
}
