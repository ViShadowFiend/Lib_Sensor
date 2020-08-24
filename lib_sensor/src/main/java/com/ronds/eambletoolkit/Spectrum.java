package com.ronds.eambletoolkit;

/**
 * 频谱实体, 用于 {@linkplain VibDataProcessUtil jni} 中作为加速度/速度/位移波形转频谱的媒介
 *
 * @author An.Wang 2019/8/15 16:17.
 */
public class Spectrum {

  /**
   * 幅值
   */
  private double[] amplitude;

  /**
   * 频率
   */
  private double[] frequency;

  public double[] getAmplitude() {
    return amplitude;
  }

  public void setAmplitude(double[] amplitude) {
    this.amplitude = amplitude;
  }

  public double[] getFrequency() {
    return frequency;
  }

  public void setFrequency(double[] frequency) {
    this.frequency = frequency;
  }
}
