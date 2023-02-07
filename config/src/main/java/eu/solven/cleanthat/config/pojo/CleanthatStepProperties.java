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
package eu.solven.cleanthat.config.pojo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Each {@link EngineProperties} is composed of a {@link List} of {@link CleanthatStepProperties}
 * 
 * @author Benoit Lacelle
 *
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressWarnings("PMD.ImmutableField")
@Data
@Builder
// https://stackoverflow.com/questions/51464720/lombok-1-18-0-and-jackson-2-9-6-not-working-together
@Jacksonized
public class CleanthatStepProperties {

	// the step name/id
	private final String id;

	@Builder.Default
	private boolean skip = false;

	// the step parameters
	@JsonDeserialize(as = CleanthatStepParametersProperties.class)
	private final ICleanthatStepParametersProperties parameters;

}
