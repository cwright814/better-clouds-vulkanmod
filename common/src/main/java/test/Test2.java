package test;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

public class Test2 {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.mojang.blaze3d.vertex.DefaultVertexFormat");
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            System.out.println("DefaultVertexFormat." + f.getName() + " : " + f.getType().getSimpleName());
        }
        Class<?> elemClazz = Class.forName("com.mojang.blaze3d.vertex.VertexFormatElement");
        for (java.lang.reflect.Field f : elemClazz.getDeclaredFields()) {
            System.out.println("VertexFormatElement." + f.getName() + " : " + f.getType().getSimpleName());
        }
    }
}
