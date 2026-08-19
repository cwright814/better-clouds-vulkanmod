package com.qendolin.betterclouds.shadow;

import com.qendolin.betterclouds.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class ShadowUpdateThread extends Thread {
    
    private boolean running = true;
    private final Map<Long, Integer> previousOpacities = new HashMap<>();

    public ShadowUpdateThread() {
        super("BetterClouds-ShadowUpdater");
        this.setDaemon(true);
        this.setPriority(Thread.MIN_PRIORITY);
    }

    public void stopThread() {
        this.running = false;
    }

    private int scanOffsetX = 0;
    private int scanOffsetZ = 0;
    private long worldJoinTime = 0;

    @Override
    public void run() {
        while (running) {
            try {
                int updateRate = 100;
                if (com.qendolin.betterclouds.config.ConfigManager.isInitialized()) {
                    updateRate = com.qendolin.betterclouds.config.ConfigManager.instance().shadowUpdateRate;
                }
                Thread.sleep(updateRate);

                var client = Minecraft.getInstance();
                if (client.level == null || client.player == null) {
                    previousOpacities.clear();
                    worldJoinTime = 0; // Reset on disconnect
                    continue;
                }
                
                if (worldJoinTime == 0) {
                    worldJoinTime = System.currentTimeMillis();
                }

                int worldLoadDelay = com.qendolin.betterclouds.config.ConfigManager.instance().shadowWorldLoadDelay;
                if (System.currentTimeMillis() - worldJoinTime < worldLoadDelay) {
                    continue;
                }
                
                if (!com.qendolin.betterclouds.config.ConfigManager.instance().shadowsEnabled) continue;
                if (!client.level.dimensionType().hasSkyLight()) continue;

                var lightEngine = client.level.getLightEngine();
                if (lightEngine == null) continue;

                var renderer = com.qendolin.betterclouds.BetterClouds.getCloudsRenderer();
                if (renderer == null || renderer.resources().generator() == null) continue;

                int renderDist = client.options.renderDistance().get();
                int playerChunkX = client.player.getBlockX() >> 4;
                int playerChunkZ = client.player.getBlockZ() >> 4;
                int cloudY = (int) com.qendolin.betterclouds.config.ConfigManager.instance().yOffset;
                long startMs = System.currentTimeMillis();
                int maxTime = com.qendolin.betterclouds.config.ConfigManager.instance().shadowMaxTimePerIteration;

                // We scan a small batch of chunks per iteration to keep CPU usage minimal
                int chunksPerIteration = Math.max(1, (renderDist * renderDist) / 20); // Scale with render dist

                for (int i = 0; i < chunksPerIteration; i++) {
                    if (System.currentTimeMillis() - startMs > maxTime) {
                        break; // Bail out if this iteration is taking too long
                    }

                    // Move the scan offsets in a simple typewriter pattern across the render distance square
                    scanOffsetX++;
                    if (scanOffsetX > renderDist) {
                        scanOffsetX = -renderDist;
                        scanOffsetZ++;
                        if (scanOffsetZ > renderDist) {
                            scanOffsetZ = -renderDist;
                        }
                    }

                    int targetChunkX = playerChunkX + scanOffsetX;
                    int targetChunkZ = playerChunkZ + scanOffsetZ;

                    // Scan the 256 blocks in this chunk
                    for (int bx = 0; bx < 16; bx++) {
                        for (int bz = 0; bz < 16; bz++) {
                            int worldX = (targetChunkX << 4) + bx;
                            int worldZ = (targetChunkZ << 4) + bz;

                            long posHash = BlockPos.asLong(worldX, 0, worldZ);
                            int newOpacity = CloudShadowMap.getOpacity(worldX, worldZ);
                            int oldOpacity = previousOpacities.getOrDefault(posHash, -1);

                            if (newOpacity != oldOpacity) {
                                previousOpacities.put(posHash, newOpacity);
                                if (oldOpacity != -1) {
                                    lightEngine.checkBlock(new BlockPos(worldX, cloudY, worldZ));
                                }
                            }
                        }
                    }
                }

                // Periodically clean up the cache so it doesn't grow infinitely when walking
                if (Math.random() < 0.05) {
                    previousOpacities.entrySet().removeIf(entry -> {
                        int cx = BlockPos.getX(entry.getKey()) >> 4;
                        int cz = BlockPos.getZ(entry.getKey()) >> 4;
                        return Math.abs(cx - playerChunkX) > renderDist + 2 || Math.abs(cz - playerChunkZ) > renderDist + 2;
                    });
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // Ignore general exceptions (like level unloading during access)
            }
        }
    }
}
