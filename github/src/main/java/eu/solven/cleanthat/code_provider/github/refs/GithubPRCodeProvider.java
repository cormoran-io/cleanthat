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

import eu.solven.cleanthat.code_provider.github.code_provider.AGithubCodeProvider;
import eu.solven.cleanthat.codeprovider.DummyCodeProviderFile;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderFile;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.codeprovider.IListOnlyModifiedFiles;
import eu.solven.cleanthat.git_abstraction.GithubRepositoryFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ICodeProvider} for Github pull-requests
 *
 * @author Benoit Lacelle
 */
public class GithubPRCodeProvider extends AGithubCodeProvider implements IListOnlyModifiedFiles, ICodeProviderWriter {
	private static final Logger LOGGER = LoggerFactory.getLogger(GithubPRCodeProvider.class);

	final String token;
	final GHPullRequest pr;

	public GithubPRCodeProvider(FileSystem fs, String token, GHPullRequest pr) {
		super(fs);
		this.token = token;
		this.pr = pr;
	}

	@Override
	public void listFilesForContent(Set<String> includePatterns, Consumer<ICodeProviderFile> consumer)
			throws IOException {
		pr.listFiles().forEach(prFile -> {
			if ("deleted".equals(prFile.getStatus())) {
				LOGGER.debug("Skip a deleted file: {}", prFile.getFilename());
			} else {
				consumer.accept(new DummyCodeProviderFile(prFile.getFilename(), prFile));
			}
		});
	}

	public static String loadContent(GHPullRequest pr, String filename) throws IOException {
		GHRepository repository = pr.getRepository();
		String sha1 = pr.getHead().getSha();
		return loadContent(repository, filename, sha1);
	}

	@Override
	public String toString() {
		return pr.getHtmlUrl().toExternalForm();
	}

	@Override
	public void persistChanges(Map<String, String> pathToMutatedContent,
			List<String> prComments,
			Collection<String> prLabels) {
		GHRepository repo = pr.getRepository();
		String fullRefName = "refs/heads/" + pr.getHead().getRef();
		GHRef ref;
		try {
			ref = new GithubRepositoryFacade(repo).getRef(fullRefName);
		} catch (IOException e) {
			throw new UncheckedIOException("Issue fetching refName=" + fullRefName, e);
		}
		new GithubRefWriterLogic(repo, ref).persistChanges(pathToMutatedContent, prComments, prLabels);
	}

	// @Override
	// public String deprecatedLoadContent(Object file) throws IOException {
	// return loadContent(pr, ((GHPullRequestFileDetail) file).getFilename());
	// }
	//
	// @Override
	// public String deprecatedGetFilePath(Object file) {
	// return ((GHPullRequestFileDetail) file).getFilename();
	// }

	@Override
	public Optional<String> loadContentForPath(String path) throws IOException {
		try {
			return Optional.of(loadContent(pr.getRepository(), path, pr.getHead().getSha()));
		} catch (GHFileNotFoundException e) {
			LOGGER.trace("We miss: {}", path, e);
			LOGGER.debug("We miss: {}", path);
			return Optional.empty();
		}
	}

	@Override
	public String getRepoUri() {
		return pr.getRepository().getGitTransportUrl();
	}

	@Override
	public void cleanTmpFiles() {
		LOGGER.info("Nothing to delete for {}", this);
	}
}
