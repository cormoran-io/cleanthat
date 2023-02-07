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
package eu.solven.cleanthat.code_provider.github.decorator;

import eu.solven.cleanthat.codeprovider.decorator.IGitBranch;
import eu.solven.cleanthat.codeprovider.decorator.IGitCommit;
import eu.solven.cleanthat.codeprovider.decorator.IGitReference;
import eu.solven.cleanthat.codeprovider.decorator.IGitRepository;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;

/**
 * Abstract
 * 
 * @author Benoit Lacelle
 *
 */
public class GithubDecoratorHelper {
	protected GithubDecoratorHelper() {
		// hidden
	}

	protected static class DecoratedGHRepository implements IGitRepository {
		final GHRepository repository;

		public DecoratedGHRepository(GHRepository repository) {
			this.repository = repository;
		}

		@Override
		public <T> T getDecorated() {
			return (T) repository;
		}
	}

	protected static class DecoratedGHReference implements IGitReference {
		final GHRef reference;

		public DecoratedGHReference(GHRef reference) {
			this.reference = reference;
		}

		@Override
		public String getFullRefOrSha1() {
			return reference.getRef();
		}

		@Override
		public <T> T getDecorated() {
			return (T) reference;
		}
	}

	protected static class DecoratedGHBranch implements IGitBranch {
		final GHBranch branch;

		public DecoratedGHBranch(GHBranch branch) {
			this.branch = branch;
		}

		@Override
		public <T> T getDecorated() {
			return (T) branch;
		}
	}

	protected static class DecoratedGHCommit implements IGitCommit {
		final GHCommit commit;

		public DecoratedGHCommit(GHCommit commit) {
			this.commit = commit;
		}

		@Override
		public <T> T getDecorated() {
			return (T) commit;
		}
	}

	public static IGitRepository decorate(GHRepository repository) {
		return new DecoratedGHRepository(repository);
	}

	public static IGitReference decorate(GHRef reference) {
		return new DecoratedGHReference(reference);
	}

	public static IGitBranch decorate(GHBranch branch) {
		return new DecoratedGHBranch(branch);
	}

	public static IGitCommit decorate(GHCommit commit) {
		return new DecoratedGHCommit(commit);
	}
}
