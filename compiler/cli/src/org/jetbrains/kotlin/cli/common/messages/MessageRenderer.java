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

package org.jetbrains.kotlin.cli.common.messages;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import kotlin.KotlinPackage;
import kotlin.io.IoPackage;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.internal.CLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface MessageRenderer {

    MessageRenderer XML = new MessageRenderer() {
        @Override
        public String renderPreamble() {
            return "<MESSAGES>";
        }

        @Override
        public String render(@NotNull CompilerMessageSeverity severity, @NotNull String message, @NotNull CompilerMessageLocation location) {
            StringBuilder out = new StringBuilder();
            out.append("<").append(severity.toString());
            if (location.getPath() != null) {
                out.append(" path=\"").append(e(location.getPath())).append("\"");
                out.append(" line=\"").append(location.getLine()).append("\"");
                out.append(" column=\"").append(location.getColumn()).append("\"");
            }
            out.append(">");

            out.append(e(message));

            out.append("</").append(severity.toString()).append(">\n");
            return out.toString();
        }

        private String e(String str) {
            return StringUtil.escapeXml(str);
        }

        @Override
        public String renderConclusion() {
            return "</MESSAGES>";
        }
    };

    MessageRenderer PLAIN_FULL_PATHS = new PlainText() {
        @Nullable
        @Override
        protected String getPath(@NotNull CompilerMessageLocation location) {
            return location.getPath();
        }
    };

    MessageRenderer PLAIN_RELATIVE_PATHS = new PlainText() {
        private final File cwd = new File(".").getAbsoluteFile();

        @Nullable
        @Override
        protected String getPath(@NotNull CompilerMessageLocation location) {
            String path = location.getPath();
            return cwd == null || path == null ? path : IoPackage.relativePath(cwd, new File(path));
        }
    };

    abstract class PlainText implements MessageRenderer {
        // AnsiConsole doesn't check isatty() for stderr (see https://github.com/fusesource/jansi/pull/35).
        // TODO: investigate why ANSI escape codes on Windows only work in REPL for some reason
        private static final boolean COLOR_ENABLED =
                !SystemInfo.isWindows &&
                CLibrary.isatty(CLibrary.STDERR_FILENO) != 0;

        private static final String LINE_SEPARATOR = LineSeparator.getSystemLineSeparator().getSeparatorString();

        @Override
        public String renderPreamble() {
            return "";
        }

        @Override
        public String render(
                @NotNull CompilerMessageSeverity severity, @NotNull String message, @NotNull CompilerMessageLocation location
        ) {
            StringBuilder result = new StringBuilder();

            int line = location.getLine();
            int column = location.getColumn();
            String lineContent = location.getLineContent();

            String path = getPath(location);
            if (path != null) {
                result.append(path);
                result.append(":");
                if (line > 0) {
                    result.append(line).append(":");
                    if (column > 0) {
                        result.append(column).append(":");
                    }
                }
                result.append(" ");
            }

            if (COLOR_ENABLED) {
                Ansi ansi = Ansi.ansi()
                        .bold()
                        .fg(severityColor(severity))
                        .a(severity.name().toLowerCase())
                        .a(": ")
                        .reset()
                        .bold();

                // Only make the first line of the message bold. Otherwise long overload ambiguity errors or exceptions are hard to read
                String decapitalized = decapitalizeIfNeeded(message);
                int firstNewline = decapitalized.indexOf(LINE_SEPARATOR);
                if (firstNewline < 0) {
                    result.append(ansi.a(decapitalized).reset());
                }
                else {
                    result.append(ansi.a(decapitalized.substring(0, firstNewline)).reset().a(decapitalized.substring(firstNewline)));
                }
            }
            else {
                result.append(severity.name().toLowerCase());
                result.append(": ");
                result.append(decapitalizeIfNeeded(message));
            }

            if (lineContent != null && 1 <= column && column <= lineContent.length() + 1) {
                result.append(LINE_SEPARATOR);
                result.append(lineContent);
                result.append(LINE_SEPARATOR);
                result.append(KotlinPackage.repeat(" ", column - 1));
                result.append("^");
            }

            return result.toString();
        }

        @NotNull
        private static String decapitalizeIfNeeded(@NotNull String message) {
            // TODO: invent something more clever
            // An ad-hoc heuristic to prevent decapitalization of some names
            if (message.startsWith("Java") || message.startsWith("Kotlin")) {
                return message;
            }

            // For abbreviations and capitalized text
            if (message.length() >= 2 && Character.isUpperCase(message.charAt(0)) && Character.isUpperCase(message.charAt(1))) {
                return message;
            }

            return KotlinPackage.decapitalize(message);
        }

        @NotNull
        private static Ansi.Color severityColor(@NotNull CompilerMessageSeverity severity) {
            switch (severity) {
                case EXCEPTION:
                    return Ansi.Color.RED;
                case ERROR:
                    return Ansi.Color.RED;
                case WARNING:
                    return Ansi.Color.YELLOW;
                case INFO:
                    return Ansi.Color.BLUE;
                case LOGGING:
                    return Ansi.Color.BLUE;
                case OUTPUT:
                    return Ansi.Color.BLUE;
                default:
                    throw new UnsupportedOperationException("Unknown severity: " + severity);
            }
        }

        @Nullable
        protected abstract String getPath(@NotNull CompilerMessageLocation location);

        @Override
        public String renderConclusion() {
            return "";
        }
    }

    String renderPreamble();

    String render(@NotNull CompilerMessageSeverity severity, @NotNull String message, @NotNull CompilerMessageLocation location);

    String renderConclusion();
}
