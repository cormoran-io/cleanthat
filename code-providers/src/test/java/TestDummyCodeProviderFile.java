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
import eu.solven.cleanthat.codeprovider.DummyCodeProviderFile;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestDummyCodeProviderFile {
	@Test
	public void testMissingSlash() {
		Assertions.assertThatThrownBy(() -> new DummyCodeProviderFile("dir/file", null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testTrailingDoubleSlash() {
		Assertions.assertThatThrownBy(() -> new DummyCodeProviderFile("//dir/file", null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testOk() {
		new DummyCodeProviderFile("/dir/file", null);
	}
}
