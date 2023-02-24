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
package eu.solven.cleanthat.spotless.language;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.diffplug.spotless.markdown.FlexmarkStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.spotless.AFormatterFactory;
import eu.solven.cleanthat.spotless.pojo.SpotlessFormatterProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepParametersProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepProperties;

/**
 * Configure Spotless engine for '.MD' files
 * 
 * @author Benoit Lacelle
 *
 */
public class MarkdownFormatterFactory extends AFormatterFactory {
	// CleanThat will call spotless from the root directory: process any '*.MD' file from there
	@Override
	public Set<String> defaultIncludes() {
		return ImmutableSet.of("**/*.MD", "**/*.md");
	}

	@Override
	public MarkdownFormatterStepFactory makeStepFactory(ICodeProvider codeProvider,
			SpotlessFormatterProperties formatterProperties) {
		return new MarkdownFormatterStepFactory(this, codeProvider, formatterProperties);
	}

	@Override
	public List<SpotlessStepProperties> exampleSteps() {
		SpotlessStepProperties flexmark = SpotlessStepProperties.builder().id("flexmark").build();
		SpotlessStepParametersProperties flexmarkParameters = new SpotlessStepParametersProperties();
		flexmarkParameters.putProperty("version", FlexmarkStep.defaultVersion());
		flexmark.setParameters(flexmarkParameters);

		SpotlessStepProperties freshmark = SpotlessStepProperties.builder().id("freshmark").build();
		SpotlessStepParametersProperties freshmarkParameters = new SpotlessStepParametersProperties();
		freshmarkParameters.putProperty("properties", Map.of("k1", "v1"));
		freshmark.setParameters(freshmarkParameters);

		return ImmutableList.<SpotlessStepProperties>builder().add(flexmark).add(freshmark).build();
	}
}
