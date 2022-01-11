package eu.solven.cleanthat.code_provider.github.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.cleanthat.code_provider.github.decorator.GithubDecoratorHelper;
import eu.solven.cleanthat.code_provider.github.event.pojo.WebhookRelevancyResult;
import eu.solven.cleanthat.codeprovider.decorator.IGitReference;
import eu.solven.cleanthat.codeprovider.git.GitRepoBranchSha1;
import eu.solven.cleanthat.codeprovider.git.IGitRefCleaner;
import eu.solven.cleanthat.formatter.CodeFormatResult;
import eu.solven.cleanthat.git_abstraction.GithubRepositoryFacade;

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

	public static CodeFormatResult executeCleaning(WebhookRelevancyResult relevancyResult,
			GHRepository repo,
			IGitRefCleaner cleaner,
			GithubRepositoryFacade facade,
			Supplier<IGitReference> headSupplier) {
		CodeFormatResult result;
		if (relevancyResult.optBaseForHead().isPresent()) {
			// If the base does not exist, then something is wrong: let's check right away it is available
			GHRef base;
			try {
				base = facade.getRef(relevancyResult.optBaseForHead().get().getRef());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			result = cleaner.formatRefDiff(GithubDecoratorHelper.decorate(repo),
					GithubDecoratorHelper.decorate(base),
					headSupplier);
		} else {
			result = cleaner.formatRef(GithubDecoratorHelper.decorate(repo), headSupplier);
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
			optOpenPr = facade.openPrIfNoneExists(base,
					lazyRefCreated,
					"Cleanthat",
					"Cleanthat <body>\r\n@blacelle please look at me");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (optOpenPr.isPresent()) {
			LOGGER.info("We succeeded opening a PR open to merge {} into {}: {}",
					lazyRefCreated,
					base,
					optOpenPr.get());
		} else {
			LOGGER.info("There is already a PR open to merge {} into {}", lazyRefCreated, base);
		}
	}
}