package com.sympauthy

import java.io.File

/**
 * The classes compiled from the sources into [packageName] and every package under it, nested ones
 * included. What the annotation processor generated beside them, named from `$` or carrying
 * `$Intercepted`, is left out, and so are the tests compiled into the same packages.
 *
 * Each class is loaded without being initialized, so reading its declaration runs none of its code.
 */
internal fun compiledClassesOf(packageName: String): List<Class<*>> {
    val loader = TestClasses::class.java.classLoader
    val testOutput = File(TestClasses::class.java.protectionDomain.codeSource.location.toURI())
    return loader.getResources(packageName.replace('.', '/')).toList()
        .filter { it.protocol == "file" }
        .map { File(it.toURI()) }
        .filterNot { it.startsWith(testOutput) }
        .flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .filterNot { it.name.startsWith("$") || "\$Intercepted" in it.name }
                .map { file ->
                    val name = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                    "$packageName.$name"
                }
        }
        .distinct()
        .map { Class.forName(it, false, loader) }
}

/** A class compiled into the test output, which locates it. */
private object TestClasses
