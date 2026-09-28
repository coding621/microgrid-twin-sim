package com.microgrid.sim.simulation.history;

/**
 * 历史数据环形缓冲区 - 存储最近 len 个 tick 的四条时间序列。
 *
 * <p>存储的数据包括：负载 [kW]、光伏 [kW]、辐照度 [W/m²]、温度 [°C]、电池 SoC [kWh]。
 * 所有 getter 按时间顺序（从旧到新）返回数据。
 *
 * @author Coding
 */
public final class HistoryBuffer {

    /** 缓冲区容量 */
    private final int len;
    /** 负载历史数据 */
    private final double[] load;
    /** 光伏历史数据 */
    private final double[] pv;
    /** 辐照度历史数据 */
    private final double[] irr;
    /** 温度历史数据 */
    private final double[] temp;
    /** 电池 SoC 历史数据 */
    private final double[] soc;
    /** 下一个写入位置 */
    private int head = 0;
    /** 当前有效数据量 */
    private int count = 0;

    /**
     * 构造函数
     *
     * @param len 缓冲区容量（保存最近 len 个 tick）
     */
    public HistoryBuffer(int len) {
        this.len = len;
        load = new double[len];
        pv = new double[len];
        irr = new double[len];
        temp = new double[len];
        soc = new double[len];
    }

    /**
     * 推入最新样本数据
     *
     * @param l 负载 (kW)
     * @param p 光伏 (kW)
     * @param g 辐照度 (W/m²)
     * @param t 环境温度 (°C)
     * @param s 电池 SoC (kWh)
     */
    public void push(double l, double p, double g, double t, double s) {
        load[head] = l;
        pv[head] = p;
        irr[head] = g;
        temp[head] = t;
        soc[head] = s;
        head = (head + 1) % len;
        if (count < len) {
            count++;
        }
    }

    /** @return 当前有效数据量 */
    public int size() {
        return count;
    }

    /** @return 缓冲区是否已满 */
    public boolean isFull() {
        return count == len;
    }

    /** @return 按时间顺序排列的负载数据 */
    public double[] getLoad() {
        return snapshot(load);
    }

    /** @return 按时间顺序排列的光伏数据 */
    public double[] getPv() {
        return snapshot(pv);
    }

    /** @return 按时间顺序排列的温度数据 */
    public double[] getTemp() {
        return snapshot(temp);
    }

    /** @return 按时间顺序排列的 SoC 数据 */
    public double[] getSoc() {
        return snapshot(soc);
    }

    /** @return 按时间顺序排列的辐照度数据 */
    public double[] getIrr() {
        return snapshot(irr);
    }

    /** @return 最新的负载值 */
    public double getLoadLast() {
        return load[head];
    }

    /** @return 最新的光伏值 */
    public double getPvLast() {
        return pv[head];
    }

    /**
     * 按时间顺序生成快照
     *
     * @param a 原始环形数组
     * @return 从旧到新排列的数组
     */
    private double[] snapshot(double[] a) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = a[(head - count + i + len) % len];
        }
        return out;
    }
}