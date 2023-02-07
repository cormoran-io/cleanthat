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

import eu.solven.pepper.unittest.ILogDisabler;
import eu.solven.pepper.unittest.PepperTestHelper;
import java.io.File;
import org.apache.maven.project.MavenProject;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.boot.SpringApplication;

public class TestCleanThatCleanThatMojoTest extends ACleanThatMojoTest {
	@Test
	public void testCleanthat_noConfig() throws Exception {
		String relativePathToParent = "/unit/project-to-test";
		File readWriteFolder = temporaryFolder(relativePathToParent);

		// Ensure the test resources does not hold a cleanthat.yaml
		File cleanthatYaml = new File(readWriteFolder, "cleanthat.yaml");
		Assertions.assertThat(cleanthatYaml).doesNotExist();

		MavenProject project = prepareMojoInTemporaryFolder(relativePathToParent, readWriteFolder);

		CleanThatCleanThatMojo myMojo = (CleanThatCleanThatMojo) lookupConfiguredMojo(project, "cleanthat");

		try (ILogDisabler logCLoser = PepperTestHelper.disableLog(SpringApplication.class)) {
			Assertions.assertThatThrownBy(() -> myMojo.execute()).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	public void testCleanthat_initThenLint() throws Exception {
		String relativePathToParent = "/unit/project-to-test";
		File readWriteFolder = temporaryFolder(relativePathToParent);

		// Ensure the test resources does not hold a cleanthat.yaml
		File cleanthatYaml = new File(readWriteFolder, "cleanthat.yaml");
		Assertions.assertThat(cleanthatYaml).doesNotExist();

		MavenProject project = prepareMojoInTemporaryFolder(relativePathToParent, readWriteFolder);

		// 'init' will create a cleanthat.yaml
		CleanThatInitMojo initMojo = lookupConfiguredInitMojo(project);
		initMojo.execute();

		CleanThatCleanThatMojo fixMojo = lookupConfiguredFixMojo(project);
		fixMojo.execute();
	}
}