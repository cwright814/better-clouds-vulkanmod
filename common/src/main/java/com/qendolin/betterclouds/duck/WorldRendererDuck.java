package com.qendolin.betterclouds.duck;

import com.qendolin.betterclouds.clouds.Renderer;

public interface WorldRendererDuck {
    Renderer betterclouds$getRenderer();
    com.qendolin.betterclouds.clouds.vulkan.VulkanRenderer betterclouds$getVulkanRenderer();

}
