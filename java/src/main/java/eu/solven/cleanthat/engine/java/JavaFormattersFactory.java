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
package eu.solven.cleanthat.engine.java;

import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatStepProperties;
import eu.solven.cleanthat.config.pojo.ICleanthatStepParametersProperties;
import eu.solven.cleanthat.engine.ASourceCodeFormatterFactory;
import eu.solven.cleanthat.engine.java.refactorer.JavaRefactorer;
import eu.solven.cleanthat.engine.java.refactorer.JavaRefactorerProperties;
import eu.solven.cleanthat.formatter.CleanthatSession;
import eu.solven.cleanthat.formatter.ILintFixer;
import eu.solven.cleanthat.formatter.ILintFixerWithId;
import eu.solven.cleanthat.language.IEngineProperties;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formatter for Java
 *
 * @author Benoit Lacelle
 */
public class JavaFormattersFactory extends ASourceCodeFormatterFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaFormattersFactory.class);

	public JavaFormattersFactory(ConfigHelpers configHelpers) {
		super(configHelpers);
	}

	@Override
	public String getEngine() {
		return "java";
	}

	@SuppressWarnings("PMD.TooFewBranchesForASwitchStatement")
	@Override
	public ILintFixer makeLintFixer(CleanthatSession cleanthatSession,
			IEngineProperties engineProperties,
			CleanthatStepProperties stepProperties) {
		ILintFixerWithId processor;
		String stepId = stepProperties.getId();
		ICleanthatStepParametersProperties parameters = getParameters(stepProperties);

		LOGGER.debug("Processing: {}", stepId);

		switch (stepId) {
		case JavaRefactorer.ID_REFACTORER: {
			JavaRefactorerProperties processorConfig = convertValue(parameters, JavaRefactorerProperties.class);
			processor = new JavaRefactorer(engineProperties, processorConfig);
			break;
		}

		default:
			throw new IllegalArgumentException("Unknown step: " + stepId);
		}

		if (!processor.getId().equals(stepId)) {
			throw new IllegalStateException("Inconsistency: " + processor.getId() + " vs " + stepId);
		}

		return processor;
	}

	@Override
	public CleanthatEngineProperties makeDefaultProperties() {
		return CleanthatEngineProperties.builder()
				.engine(getEngine())
				.step(CleanthatStepProperties.builder()
						.id(JavaRefactorer.ID_REFACTORER)
						.parameters(JavaRefactorerProperties.defaults())
						.build())
				.build();
	}

	@Override
	public Map<String, String> makeCustomDefaultFiles(CleanthatEngineProperties engineProperties) {
		return Map.of();
	}

	@Override
	public Set<String> getDefaultIncludes() {
		return Set.of("glob:**/src/**/*.java");
	}

}
