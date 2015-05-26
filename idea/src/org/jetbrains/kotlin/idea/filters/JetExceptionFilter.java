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

package org.jetbrains.kotlin.idea.filters;

import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import kotlin.KotlinPackage;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.util.DebuggerUtils;
import org.jetbrains.kotlin.load.kotlin.PackageClassUtils;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.JetFile;
import org.jetbrains.kotlin.resolve.jvm.JvmClassName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JetExceptionFilter implements Filter {
    private final ExceptionFilter exceptionFilter;
    private final GlobalSearchScope searchScope;

    public JetExceptionFilter(@NotNull GlobalSearchScope searchScope) {
        this.exceptionFilter = new ExceptionFilter(searchScope);
        this.searchScope = searchScope;
    }

    @Nullable
    private HyperlinkInfo createHyperlinkInfo(@NotNull String line) {
        Project project = searchScope.getProject();
        if (project == null) return null;

        StackTraceElement element = parseStackTraceLine(line);
        if (element == null) return null;

        String fileName = element.getFileName();

        // fullyQualifiedName is of format "package.Class$Inner"
        String fullyQualifiedName = element.getClassName();

        // All classes except package classes, package parts and top level closures are handled correctly in the default ExceptionFilter
        if (!isPackageClassOrPackagePartPrefixedName(fullyQualifiedName)) return null;

        String internalName = fullyQualifiedName.replace('.', '/');
        JvmClassName jvmClassName = JvmClassName.byInternalName(internalName);

        JetFile file = DebuggerUtils.findSourceFileForClass(project, searchScope, jvmClassName, fileName, element.getLineNumber() - 1);

        if (file == null) return null;
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return null;

        return new OpenFileHyperlinkInfo(project, virtualFile, element.getLineNumber() - 1);
    }

    private static boolean isPackageClassOrPackagePartPrefixedName(String fqName) {
        if (fqName.equals(PackageClassUtils.getPackageClassName(FqName.ROOT))) {
            return true;
        }

        int lastDot = fqName.lastIndexOf('.');
        String classNameWithInners = fqName.substring(lastDot + 1);
        int firstDollar = classNameWithInners.indexOf('$');
        String className = firstDollar >= 0 ? classNameWithInners.substring(0, firstDollar) : classNameWithInners;

        String packageClassName = PackageClassUtils.getPackageClassName(new FqName(fqName).parent());
        return packageClassName.equals(className);
    }

    // Matches strings like "\tat test.TestPackage$foo$f$1.invoke(a.kt:3)\n"
    private static final Pattern STACK_TRACE_ELEMENT_PATTERN = Pattern.compile("^\\s*at\\s+(.+)\\.(.+)\\((.+):(\\d+)\\)\\s*$");

    @Nullable
    private static StackTraceElement parseStackTraceLine(@NotNull String line) {
        Matcher matcher = STACK_TRACE_ELEMENT_PATTERN.matcher(line);
        if (matcher.matches()) {
            String declaringClass = matcher.group(1);
            String methodName = matcher.group(2);
            String fileName = matcher.group(3);
            String lineNumber = matcher.group(4);
            //noinspection ConstantConditions
            return new StackTraceElement(declaringClass, methodName, fileName, Integer.parseInt(lineNumber));
        }
        return null;
    }

    @NotNull
    private Result patchResult(@NotNull Result result, @NotNull String line) {
        final HyperlinkInfo newHyperlinkInfo = createHyperlinkInfo(line);
        if (newHyperlinkInfo == null) return result;

        return new Result(KotlinPackage.map(result.getResultItems(), new Function1<ResultItem, ResultItem>() {
            @Override
            public ResultItem invoke(ResultItem item) {
                return new ResultItem(item.getHighlightStartOffset(), item.getHighlightEndOffset(), newHyperlinkInfo,
                                      item.getHighlightAttributes());
            }
        }));
    }

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
        Result result = exceptionFilter.applyFilter(line, entireLength);
        return result == null ? null : patchResult(result, line);
    }
}
