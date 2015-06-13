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

package org.jetbrains.kotlin.descriptors.impl

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.resolve.scopes.LazyScopeAdapter
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.utils.sure
import java.util.LinkedHashSet
import kotlin.properties.Delegates

public class ModuleDescriptorImpl(
        moduleName: Name,
        private val storageManager: StorageManager,
        private val moduleParameters: ModuleParameters
) : DeclarationDescriptorImpl(Annotations.EMPTY, moduleName), ModuleDescriptor, ModuleParameters by moduleParameters {
    init {
        if (!moduleName.isSpecial()) {
            throw IllegalArgumentException("Module name must be special: $moduleName")
        }
    }

    private var dependencies: ModuleDependencies? = null
    private var packageFragmentProviderForModuleContent: PackageFragmentProvider? = null

    private var _packageViewManager: PackageViewManager? = null

    override val packageViewManager: PackageViewManager
        get() = _packageViewManager!!
    
    private val packageFragmentProviderForWholeModuleWithDependencies by Delegates.lazy {
        val moduleDependencies = dependencies.sure { "Dependencies of module $id were not set before querying module content" }
        val dependenciesDescriptors = moduleDependencies.descriptors
        assert(this in dependenciesDescriptors) { "Module ${id} is not contained in his own dependencies, this is probably a misconfiguration" }
        dependenciesDescriptors.forEach {
            dependency ->
            assert(dependency.isInitialized) {
                "Dependency module ${dependency.id} was not initialized by the time contents of dependent module ${this.id} were queried"
            }
        }
        CompositePackageFragmentProvider(dependenciesDescriptors.map {
            it.packageFragmentProviderForModuleContent!!
        })
    }

    public val isInitialized: Boolean
        get() = packageFragmentProviderForModuleContent != null

    public fun setDependencies(dependencies: ModuleDependencies) {
        assert(this.dependencies == null) { "Dependencies of $id were already set" }
        this.dependencies = dependencies
    }

    public fun setDependencies(vararg descriptors: ModuleDescriptorImpl) {
        setDependencies(descriptors.toList())
    }

    public fun setDependencies(descriptors: List<ModuleDescriptorImpl>) {
        setDependencies(ModuleDependenciesImpl(descriptors))
    }

    private val id: String
        get() = getName().toString()

    /*
     * Call initialize() to set module contents. Uninitialized module cannot be queried for its contents.
     */
    public fun initialize(providerForModuleContent: PackageFragmentProvider) {
        assert(!isInitialized) { "Attempt to initialize module $id twice" }
        this.packageFragmentProviderForModuleContent = providerForModuleContent
        if (this._packageViewManager == null) {
            this._packageViewManager = PackageViewManagerImpl(this, storageManager)
        }
    }

    public fun setPackageViewManager(packageViewManager: PackageViewManager) {
        assert(!isInitialized) { "Custom package view manager should be set before module content is initialized" }
        this._packageViewManager = packageViewManager
    }

    val packageFragmentProvider: PackageFragmentProvider
        get() = packageFragmentProviderForWholeModuleWithDependencies

    private val friendModules = LinkedHashSet<ModuleDescriptor>()

    override fun isFriend(other: ModuleDescriptor) = other == this || other in friendModules

    public fun addFriend(friend: ModuleDescriptorImpl): Unit {
        assert(friend != this) { "Attempt to make module $id a friend to itself" }
        friendModules.add(friend)
    }

    override val builtIns: KotlinBuiltIns
        get() = KotlinBuiltIns.getInstance()
}

class PackageViewManagerImpl(private val module: ModuleDescriptorImpl, private val storageManager: StorageManager) : PackageViewManager {
    override fun getPackage(fqName: FqName): PackageViewDescriptor? {
        val fragments = module.packageFragmentProvider.getPackageFragments(fqName)
        return if (!fragments.isEmpty()) PackageViewDescriptorImpl(module, fqName, fragments) else null
    }

    override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
        return module.packageFragmentProvider.getSubPackagesOf(fqName, nameFilter)
    }

    override fun getParentView(packageView: PackageViewDescriptor): PackageViewDescriptor? {
        val fqName = packageView.getFqName()
        return if (fqName.isRoot()) null else return LazyPackageViewWrapper(fqName.parent(), module, storageManager)
    }
}

public interface ModuleDependencies {
    public val descriptors: List<ModuleDescriptorImpl>
}

public class ModuleDependenciesImpl(override val descriptors: List<ModuleDescriptorImpl>) : ModuleDependencies

public class LazyModuleDependencies(
        storageManager: StorageManager,
        computeDependencies: () -> List<ModuleDescriptorImpl>
) : ModuleDependencies {
    private val dependencies = storageManager.createLazyValue(computeDependencies)

    override val descriptors: List<ModuleDescriptorImpl>
        get() = dependencies()
}

/*
 this wrapper should only be created to save computation for package view
 that is known to exist but we do not necessarily need to query its contents

 ModuleDescriptor#getPackage should be used for most use cases
  */
public class LazyPackageViewWrapper(
        fqName: FqName, module: ModuleDescriptor, storageManager: StorageManager
)
: AbstractPackageViewDescriptor(fqName, module) {
    private val _delegate = storageManager.createNullableLazyValue {
        module.packageViewManager.getPackage(getFqName())
    }

    private val delegate: PackageViewDescriptor?
        get() = _delegate()

    private val scope = LazyScopeAdapter(storageManager.createLazyValue { delegate?.getMemberScope() ?: JetScope.Empty })

    override fun getMemberScope(): JetScope {
        return scope
    }

    override fun getFragments(): MutableList<PackageFragmentDescriptor> {
        return delegate?.getFragments() ?: listOf()
    }
}
