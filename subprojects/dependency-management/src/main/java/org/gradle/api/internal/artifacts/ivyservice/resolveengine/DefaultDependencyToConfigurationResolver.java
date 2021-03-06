/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencyToConfigurationResolver implements DependencyToConfigurationResolver {
    // TODO - don't pass in 'from' configuration - the dependency should have whatever context it needs
    public Set<ConfigurationMetadata> resolveTargetConfigurations(DependencyMetadata dependencyMetadata, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent) {
        // TODO - resolve directly to config meta data
        Set<String> targetConfigurationNames = new LinkedHashSet<String>();
        for (String config : dependencyMetadata.getModuleConfigurations()) {
            if (config.equals("*") || config.equals("%")) {
                collectTargetConfiguration(dependencyMetadata, fromConfiguration, fromConfiguration.getName(), targetComponent, targetConfigurationNames);
            } else if (fromConfiguration.getHierarchy().contains(config)) {
                collectTargetConfiguration(dependencyMetadata, fromConfiguration, config, targetComponent, targetConfigurationNames);
            }
        }

        Set<ConfigurationMetadata> targets = new LinkedHashSet<ConfigurationMetadata>();
        for (String targetConfigurationName : targetConfigurationNames) {
            ConfigurationMetadata targetConfiguration = targetComponent.getConfiguration(targetConfigurationName);
            targets.add(targetConfiguration);
        }
        return targets;
    }

    private void collectTargetConfiguration(DependencyMetadata dependencyDescriptor, ConfigurationMetadata fromConfiguration, String mappingRhs, ComponentResolveMetadata targetModule, Collection<String> targetConfigs) {
        String[] dependencyConfigurations = dependencyDescriptor.getDependencyConfigurations(mappingRhs, fromConfiguration.getName());
        for (String target : dependencyConfigurations) {
            String candidate = target;
            int startFallback = candidate.indexOf('(');
            if (startFallback >= 0) {
                if (candidate.charAt(candidate.length() - 1) == ')') {
                    String preferred = candidate.substring(0, startFallback);
                    if (targetModule.getConfiguration(preferred) != null) {
                        targetConfigs.add(preferred);
                        continue;
                    }
                    candidate = candidate.substring(startFallback + 1, candidate.length() - 1);
                }
            }
            if (candidate.equals("*")) {
                for (String configName : targetModule.getConfigurationNames()) {
                    if (targetModule.getConfiguration(configName).isVisible()) {
                        targetConfigs.add(configName);
                    }
                }
                continue;
            }
            if (targetModule.getConfiguration(candidate) != null) {
                targetConfigs.add(candidate);
                continue;
            }
            throw new RuntimeException(String.format("Module version %s, configuration '%s' declares a dependency on configuration '%s' which is not declared in the module descriptor for %s",
                    fromConfiguration.getComponent().getId(), fromConfiguration.getName(),
                    target, targetModule.getId()));
        }
    }
}
