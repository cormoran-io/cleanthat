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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.solven.cleanthat.any_language.ACodeCleaner;
import eu.solven.cleanthat.engine.IEngineLintFixerFactory;
import eu.solven.cleanthat.formatter.ICodeProviderFormatter;
import java.util.Collection;

/**
 * A default {@link ACodeCleaner} for maven
 * 
 * @author Benoit Lacelle
 *
 */
public class MavenCodeCleaner extends ACodeCleaner {

	public MavenCodeCleaner(Collection<ObjectMapper> objectMappers,
			Collection<IEngineLintFixerFactory> factories,
			ICodeProviderFormatter formatterProvider) {
		super(objectMappers, factories, formatterProvider);
	}

}
