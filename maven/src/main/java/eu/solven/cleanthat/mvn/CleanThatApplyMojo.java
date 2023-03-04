/*
 * Copyright 2023 Benoit Lacelle - SOLVEN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.solven.cleanthat.mvn;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.springframework.context.ApplicationContext;

import eu.solven.cleanthat.any_language.ICodeCleaner;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.config.pojo.CleanthatStepProperties;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import eu.solven.cleanthat.language.javaparser.ICleanthatJavaparserConstants;
import eu.solven.cleanthat.mvn.codeprovider.OverlayCodeProviderWrite;

/**
 * This {@link org.apache.maven.plugin.Mojo} enables applying a single {@link IMutator} over current directory.
 * 
 * @author Benoit Lacelle
 *
 */
// https://maven.apache.org/guides/plugin/guide-java-plugin-development.html
@Mojo(name = CleanThatApplyMojo.MOJO_SINGLE,
		defaultPhase = LifecyclePhase.PROCESS_SOURCES,
		threadSafe = true,
		// Used to enable symbolSolving based on project dependencies
		requiresDependencyResolution = ResolutionScope.RUNTIME,
		// One may rely on the mvn plugin to clean a folder, even if no pom.xml is available
		requiresProject = false)
public class CleanThatApplyMojo extends ACleanThatSpringMojo {
	public static final String MOJO_SINGLE = "apply";

	@Override
	protected List<Class<?>> springClasses() {
		return CleanThatCleanThatMojo.cleanThatSpringClasses();
	}

	@Override
	public void doClean(ApplicationContext appContext) {
		if (isRunOnlyAtRoot() && !isThisTheExecutionRoot()) {
			// This will check it is called only if the command is run from the project root.
			// However, it will not prevent the plugin to be called on each module
			getLog().info("maven-cleanthat-plugin:" + MOJO_SINGLE + " skipped (not execution root)");
			return;
		}

		ICodeProviderWriter codeProvider = CleanThatMavenHelper.makeCodeProviderWriter(this);
		Map<Path, String> overlays = new LinkedHashMap<>();

		var topConfig = CleanthatRepositoryProperties.defaultRepository();
		var engineProperties = CleanthatEngineProperties.builder()
				.engine(ICleanthatJavaparserConstants.ENGINE_ID)
				.step(CleanthatStepProperties.builder().id(ICleanthatJavaparserConstants.STEP_ID).build())
				.build();
		topConfig.setEngines(Collections.singletonList(engineProperties));

		ICodeProviderWriter overlayed = new OverlayCodeProviderWrite(codeProvider, overlays);

		ICodeCleaner codeCleaner = CleanThatMavenHelper.makeCodeCleaner(appContext);
		codeCleaner.formatCodeGivenConfig(CleanThatApplyMojo.class.getSimpleName(), overlayed, isDryRun());
	}
}