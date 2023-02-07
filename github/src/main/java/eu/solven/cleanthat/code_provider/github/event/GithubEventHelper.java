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
package eu.solven.cleanthat.code_provider.github.event;

import eu.solven.cleanthat.code_provider.github.decorator.GithubDecoratorHelper;
import eu.solven.cleanthat.code_provider.github.event.pojo.WebhookRelevancyResult;
import eu.solven.cleanthat.codeprovider.decorator.ILazyGitReference;
import eu.solven.cleanthat.codeprovider.git.GitRepoBranchSha1;
import eu.solven.cleanthat.codeprovider.git.IGitRefCleaner;
import eu.solven.cleanthat.formatter.CodeFormatResult;
import eu.solven.cleanthat.git_abstraction.GithubRepositoryFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.kohsuke.github.GHCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps executing logic on GithubEvents
 * 
 * @author Benoit Lacelle
 *
 */
public class GithubEventHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(GithubEventHelper.class);

	protected GithubEventHelper() {
		// hidden
	}

	public static CodeFormatResult executeCleaning(Path root,
			WebhookRelevancyResult relevancyResult,
			IGitRefCleaner cleaner,
			GithubRepositoryFacade facade,
			ILazyGitReference headSupplier) {
		CodeFormatResult result;
		if (relevancyResult.optBaseForHead().isPresent()) {
			GitRepoBranchSha1 baseAsObject = relevancyResult.optBaseForHead().get();

			// We use as base a commit, and not a ref
			// Else, a compare within a single ref would lead to no diff (as the ref would take by default the most
			// recent commit, not the commit before the push)
			String sha1 = baseAsObject.getSha();

			GHCommit base = facade.getCommit(sha1);

			result = cleaner.formatCommitToRefDiff(root,
					facade.getRepository(),
					GithubDecoratorHelper.decorate(base),
					headSupplier);
		} else {
			throw new IllegalArgumentException("Unclear expected behavior");
			// result = cleaner.formatRef(GithubDecoratorHelper.decorate(repo), headSupplier.getSupplier().get());
		}
		return result;
	}

	public static void optCreateBranchOpenPr(WebhookRelevancyResult relevancyResult,
			GithubRepositoryFacade facade,
			AtomicReference<GitRepoBranchSha1> refLazyRefCreated,
			CodeFormatResult result) {
		if (refLazyRefCreated.get() != null) {
			GitRepoBranchSha1 lazyRefCreated = refLazyRefCreated.get();
			if (relevancyResult.optBaseForHead().isEmpty()) {
				LOGGER.warn("We created a tmpRef but there is no base");
			} else {
				if (result.isEmpty()) {
					LOGGER.info("Clean is done but no files impacted: the temporary ref ({}) is about to be removed",
							lazyRefCreated);
					try {
						// TODO This ref should never have been created
						// TODO We should remove a ref only if CleanThat is the only writer, else it means a human has
						// made some work on it
						facade.removeRef(lazyRefCreated);
					} catch (IOException e) {
						LOGGER.warn("Issue removing a temporary ref (" + lazyRefCreated + ")", e);
					}
				} else {
					LOGGER.info("Clean is done but and some files are impacted: We open a PR if none already exists");
					doOpenPr(relevancyResult, facade, lazyRefCreated);
				}
			}
		} else {
			LOGGER.debug("The changes would have been committed directly in the head branch");
		}
	}

	public static void doOpenPr(WebhookRelevancyResult relevancyResult,
			GithubRepositoryFacade facade,
			GitRepoBranchSha1 lazyRefCreated) {
		// TODO We may want to open a PR in a different repository, in case the original repository does not
		// accept new branches
		Optional<?> optOpenPr;
		GitRepoBranchSha1 base = relevancyResult.optBaseForHead().get();
		try {
			// TODO Add details about the event triggering this
			String body = "Cleanthat\r\n@blacelle please look at me";
			optOpenPr = facade.openPrIfNoneExists(base, lazyRefCreated, "Cleanthat", body);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (optOpenPr.isPresent()) {
			LOGGER.info("We succeeded opening a RR open to merge {} into {}: {}",
					lazyRefCreated,
					base,
					optOpenPr.get());
		} else {
			LOGGER.info("There is already a RR open to merge {} into {}", lazyRefCreated, base);
		}
	}
}
