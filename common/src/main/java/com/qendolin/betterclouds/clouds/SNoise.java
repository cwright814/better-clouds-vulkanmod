package com.qendolin.betterclouds.clouds;

public class SNoise {
    private static float mod289(float x) {
        return x - (float)Math.floor(x * (1.0f / 289.0f)) * 289.0f;
    }
    private static float permute(float x) {
        return mod289(((x * 34.0f) + 1.0f) * x);
    }
    
    public static float snoise(float x, float y, float offsetX, float offsetZ) {
        x += offsetX;
        y += offsetZ;
        
        float C_x = 0.211324865405187f;
        float C_y = 0.366025403784439f;
        float C_z = -0.577350269189626f;
        float C_w = 0.024390243902439f;
        
        float i_x = (float)Math.floor(x + (x + y) * C_y);
        float i_y = (float)Math.floor(y + (x + y) * C_y);
        
        float x0_x = x - i_x + (i_x + i_y) * C_x;
        float x0_y = y - i_y + (i_x + i_y) * C_x;
        
        float i1_x = (x0_x > x0_y) ? 1.0f : 0.0f;
        float i1_y = (x0_x > x0_y) ? 0.0f : 1.0f;
        
        float x12_x = x0_x + C_x - i1_x;
        float x12_y = x0_y + C_x - i1_y;
        float x12_z = x0_x + C_z;
        float x12_w = x0_y + C_z;
        
        i_x = mod289(i_x);
        i_y = mod289(i_y);
        
        float p0 = permute(permute(i_y + 0.0f) + i_x + 0.0f);
        float p1 = permute(permute(i_y + i1_y) + i_x + i1_x);
        float p2 = permute(permute(i_y + 1.0f) + i_x + 1.0f);
        
        float m0 = Math.max(0.5f - (x0_x*x0_x + x0_y*x0_y), 0.0f);
        float m1 = Math.max(0.5f - (x12_x*x12_x + x12_y*x12_y), 0.0f);
        float m2 = Math.max(0.5f - (x12_z*x12_z + x12_w*x12_w), 0.0f);
        
        m0 *= m0; m0 *= m0;
        m1 *= m1; m1 *= m1;
        m2 *= m2; m2 *= m2;
        
        float p0_w = p0 * C_w; float f_p0 = p0_w - (float)Math.floor(p0_w); float x_x0 = 2.0f * f_p0 - 1.0f;
        float p1_w = p1 * C_w; float f_p1 = p1_w - (float)Math.floor(p1_w); float x_x1 = 2.0f * f_p1 - 1.0f;
        float p2_w = p2 * C_w; float f_p2 = p2_w - (float)Math.floor(p2_w); float x_x2 = 2.0f * f_p2 - 1.0f;
        
        float h_x0 = Math.abs(x_x0) - 0.5f;
        float h_x1 = Math.abs(x_x1) - 0.5f;
        float h_x2 = Math.abs(x_x2) - 0.5f;
        
        float ox_x0 = (float)Math.floor(x_x0 + 0.5f);
        float ox_x1 = (float)Math.floor(x_x1 + 0.5f);
        float ox_x2 = (float)Math.floor(x_x2 + 0.5f);
        
        float a0_x0 = x_x0 - ox_x0;
        float a0_x1 = x_x1 - ox_x1;
        float a0_x2 = x_x2 - ox_x2;
        
        m0 *= 1.79284291400159f - 0.85373472095314f * (a0_x0*a0_x0 + h_x0*h_x0);
        m1 *= 1.79284291400159f - 0.85373472095314f * (a0_x1*a0_x1 + h_x1*h_x1);
        m2 *= 1.79284291400159f - 0.85373472095314f * (a0_x2*a0_x2 + h_x2*h_x2);
        
        float g_x0 = a0_x0 * x0_x + h_x0 * x0_y;
        float g_x1 = a0_x1 * x12_x + h_x1 * x12_y;
        float g_x2 = a0_x2 * x12_z + h_x2 * x12_w;
        
        return 130.0f * (m0 * g_x0 + m1 * g_x1 + m2 * g_x2);
    }
}
