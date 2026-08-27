package com.qendolin.betterclouds.clouds;

public class SNoise {
    private static double mod289(double x) {
        return x - Math.floor(x * (1.0 / 289.0)) * 289.0;
    }
    private static double permute(double x) {
        return mod289(((x * 34.0) + 1.0) * x);
    }
    
    public static float snoise(double x, double y, double offsetX, double offsetZ) {
        x += offsetX;
        y += offsetZ;
        
        double C_x = 0.211324865405187;
        double C_y = 0.366025403784439;
        double C_z = -0.577350269189626;
        double C_w = 0.024390243902439;
        
        double i_x = Math.floor(x + (x + y) * C_y);
        double i_y = Math.floor(y + (x + y) * C_y);
        
        double x0_x = x - i_x + (i_x + i_y) * C_x;
        double x0_y = y - i_y + (i_x + i_y) * C_x;
        
        double i1_x = (x0_x > x0_y) ? 1.0 : 0.0;
        double i1_y = (x0_x > x0_y) ? 0.0 : 1.0;
        
        double x12_x = x0_x + C_x - i1_x;
        double x12_y = x0_y + C_x - i1_y;
        double x12_z = x0_x + C_z;
        double x12_w = x0_y + C_z;
        
        i_x = mod289(i_x);
        i_y = mod289(i_y);
        
        double p0 = permute(permute(i_y + 0.0) + i_x + 0.0);
        double p1 = permute(permute(i_y + i1_y) + i_x + i1_x);
        double p2 = permute(permute(i_y + 1.0) + i_x + 1.0);
        
        double m0 = Math.max(0.5 - (x0_x*x0_x + x0_y*x0_y), 0.0);
        double m1 = Math.max(0.5 - (x12_x*x12_x + x12_y*x12_y), 0.0);
        double m2 = Math.max(0.5 - (x12_z*x12_z + x12_w*x12_w), 0.0);
        
        m0 *= m0; m0 *= m0;
        m1 *= m1; m1 *= m1;
        m2 *= m2; m2 *= m2;
        
        double p0_w = p0 * C_w; double f_p0 = p0_w - Math.floor(p0_w); double x_x0 = 2.0 * f_p0 - 1.0;
        double p1_w = p1 * C_w; double f_p1 = p1_w - Math.floor(p1_w); double x_x1 = 2.0 * f_p1 - 1.0;
        double p2_w = p2 * C_w; double f_p2 = p2_w - Math.floor(p2_w); double x_x2 = 2.0 * f_p2 - 1.0;
        
        double h_x0 = Math.abs(x_x0) - 0.5;
        double h_x1 = Math.abs(x_x1) - 0.5;
        double h_x2 = Math.abs(x_x2) - 0.5;
        
        double ox_x0 = Math.floor(x_x0 + 0.5);
        double ox_x1 = Math.floor(x_x1 + 0.5);
        double ox_x2 = Math.floor(x_x2 + 0.5);
        
        double a0_x0 = x_x0 - ox_x0;
        double a0_x1 = x_x1 - ox_x1;
        double a0_x2 = x_x2 - ox_x2;
        
        m0 *= 1.79284291400159 - 0.85373472095314 * (a0_x0*a0_x0 + h_x0*h_x0);
        m1 *= 1.79284291400159 - 0.85373472095314 * (a0_x1*a0_x1 + h_x1*h_x1);
        m2 *= 1.79284291400159 - 0.85373472095314 * (a0_x2*a0_x2 + h_x2*h_x2);
        
        double g_x0 = a0_x0 * x0_x + h_x0 * x0_y;
        double g_x1 = a0_x1 * x12_x + h_x1 * x12_y;
        double g_x2 = a0_x2 * x12_z + h_x2 * x12_w;
        
        return (float) (130.0 * (m0 * g_x0 + m1 * g_x1 + m2 * g_x2));
    }
}
