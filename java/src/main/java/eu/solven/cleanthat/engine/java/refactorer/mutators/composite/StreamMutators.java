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
package eu.solven.cleanthat.engine.java.refactorer.mutators.composite;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.plexus.languages.java.version.JavaVersion;

import com.google.common.base.Suppliers;

import eu.solven.cleanthat.config.pojo.ICleanthatStepParametersProperties;
import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.meta.IConstructorNeedsJdkVersion;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.scanner.MutatorsScanner;

/**
 * This mutator will apply all {@link IMutator} improving {@link Stream} usage.
 * 
 * @author Benoit Lacelle
 *
 */
public class StreamMutators extends CompositeMutator<IMutator> implements IConstructorNeedsJdkVersion {

	static final Supplier<List<IMutator>> STREAM = Suppliers.memoize(() -> MutatorsScanner
			.instantiate(JavaVersion.parse(IJdkVersionConstants.LAST),
					AllIncludingDraftSingleMutators.ALL_INCLUDINGDRAFT.get())
			.stream()
			.filter(m -> m.getTags().contains(ICleanthatStepParametersProperties.GUAVA))
			.collect(Collectors.toList()));

	public StreamMutators(JavaVersion sourceJdkVersion) {
		super(filterWithJdk(sourceJdkVersion, STREAM.get()));
	}

	@Override
	public String getCleanthatId() {
		return ICleanthatStepParametersProperties.STREAM;
	}
}
