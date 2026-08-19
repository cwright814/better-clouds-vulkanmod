package com.qendolin.betterclouds.shadow;

import com.qendolin.betterclouds.BetterClouds;
import com.qendolin.betterclouds.config.Config;
import net.minecraft.util.Mth;

public class CloudShadowMap {

    /**
     * Calculates the cloud opacity (0-15) for a given absolute world coordinate.
     * This mimics the ChunkedGenerator noise sampling to mathematically determine
     * if a cloud is present directly above.
     */
    public static int getOpacity(int worldX, int worldZ) {
        // We will configure a config flag later, for now just compute it
        var renderer = BetterClouds.getCloudsRenderer();
        if (renderer == null) {
            return 0;
        }

        var generator = renderer.resources().generator();
        if (generator == null) {
            return 0;
        }
        
        var options = com.qendolin.betterclouds.config.ConfigManager.instance();

        // Convert absolute world position to generator-relative position
        double relX = worldX - generator.originX();
        double relZ = worldZ - generator.originZ();
        
        float spacing = options.spacing;
        
        // Find the closest grid point
        int globalGridX = Mth.floor(relX / spacing);
        int globalGridZ = Mth.floor(relZ / spacing);
        
        // Convert to the exact sample coordinates used in the noise function
        int sampleX = Mth.floor(globalGridX * spacing);
        int sampleZ = Mth.floor(globalGridZ * spacing);

        // Check sparsity (same as generator)
        if (options.sparsity > 0 && com.qendolin.betterclouds.clouds.Sampler.hashToFloat(generator.sampler.getSeed(), 'G', globalGridX, globalGridZ) < options.sparsity) {
            return 0;
        }

        // Use the dynamic cloudiness factor (which scales during weather/rain!)
        float cloudiness = generator.currentCloudiness();
        
        float value = generator.sampler.sample(sampleX, sampleZ, cloudiness, options.fuzziness, options.samplingScale);
        
        if (value <= 0) {
            return 0;
        }

        // Map the noise value (usually 0.0 to 1.0) to a light dampening opacity (1 to 4)
        // This is where feathering occurs!
        int opacity = Mth.clamp(Mth.floor(value * 4.0f) + 1, 1, 4);
        return opacity;
    }
}
