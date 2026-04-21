/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus

import spock.lang.Specification
import spock.lang.Unroll

class CasualTypeResolverTest extends Specification
{
    def cl = getClass().classLoader

    @Unroll
    def "loadClass returns primitive #expected for '#input'"()
    {
        expect:
        CasualTypeResolver.loadClass(cl, input) == expected

        where:
        input      | expected
        "byte"     | byte.class
        "short"    | short.class
        "int"      | int.class
        "long"     | long.class
        "float"    | float.class
        "double"   | double.class
        "boolean"  | boolean.class
        "char"     | char.class
        "void"     | void.class
    }

    @Unroll
    def "loadClass resolves normal class '#className'"()
    {
        expect:
        CasualTypeResolver.loadClass(cl, className) == expectedClass

        where:
        className                    | expectedClass
        "java.lang.String"           | String.class
        "java.util.List"             | List.class
        "java.lang.Object"           | Object.class
        "spock.lang.Specification"   | Specification.class
    }

    @Unroll
    def "loadClass resolves array syntax '#arrayName'"()
    {
        expect:
        CasualTypeResolver.loadClass(cl, arrayName) == expectedClass

        where:
        arrayName                    | expectedClass
        "java.lang.String[]"         | String[].class
        "int[]"                      | int[].class
        "byte[]"                     | byte[].class
        "java.util.List[]"           | List[].class
        "spock.lang.Specification[]" | Specification[].class
    }

    @Unroll
    def "loadClass resolves multi-dimensional array '#arrayName'"()
    {
        expect:
        CasualTypeResolver.loadClass(cl, arrayName) == expectedClass

        where:
        arrayName                | expectedClass
        "int[][]"                | int[][].class
        "java.lang.String[][]"   | String[][].class
        "byte[][][]"             | byte[][][].class
    }

    def "loadClass resolves internal JVM array descriptor"()
    {
        expect:
        CasualTypeResolver.loadClass(cl, "[Ljava.lang.String;") == String[].class
        CasualTypeResolver.loadClass(cl, "[[I") == int[][].class
        CasualTypeResolver.loadClass(cl, "[B") == byte[].class
    }

    def "loadClass throws ClassNotFoundException for non-existent class"()
    {
        when:
        CasualTypeResolver.loadClass(cl, "com.nonexistent.DoesNotExist")

        then:
        thrown(ClassNotFoundException)
    }

    def "loadClass throws ClassNotFoundException for invalid primitive-like name"()
    {
        when:
        CasualTypeResolver.loadClass(cl, "intt")

        then:
        thrown(ClassNotFoundException)
    }
}
