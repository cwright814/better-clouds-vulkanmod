package com.qendolin.betterclouds.clouds;

import com.qendolin.betterclouds.config.Config;
import com.qendolin.betterclouds.config.ConfigManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.Arrays;
import java.util.List;

public class Sampler {
    /**
     * -1, 0, 1, 2     - Default with frequent clumps of clouds<br>
     * -3, -1, 0, 1, 2 - Pretty big, sparse fields of clouds and fields of clear sky, maybe too big for 32 Chunks of render distance<br>
     * -2, 0, 1, 2     - Medium heaps of clouds with fields of clear sky, no problem for 32 Chunks<br>
     * 0, 1, 2         - Many spots of small clouds with some medium holes of clear sky<br>
     *
     * <p>Run SamplerTest.java to visualize the noise function</p>
     */
    @SuppressWarnings("unchecked")
    public static final List<Integer>[] OCTAVE_OPTIONS = new List[] {
            List.of(-1, 0, 1, 2),
            List.of(-3, -1, 0, 1, 2),
            List.of(-2, 0, 1, 2),
            List.of(0, 1, 2)
    };

    public static final float REGION_SIZE = 2048;
    public static final float BASE_FUZZINESS = 0.9f;

    private final long seed;

    private final SimplexNoise regionNoise;
    private final SimplexNoise coverageNoise;
    private final List<PerlinSimplexNoise> detailNoises;

    public final float noiseOffsetX;
    public final float noiseOffsetZ;

    public Sampler(long seed) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed));
        WorldgenRandom regionRandom = new WorldgenRandom(new LegacyRandomSource(random.nextInt()));
        this.seed = seed;
        this.noiseOffsetX = hashToFloat(seed, 'O', 'X') * 10000.0f;
        this.noiseOffsetZ = hashToFloat(seed, 'O', 'Z') * 10000.0f;

        Config options = ConfigManager.instance();

        regionNoise = new SimplexNoise(regionRandom);
        coverageNoise = new SimplexNoise(random);
        detailNoises = options.noisePreset().octaves
                .stream().map(octave -> new PerlinSimplexNoise(random, octave)).toList();
    }

    /**
     * For testing only
     */
    public Sampler() {
        this.seed = 1337;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed));
        WorldgenRandom regionRandom = new WorldgenRandom(new LegacyRandomSource(random.nextInt()));
        this.noiseOffsetX = hashToFloat(seed, 'O', 'X') * 10000.0f;
        this.noiseOffsetZ = hashToFloat(seed, 'O', 'Z') * 10000.0f;

        Config options = new Config();
        options.noisePreset().octaves = Arrays.asList(
                Arrays.asList(-1, 0, 1, 2),
                Arrays.asList(-3, -1, 0, 1, 2),
                Arrays.asList(-2, 0, 1, 2),
                Arrays.asList(0, 1, 2)
        );

        regionNoise = new SimplexNoise(regionRandom);
        coverageNoise = new SimplexNoise(random);
        detailNoises = options.noisePreset().octaves
                .stream().map(octave -> new PerlinSimplexNoise(random, octave)).toList();
    }

    // Jenkins hash function (seed does not have to be prime)
    public static long hash(long seed, int... values) {
        long hash = seed;
        for (int value : values) {
            hash += value;
            hash += hash << 10;
            hash ^= hash >>> 6;
        }
        hash += hash << 3;
        hash ^= hash >> 11;
        hash += hash << 15;
        return hash;
    }

    // https://stackoverflow.com/a/17479300/7448536
    // Distribution is very uniform from my testing
    public static float hashToFloat(long seed, int... values) {
        int hash = Long.hashCode(hash(seed, values));

        int ieeeMantissa = 0x007FFFFF;
        int ieeeOne = 0x3F800000;

        hash &= ieeeMantissa;
        hash |= ieeeOne;
        float f = Float.intBitsToFloat(hash);
        return f - 1;
    }

    public long getSeed() {
        return seed;
    }

    public float randomOffsetX(int x, int z, int pass) {
        return hashToFloat(seed, 'S', x, z, 'X', pass);
    }

    public float randomOffsetZ(int x, int z, int pass) {
        return hashToFloat(seed, 'S', x, z, 'Z', pass);
    }

    public float sample(int x, int z, float cloudiness, float fuzziness, float scale) {
        float value;
        if (ConfigManager.instance().syncedShadows) {
            // Base noise coordinate
            float fScale = scale * 128.0f;
            float nx = (float) x / fScale + 0.5f;
            float nz = (float) z / fScale + 0.5f;
            
            // 4 octaves to match the GPU shader exactly!
            value = SNoise.snoise(nx * 0.25f, nz * 0.25f, noiseOffsetX, noiseOffsetZ) * 0.4f
                  + SNoise.snoise(nx * 0.5f,  nz * 0.5f,  noiseOffsetX, noiseOffsetZ) * 0.3f
                  + SNoise.snoise(nx,         nz,         noiseOffsetX, noiseOffsetZ) * 0.2f
                  + SNoise.snoise(nx * 2.0f,  nz * 2.0f,  noiseOffsetX, noiseOffsetZ) * 0.1f;
        } else {
            // Shift coordinates slightly to avoid artifacts at the exact noise origin (0,0)
            double nx = (double) x / scale / 128.0 + 0.5;
            double nz = (double) z / scale / 128.0 + 0.5;

            // TODO: A vanilla like cloud distribution is not possible with this function
            if (detailNoises.size() > 1) {
                double regionNoiseValue = (regionNoise.getValue((double) x / REGION_SIZE + 0.5, (double) z / REGION_SIZE + 0.5) * 0.5 + 0.5) * detailNoises.size();
                int noiseInd = (int) regionNoiseValue;
                // Prevent OutOfBounds just in case regionNoiseValue hits exactly 1.0
                if (noiseInd >= detailNoises.size()) noiseInd = detailNoises.size() - 1;
                if (noiseInd < 0) noiseInd = 0;
                PerlinSimplexNoise noise1 = detailNoises.get(noiseInd), noise2 = detailNoises.get((noiseInd + 1) % detailNoises.size());

                value = (float) Mth.lerp(
                        Math.pow(Mth.clamp(regionNoiseValue - noiseInd, 0, 1), 5),
                        noise1.getValue(nx, nz, false),
                        noise2.getValue(nx, nz, false)
                );
            } else {
                value = (float) detailNoises.getFirst().getValue(nx, nz, false);
            }
        }

        value = value / 2.0f + 0.5f;
        value = (value - (1.0f - cloudiness)) / cloudiness;
        if (ConfigManager.instance().syncedShadows) {
            value *= (float)smoothstep(-0.6 * cloudiness - 0.3, -0.6 * cloudiness, SNoise.snoise((float)(x / 1024.0 + 0.5), (float)(z / 1024.0 + 0.5), noiseOffsetX, noiseOffsetZ));
        } else {
            value *= (float)smoothstep(-0.6 * cloudiness - 0.3, -0.6 * cloudiness, coverageNoise.getValue((double) x / 1024.0 + 0.5, (double) z / 1024.0 + 0.5));
        }

        float random = hashToFloat(seed, 'B', x, z);
        if (random > value + (BASE_FUZZINESS - fuzziness)) value = 0;
        return (float) value;
    }

    // https://stackoverflow.com/a/50815919/7448536
    double smoothstep(double edge0, double edge1, double x) {
        // Scale, bias and saturate x to 0..1 range
        x = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        // Evaluate polynomial
        return x * x * (3 - 2 * x);
    }
}
