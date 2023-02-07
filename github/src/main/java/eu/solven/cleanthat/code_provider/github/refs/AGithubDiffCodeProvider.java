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
package eu.solven.cleanthat.code_provider.github.refs;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import eu.solven.cleanthat.code_provider.github.code_provider.AGithubCodeProvider;
import eu.solven.cleanthat.code_provider.github.code_provider.FileIsTooBigException;
import eu.solven.cleanthat.codeprovider.DummyCodeProviderFile;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderFile;
import eu.solven.cleanthat.codeprovider.IListOnlyModifiedFiles;
import eu.solven.pepper.logging.PepperLogHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ICodeProvider} for Github pull-requests
 *
 * @author Benoit Lacelle
 */
public abstract class AGithubDiffCodeProvider extends AGithubCodeProvider implements IListOnlyModifiedFiles {
	private static final Logger LOGGER = LoggerFactory.getLogger(AGithubDiffCodeProvider.class);

	private static final int LIMIT_COMMIT_IN_COMPARE = 250;

	final String token;
	final GHRepository baseRepository;

	final Supplier<GHCompare> diffSupplier;

	public AGithubDiffCodeProvider(FileSystem fs, String token, GHRepository baseRepository) {
		super(fs);
		this.token = token;

		this.baseRepository = baseRepository;

		// https://stackoverflow.com/questions/26925312/github-api-how-to-compare-2-commits
		this.diffSupplier = Suppliers.memoize(() -> {
			try {
				return baseRepository.getCompare(getBaseId(), getHeadId());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	protected abstract String getBaseId();

	protected abstract String getHeadId();

	@Override
	public void listFilesForContent(Set<String> patterns, Consumer<ICodeProviderFile> consumer) throws IOException {
		GHCompare diff = diffSupplier.get();

		if (diff.getTotalCommits() >= LIMIT_COMMIT_IN_COMPARE) {
			// https://stackoverflow.com/questions/26925312/github-api-how-to-compare-2-commits
			// https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
			LOGGER.warn("We are considering a diff of more than 250 Commits ({})", diff.getTotalCommits());
		}

		Stream.of(diff.getFiles()).forEach(prFile -> {
			// Github does not prefix with '/'
			// TODO What is the rational of requiring leading '/'?
			String fileName = "/" + prFile.getFileName();
			if ("removed".equals(prFile.getStatus())) {
				LOGGER.debug("Skip a removed file: {}", fileName);
			} else {
				consumer.accept(new DummyCodeProviderFile(fileName, prFile));
			}
		});
	}

	@Override
	public String toString() {
		return diffSupplier.get().getHtmlUrl().toExternalForm();
	}

	@Override
	public Optional<String> loadContentForPath(String path) throws IOException {
		try {
			return Optional.of(loadContent(baseRepository, path, getHeadId()));
		} catch (GHFileNotFoundException e) {
			LOGGER.trace("We miss: {}", path, e);
			LOGGER.debug("We miss: {}", path);
			return Optional.empty();
		} catch (FileIsTooBigException e) {
			LOGGER.trace("File is too big to be processed: {} ({})",
					path,
					PepperLogHelper.humanBytes(e.getLength()),
					e);
			LOGGER.warn("File is too big to be processed: {} ({})", path, PepperLogHelper.humanBytes(e.getLength()));
			return Optional.empty();
		}
	}

	@Override
	public String getRepoUri() {
		return baseRepository.getGitTransportUrl();
	}

}
