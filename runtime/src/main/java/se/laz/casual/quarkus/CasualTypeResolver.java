/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import java.lang.reflect.Array;

public class CasualTypeResolver
{
    private CasualTypeResolver()
    {}
    public static Class<?> loadClass(ClassLoader cl, String name) throws ClassNotFoundException
    {
        return switch (name) {
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> {
                // Check if it's already an internal JVM descriptor
                if (name.startsWith("["))
                {
                    yield Class.forName(name, false, cl);
                }
                // Handle human-readable array syntax "com.foo.Bar[]"
                if (name.endsWith("[]"))
                {
                    String elementClassName = name.substring(0, name.length() - 2);
                    Class<?> elementClass = loadClass(cl, elementClassName);
                    yield Array.newInstance(elementClass, 0).getClass();
                }
                yield cl.loadClass(name);
            }
        };
    }
}
