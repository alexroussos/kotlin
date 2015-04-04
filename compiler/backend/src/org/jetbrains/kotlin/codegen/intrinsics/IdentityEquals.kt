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

package org.jetbrains.kotlin.codegen.intrinsics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.psi.JetBinaryExpression
import org.jetbrains.kotlin.psi.JetCallExpression
import org.jetbrains.kotlin.psi.JetExpression
import org.jetbrains.org.objectweb.asm.Type

import org.jetbrains.kotlin.resolve.jvm.AsmTypes.OBJECT_TYPE

public class IdentityEquals : LazyIntrinsicMethod() {
    override fun generateImpl(codegen: ExpressionCodegen, returnType: Type, element: PsiElement?, arguments: List<JetExpression>, receiver: StackValue): StackValue {
        val left: StackValue
        val right: StackValue
        if (element is JetCallExpression) {
            left = receiver
            right = codegen.gen(arguments.get(0))
        }
        else {
            assert(element is JetBinaryExpression)
            val e = element as JetBinaryExpression
            left = codegen.gen(e.getLeft())
            right = codegen.gen(e.getRight())
        }
        return StackValue.cmp(JetTokens.EQEQEQ, OBJECT_TYPE, left, right)
    }


    override fun supportCallable(): Boolean {
        return false
    }

    override fun toCallable(method: CallableMethod): ExtendedCallable {
        return IntrinsicCallable.binaryIntrinsic(method.getReturnType(), OBJECT_TYPE, nullOrObject(method.getThisType()), nullOrObject(method.getReceiverClass())) {
            v -> v.invokestatic(IntrinsicMethods.INTRINSICS_CLASS_NAME, "areEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
            AsmUtil.genAreEqualCall(v)
        }
    }

}
