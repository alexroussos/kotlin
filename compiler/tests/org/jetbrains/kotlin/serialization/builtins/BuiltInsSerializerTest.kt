/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.serialization.builtins

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.createBuiltInPackageFragmentProvider
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.jvm.compiler.LoadDescriptorUtil.TEST_PACKAGE_FQNAME
import org.jetbrains.kotlin.serialization.deserialization.ClassDescriptorFactory
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.test.JetTestUtils
import org.jetbrains.kotlin.test.TestCaseWithTmpdir
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator
import java.io.File
import java.io.FileInputStream

public class BuiltInsSerializerTest : TestCaseWithTmpdir() {
    private fun doTest(fileName: String) {
        val source = "compiler/testData/serialization/builtinsSerializer/$fileName"
        BuiltInsSerializer(dependOnOldBuiltIns = true).serialize(
                tmpdir,
                srcDirs = listOf(File(source)),
                extraClassPath = listOf(ForTestCompileRuntime.runtimeJarForTests()),
                onComplete = { totalSize, totalFiles -> }
        )

        val module = JetTestUtils.createEmptyModule("<module>")

        val packageFragmentProvider = createBuiltInPackageFragmentProvider(
                LockBasedStorageManager(), module, setOf(TEST_PACKAGE_FQNAME), ClassDescriptorFactory.EMPTY
        ) {
            val file = File(tmpdir, it)
            if (file.exists()) FileInputStream(file) else null
        }

        module.initialize(packageFragmentProvider)
        module.setDependencies(module, KotlinBuiltIns.getInstance().getBuiltInsModule())

        RecursiveDescriptorComparator.validateAndCompareDescriptorWithFile(
                module.getPackage(TEST_PACKAGE_FQNAME)!!,
                RecursiveDescriptorComparator.DONT_INCLUDE_METHODS_OF_OBJECT,
                File(source.replace(".kt", ".txt"))
        )
    }

    fun testSimple() {
        doTest("simple.kt")
    }

    fun testNestedClassesAndObjects() {
        doTest("nestedClassesAndObjects.kt")
    }

    fun testCompileTimeConstants() {
        doTest("compileTimeConstants.kt")
    }

    fun testAnnotationTargets() {
        doTest("annotationTargets.kt")
    }

    fun testPrimitives() {
        doTest("annotationArguments/primitives.kt")
    }

    fun testPrimitiveArrays() {
        doTest("annotationArguments/primitiveArrays.kt")
    }

    fun testString() {
        doTest("annotationArguments/string.kt")
    }

    fun testAnnotation() {
        doTest("annotationArguments/annotation.kt")
    }

    fun testEnum() {
        doTest("annotationArguments/enum.kt")
    }
}
