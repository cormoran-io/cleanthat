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
package eu.solven.cleanthat.engine.java.refactorer.cases;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me.JUnit4ToJUnit5Cases;
import eu.solven.cleanthat.engine.java.refactorer.test.ARefactorerCases;
import java.io.IOException;
import java.util.Collection;
import org.junit.runners.Parameterized.Parameters;

public class TestJUnit4ToJUnit5Cases extends AParameterizesRefactorerCases {

	private static ARefactorerCases getStaticRefactorerCases() {
		return new JUnit4ToJUnit5Cases();
	}

	public TestJUnit4ToJUnit5Cases(JavaParser javaParser, String testName, ClassOrInterfaceDeclaration testCase) {
		super(javaParser, testName, testCase);
	}

	// https://github.com/junit-team/junit4/wiki/parameterized-tests
	@Parameters(name = "{1}")
	public static Collection<Object[]> data() throws IOException {
		ARefactorerCases testCases = getStaticRefactorerCases();
		return listCases(testCases);
	}

	@Override
	protected ARefactorerCases getCases() {
		return getStaticRefactorerCases();
	}

}
