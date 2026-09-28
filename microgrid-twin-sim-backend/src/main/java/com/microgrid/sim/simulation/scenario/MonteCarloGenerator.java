package com.microgrid.sim.simulation.scenario;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.rng.simple.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 蒙特卡洛场景生成器 - 通过高斯 Copula 耦合负载与光伏，生成 N 个随机场景。
 *
 * <p>使用多元正态分布采样，协方差矩阵采用指数相关结构 ρ^{|Δt|}，ρ=0.6。
 * 默认生成 50 个等概率 (1/N) 场景。
 *
 * @author Coding
 */
public final class MonteCarloGenerator implements ScenarioGenerator {

    /** 预测视野长度 */
    private final int H;
    /** 蒙特卡洛抽样数量 */
    private final int N;

    /**
     * 构造函数
     *
     * @param horizon 预测视野（步数）
     * @param draws   抽样数量
     */
    public MonteCarloGenerator(int horizon, int draws) {
        H = horizon;
        N = draws;
    }

    /**
     * 通过高斯 Copula 生成 N 个场景
     *
     * @param loadQ 负载分位数 [q05,q50,q95][H]
     * @param pvQ   光伏分位数 [q05,q50,q95][H]
     * @return N 个场景的列表，每个概率 1/N
     */
    @Override
    public List<Scenario> generate(double[][] loadQ, double[][] pvQ) {
        // 构造均值向量 μ 和标准差向量 σ
        double[] mu = new double[2 * H];
        double[] sig = new double[2 * H];

        for (int k = 0; k < H; k++) {
            mu[k] = loadQ[1][k];                            // 均值 = 中位数
            sig[k] = 0.5 * (loadQ[2][k] - loadQ[0][k]);     // σ ≈ 半距
            mu[k + H] = pvQ[1][k];
            sig[k + H] = 0.5 * (pvQ[2][k] - pvQ[0][k]);
        }

        // 构造指数相关协方差矩阵 ρ^{|Δt|}, ρ=0.6
        RealMatrix cov = MatrixUtils.createRealIdentityMatrix(2 * H);
        final double rho = 0.6;
        for (int i = 0; i < 2 * H; i++) {
            for (int j = 0; j < 2 * H; j++) {
                cov.setEntry(i, j, Math.pow(rho, Math.abs(i - j)) * sig[i] * sig[j]);
            }
        }

        // 多元正态分布抽样
        MultivariateNormalDistribution mvn =
                new MultivariateNormalDistribution(
                        (RandomGenerator) RandomSource.XO_RO_SHI_RO_64_S.create(),
                        mu, cov.getData());

        List<Scenario> list = new ArrayList<>(N);
        for (int d = 0; d < N; d++) {
            double[] draw = mvn.sample();
            double[] L = Arrays.copyOfRange(draw, 0, H);
            double[] P = Arrays.copyOfRange(draw, H, 2 * H);
            list.add(new Scenario(L, P, 1.0 / N));
        }
        return list;
    }
}