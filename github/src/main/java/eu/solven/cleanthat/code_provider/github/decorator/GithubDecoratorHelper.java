package eu.solven.cleanthat.code_provider.github.decorator;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;

import eu.solven.cleanthat.codeprovider.decorator.IGitBranch;
import eu.solven.cleanthat.codeprovider.decorator.IGitReference;
import eu.solven.cleanthat.codeprovider.decorator.IGitRepository;

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

	public static IGitRepository decorate(GHRepository repository) {
		return new DecoratedGHRepository(repository);
	}

	public static IGitReference decorate(GHRef reference) {
		return new DecoratedGHReference(reference);
	}

	public static IGitBranch decorate(GHBranch branch) {
		return new DecoratedGHBranch(branch);
	}
}