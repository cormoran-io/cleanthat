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
package eu.solven.cleanthat.openrewrite.cases;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.google.common.io.ByteStreams;

import eu.solven.cleanthat.engine.java.refactorer.AAstRefactorer;
import eu.solven.cleanthat.engine.java.refactorer.OpenrewriteMutator;
import eu.solven.cleanthat.engine.java.refactorer.OpenrewriteRefactorer;
import eu.solven.cleanthat.engine.java.refactorer.javaparser.JavaparserTestHelpers;
import eu.solven.cleanthat.engine.java.refactorer.test.ATestCases;
import eu.solven.pepper.unittest.ILogDisabler;
import eu.solven.pepper.unittest.PepperTestHelper;

public class TestCommonStaticAnalysisCustom extends ATestCases<SourceFile, Result> {

	final OpenrewriteRefactorer refactorer = new OpenrewriteRefactorer(Arrays.asList());

	@Test
	public void testWeirdCharacters() throws IOException {
		Resource testRoaringBitmapSource =
				new ClassPathResource("/source/do_not_format_me/shaft_engine/PropertyFileManager.java");
		var asString =
				new String(ByteStreams.toByteArray(testRoaringBitmapSource.getInputStream()), StandardCharsets.UTF_8);

		Environment environment = Environment.builder().scanRuntimeClasspath("org.openrewrite.staticanalysis").build();
		Recipe recipe = environment.activateRecipes("org.openrewrite.staticanalysis.CommonStaticAnalysis");

		OpenrewriteMutator mutator = new OpenrewriteMutator(recipe);

		// LiteralsFirstInComparisons transformer = new LiteralsFirstInComparisons();
		JavaParser javaParser = JavaparserTestHelpers.makeDefaultJavaParser(true);
		var compilationUnit = javaParser.parse(asString).getResult().get();

		SourceFile openrewriteCompilationUnit = convertToAst(compilationUnit);

		Optional<Result> transformed;
		try (ILogDisabler logDisabler = PepperTestHelper.disableLog(OpenrewriteMutator.class)) {
			transformed = mutator.walkAst(openrewriteCompilationUnit);
		}

		// This test is flaky
		if (transformed.isEmpty()) {
			// Sometimes, OpenRewrite does not do weird things
			Assertions.assertThat(transformed).isEmpty();
		} else {
			// https://github.com/ShaftHQ/SHAFT_ENGINE/pull/909/commits/42e54e077491ad0b78ed81ee8149d4b681d22e8b
			Assertions.assertThat(transformed.get().getAfter().printAll()).doesNotContain("/*~~(null)~~>*/");
		}
	}

	@Override
	public SourceFile convertToAst(Node pre) {
		var asString = pre.toString();

		return AAstRefactorer.parse(refactorer, asString)
				.orElseThrow(() -> new IllegalArgumentException("Invalid input"));
	}

	@Override
	public String resultToString(Result post) {
		return post.getAfter().printAll();
	}

	@Override
	public String astToString(SourceFile asAst) {
		return asAst.toString();
	}
}