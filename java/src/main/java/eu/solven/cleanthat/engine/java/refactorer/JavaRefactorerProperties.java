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
package eu.solven.cleanthat.engine.java.refactorer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import eu.solven.cleanthat.config.pojo.ICleanthatStepParametersProperties;
import java.util.List;
import lombok.Data;

/**
 * The configuration of {@link JavaRefactorer}.
 * 
 * 'excluded' and 'included': we include any rule which is included (by exact match, or if '*' is included), and not
 * excluded (by exact match)
 * 
 * @author Benoit Lacelle
 *
 */
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@SuppressWarnings("PMD.ImmutableField")
@Data
public class JavaRefactorerProperties implements ICleanthatStepParametersProperties {
	public static final String WILDCARD = "*";
	/**
	 * A {@link List} of included rules (by ID). '*' can be used to include all rules
	 */
	private List<String> included = List.of(WILDCARD);

	/**
	 * A {@link List} of excluded rules (by ID)
	 */
	private List<String> excluded = List.of();

	/**
	 * One may activate not-production-ready rules. It may be useful to test a new rule over some external repository
	 */
	@Deprecated
	private boolean productionReadyOnly = true;

	@Override
	public Object getCustomProperty(String key) {
		if ("included".equalsIgnoreCase(key)) {
			return included;
		} else if ("excluded".equalsIgnoreCase(key)) {
			return excluded;
		} else if ("production_ready_only".equalsIgnoreCase(key)) {
			return productionReadyOnly;
		}
		return null;
	}

	public static JavaRefactorerProperties defaults() {
		return new JavaRefactorerProperties();
	}

}
