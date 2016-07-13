/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.StartParameter;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.composite.CompositeContextBuildActionRunner;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.GradleBuildController;

import java.util.List;

public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final StartParameter buildStartParam;
    private final ServiceRegistry sharedServices;

    public DefaultCompositeContextBuilder(StartParameter startParameter, ServiceRegistry services) {
        this.buildStartParam = startParameter;
        this.sharedServices = services;
    }

    @Override
    public void addToCompositeContext(List<GradleParticipantBuild> participantBuilds, BuildRequestContext requestContext, boolean propagateFailures) {
        BuildClientMetaData client = requestContext.getClient();
        addToCompositeContext(participantBuilds, requestContext, client, propagateFailures);
    }

    @Override
    public void addToCompositeContext(List<GradleParticipantBuild> participantBuilds, BuildClientMetaData client, boolean propagateFailures) {
        addToCompositeContext(participantBuilds, null, client, propagateFailures);
    }

    private void addToCompositeContext(List<GradleParticipantBuild> participantBuilds, BuildRequestContext requestContext, BuildClientMetaData client, boolean propagateFailures) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeBuildContext context = sharedServices.get(CompositeBuildContext.class);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(sharedServices.get(StyledTextOutputFactory.class), buildStartParam, client);
        CompositeContextBuildActionRunner contextBuilder = new CompositeContextBuildActionRunner(context, propagateFailures, exceptionReporter);

        for (GradleParticipantBuild participant : participantBuilds) {
            StartParameter participantStartParam = buildStartParam.newInstance();
            participantStartParam.setProjectDir(participant.getProjectDir());

            participantStartParam.setConfigureOnDemand(false);
            if (participantStartParam.getLogLevel() == LogLevel.LIFECYCLE) {
                participantStartParam.setLogLevel(LogLevel.QUIET);
                LOGGER.lifecycle("[composite-build] Configuring participant: " + participant.getProjectDir());
            }

            GradleLauncher gradleLauncher = createGradleLauncher(participantStartParam, requestContext, gradleLauncherFactory);

            contextBuilder.run(new GradleBuildController(gradleLauncher));
        }
    }

    private GradleLauncher createGradleLauncher(StartParameter participantStartParam, BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory) {
        if (requestContext == null) {
            return gradleLauncherFactory.newInstance(participantStartParam, sharedServices, false);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(participantStartParam, requestContext, sharedServices);
        gradleLauncher.addStandardOutputListener(requestContext.getOutputListener());
        gradleLauncher.addStandardErrorListener(requestContext.getErrorListener());
        return gradleLauncher;
    }
}
