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

package org.jetbrains.kotlin.psi.psiUtil

import com.intellij.psi.PsiElement

public data class PsiChildRange(public val first: PsiElement?, public val last: PsiElement?) : Sequence<PsiElement> {
    init {
        if (first == null) {
            assert(last == null)
        }
        else {
            assert(first.getParent() == last!!.getParent())
        }
    }

    public val isEmpty: Boolean
        get() = first == null

    override fun iterator(): Iterator<PsiElement> {
        val sequence = if (first == null) {
            emptySequence<PsiElement>()
        }
        else {
            val afterLast = last!!.getNextSibling()
            first.siblings().takeWhile { it != afterLast }
        }
        return sequence.iterator()
    }

    companion object {
        public val EMPTY: PsiChildRange = PsiChildRange(null, null)

        public fun singleElement(element: PsiElement): PsiChildRange = PsiChildRange(element, element)
    }
}