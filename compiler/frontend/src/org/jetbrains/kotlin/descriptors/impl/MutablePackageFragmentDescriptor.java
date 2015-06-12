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

package org.jetbrains.kotlin.descriptors.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.resolve.scopes.AbstractScopeAdapter;
import org.jetbrains.kotlin.resolve.scopes.JetScope;

public class MutablePackageFragmentDescriptor extends PackageFragmentDescriptorImpl {
    private final JetScope scope;

    public MutablePackageFragmentDescriptor(@NotNull ModuleDescriptor module, @NotNull FqName fqName) {
        super(module, fqName);

        scope = new AbstractScopeAdapter() {
            @NotNull
            @Override
            protected JetScope getWorkerScope() {
                return Empty.INSTANCE$;
            }

            @NotNull
            @Override
            public DeclarationDescriptor getContainingDeclaration() {
                return MutablePackageFragmentDescriptor.this;
            }
        };
    }

    @NotNull
    @Override
    public JetScope getMemberScope() {
        return scope;
    }
}
