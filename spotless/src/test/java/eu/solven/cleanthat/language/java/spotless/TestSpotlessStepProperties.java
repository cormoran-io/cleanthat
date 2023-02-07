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
package eu.solven.cleanthat.language.java.spotless;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepParametersProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepProperties;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestSpotlessStepProperties {
	@Test
	public void testJackson() throws JsonProcessingException {
		ObjectMapper om = new ObjectMapper();

		SpotlessStepProperties step = SpotlessStepProperties.builder().id("someStepName").build();
		SpotlessStepParametersProperties parameters = new SpotlessStepParametersProperties();
		parameters.putProperty("someCustomeKey", "someCustomValue");
		step.setParameters(parameters);

		String asString = om.writeValueAsString(step);
		Assertions.assertThat(asString).containsSubsequence("id", "someStepName", "someCustomeKey", "someCustomValue");

		SpotlessStepProperties backAsObject = om.readValue(asString, SpotlessStepProperties.class);

		Assertions.assertThat(backAsObject).isEqualTo(step);
	}

	@Test
	public void testJackson_lackId() throws JsonProcessingException {
		ObjectMapper om = new ObjectMapper();

		SpotlessStepProperties step = SpotlessStepProperties.builder().id("someStepName").build();
		SpotlessStepParametersProperties parameters = new SpotlessStepParametersProperties();
		parameters.putProperty("someCustomeKey", "someCustomValue");
		step.setParameters(parameters);

		Map<?, ?> asMap = om.convertValue(step, Map.class);
		Assertions.assertThat(asMap.remove("id")).isNotNull();

		Assertions.assertThatThrownBy(() -> om.convertValue(asMap, SpotlessStepProperties.class))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
