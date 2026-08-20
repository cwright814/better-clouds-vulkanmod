package com.qendolin.betterclouds.mixin;

import com.qendolin.betterclouds.BetterCloudsStatic;
import com.qendolin.betterclouds.platform.ModLoader;

import java.util.ArrayList;
import java.util.List;

public class RuntimeMixinPlugin extends MixinPlugin {

    @Override
    public List<String> getMixins() {
        if (!ModLoader.isClientEnvironment()) return null;

        List<String> classes = new ArrayList<>();

        classes.add("FogRendererMixin");
        classes.add("SimpleOptionAccessor");

        classes.add("yacl.OptionListGroupSeparatorEntryMixin");
        classes.add("yacl.OptionListOptionEntryMixin");

        if (BetterCloudsStatic.IS_DEV) {
            classes.add("GlDebugMixin");
        }
        
        if (ModLoader.isModLoaded("vulkanmod")) {
            classes.add("ShaderLoadUtilMixin");
        }
        
        if (ModLoader.isModLoaded("beryl")) {
            classes.add("BerylPipelineConfigsMixin");
        }

        //noinspection ConstantValue
        if (classes.isEmpty())
            return null;

        return classes;
    }
}
