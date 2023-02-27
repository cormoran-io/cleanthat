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
package eu.solven.cleanthat.engine.java.refactorer.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

import org.codehaus.plexus.languages.java.version.JavaVersion;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutatorExternalReferences;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.AllIncludingDraftCompositeMutators;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.AllIncludingDraftSingleMutators;
import eu.solven.cleanthat.engine.java.refactorer.mutators.composite.CompositeMutator;
import eu.solven.cleanthat.engine.java.refactorer.test.LocalClassTestHelper;

// BEWARE: This will generate a versioned file: It may lead to unexpected result. However, it will also make sure this file is often up-to-date
public class TestGenerateDocumentation {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGenerateDocumentation.class);

	static final String EOL = System.lineSeparator();

	static String URL_PREFIX = "java/src/main/java/";
	static String URL_PREFIX_TEST = "java/src/test/java/";

	@Test
	public void doGenerateDocumentation() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("# Mutators");

		addSingleMutators(sb, false);
		addSingleMutators(sb, true);
		addCompositeMutators(sb);

		sb.append(EOL).append("---").append(EOL).append(EOL).append("Generated by ");
		sb.append(this.getClass().getSimpleName()).append(']');
		String relativePath = this.getClass().getName().replace('.', '/');
		sb.append('(').append(URL_PREFIX_TEST).append(relativePath).append(".java").append(')').append(EOL);

		Path srcMainResources = LocalClassTestHelper.getSrcMainResourceFolder();

		Path targetFile = srcMainResources.resolve("../../../MUTATORS.generated.MD").normalize();

		LOGGER.info("Writing into {}", targetFile);
		Files.writeString(targetFile, sb.toString(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
	}

	private static void addSingleMutators(StringBuilder sb, boolean isDraft) {
		CompositeMutator<IMutator> allSingles =
				new AllIncludingDraftSingleMutators(JavaVersion.parse(IJdkVersionConstants.LAST));

		sb.append(EOL).append("## Single Mutators").append(isDraft ? " (DRAFTs)" : " PRD-READY");

		for (IMutator mutator : allSingles.getUnderlyings()
				.stream()
				.filter(m -> isDraft == m.isDraft())
				.collect(Collectors.toList())) {
			addMutatorInfo(sb, mutator);
		}
	}

	private static void addCompositeMutators(StringBuilder sb) {
		CompositeMutator<CompositeMutator<?>> allComposites =
				new AllIncludingDraftCompositeMutators(JavaVersion.parse(IJdkVersionConstants.LAST));

		sb.append(EOL).append("## Composite Mutators");

		for (CompositeMutator<?> mutator : allComposites.getUnderlyings()) {
			addMutatorInfo(sb, mutator);
		}
	}

	private static void addMutatorInfo(StringBuilder sb, IMutator mutator) {
		sb.append(EOL).append(EOL).append("### ").append('[').append(mutator.getClass().getSimpleName()).append(']');

		String relativePath = mutator.getClass().getName().replace('.', '/');
		sb.append('(').append(URL_PREFIX).append(relativePath).append(".java").append(')').append(EOL);

		addRefToExternalRules(sb, mutator);

		if (mutator.isDraft()) {
			sb.append(EOL).append(EOL).append("isDraft");
		}

		sb.append(EOL).append(EOL).append("Require sourceJdk: ").append(mutator.minimalJavaVersion());
	}

	private static void addRefToExternalRules(StringBuilder sb, IMutator mutator) {
		mutator.getPmdId().ifPresent(ruleId -> {
			String url = "";

			if (mutator instanceof IMutatorExternalReferences) {
				url = ((IMutatorExternalReferences) mutator).pmdUrl();
			}

			sb.append(EOL).append(EOL);
			if (Strings.isNullOrEmpty(url)) {
				sb.append("PMD: [").append(ruleId).append("](").append(url).append(')');
			} else {
				sb.append("PMD: ").append(ruleId);
			}
		});
		mutator.getCheckstyleId().ifPresent(ruleId -> {
			String url = "";

			if (mutator instanceof IMutatorExternalReferences) {
				url = ((IMutatorExternalReferences) mutator).checkstyleUrl();
			}

			sb.append(EOL).append(EOL);
			if (Strings.isNullOrEmpty(url)) {
				sb.append("CheckStyle: [").append(ruleId).append("](").append(url).append(')');
			} else {
				sb.append("CheckStyle: ").append(ruleId);
			}
		});
		mutator.getSonarId().ifPresent(ruleId -> {
			String url = "";

			if (mutator instanceof IMutatorExternalReferences) {
				url = ((IMutatorExternalReferences) mutator).sonarUrl();
			}

			sb.append(EOL).append(EOL);
			if (Strings.isNullOrEmpty(url)) {
				sb.append("Sonar: [").append(ruleId).append("](").append(url).append(')');
			} else {
				sb.append("Sonar: ").append(ruleId);
			}
		});
	}
}
