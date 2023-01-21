/*
 * Copyright 2023 Solven
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
package eu.solven.cleanthat.lambda;

import eu.solven.cleanthat.engine.java.JavaFormattersFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring configuration wrapping all available languages
 * 
 * @author Benoit Lacelle
 *
 */
@Configuration
@Import({ JavaFormattersFactory.class })
public class AllLanguagesSpringConfig {

	// TODO Rely on AutoConfiguration
	// Scala is typically excluded from packaes (e.g. Lambda due to size limitations
	// @ConditionalOnClass(ScalaFormattersFactory.class)
	// @Bean
	// public ScalaFormattersFactory ScalaFormattersFactory(ObjectMapper objectMapper) {
	// return new ScalaFormattersFactory(objectMapper);
	// }
}
