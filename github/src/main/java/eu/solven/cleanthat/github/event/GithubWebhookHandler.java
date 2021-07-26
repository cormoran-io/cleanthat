package eu.solven.cleanthat.github.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppCreateTokenBuilder;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cormoran.pepper.collection.PepperMapHelper;
import cormoran.pepper.jvm.GCInspector;
import cormoran.pepper.logging.PepperLogHelper;
import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.git_abstraction.GithubFacade;
import eu.solven.cleanthat.github.event.pojo.GitPrHeadRef;
import eu.solven.cleanthat.github.event.pojo.GitRepoBranchSha1;
import eu.solven.cleanthat.github.event.pojo.GithubWebhookEvent;
import eu.solven.cleanthat.github.event.pojo.GithubWebhookRelevancyResult;
import eu.solven.cleanthat.github.event.pojo.WebhookRelevancyResult;
import eu.solven.cleanthat.lambda.step0_checkwebhook.I3rdPartyWebhookEvent;
import eu.solven.cleanthat.lambda.step0_checkwebhook.IWebhookEvent;

/**
 * Default implementation for IGithubWebhookHandler
 *
 * @author Benoit Lacelle
 */
// https://docs.github.com/en/developers/webhooks-and-events/webhook-events-and-payloads
public class GithubWebhookHandler implements IGithubWebhookHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GithubWebhookHandler.class);

	final GHApp githubApp;

	final List<ObjectMapper> objectMappers;

	public GithubWebhookHandler(GHApp githubApp, List<ObjectMapper> objectMappers) {
		this.githubApp = githubApp;
		this.objectMappers = objectMappers;
	}

	@Override
	public GHApp getGithubAsApp() {
		return githubApp;
	}

	@Override
	public GithubAndToken makeInstallationGithub(long installationId) {
		try {
			GHAppInstallation installationById = getGithubAsApp().getInstallationById(installationId);
			LOGGER.info("Permissions: {}", installationById.getPermissions());
			LOGGER.info("RepositorySelection: {}", installationById.getRepositorySelection());
			// https://github.com/hub4j/github-api/issues/570
			GHAppCreateTokenBuilder installationGithubBuilder = installationById.createToken(// Required to open
					Map.of(// new pull-requests
							"pull_requests", // Required to access a repository without having to list all available
							GHPermissionType.WRITE, // repositories
							"metadata", // Required to read files, and commit new versions
							GHPermissionType.READ,
							"contents",
							GHPermissionType.WRITE));
			// https://github.com/hub4j/github-api/issues/570
			String token = installationGithubBuilder.create().getToken();
			GitHub installationGithub = makeInstallationGithub(token);
			// https://stackoverflow.com/questions/45427275/how-to-check-my-github-current-rate-limit
			LOGGER.info("Initialized an installation github. RateLimit status: {}", installationGithub.getRateLimit());
			return new GithubAndToken(installationGithub, token);
		} catch (GHFileNotFoundException e) {
			throw new UncheckedIOException("Invalid installationId, or no actual access to it?", e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected GitHub makeInstallationGithub(String token) throws IOException {
		return new GitHubBuilder().withAppInstallationToken(token).build();
	}

	@SuppressWarnings({ "PMD.ExcessiveMethodLength",
			"checkstyle:MethodLength",
			"PMD.NPathComplexity",
			"PMD.CognitiveComplexity" })
	@Override
	public GithubWebhookRelevancyResult filterWebhookEventRelevant(I3rdPartyWebhookEvent githubEvent) {
		// https://developer.github.com/webhooks/event-payloads/
		Map<String, ?> input = githubEvent.getBody();
		long installationId = PepperMapHelper.getRequiredNumber(input, "installation", "id").longValue();
		Optional<Object> organizationUrl = PepperMapHelper.getOptionalAs(input, "organization", "url");
		LOGGER.info("Received a webhook for installationId={} (organization={})",
				installationId,
				organizationUrl.orElse("-"));
		// We are interested in 2 kind of events:
		// PR being (re)open: it is a good time to clean PR-head modified files (if not in a readonly branch)
		// Commit to branches:
		// Either there is a PR associated to the branch: it is relevant to keep the PR clean
		// Or there is no PR associate to the branch, but the branch is to be maintained clean
		// In this later case, we may clean right away on given branch (a bad-practice)
		// Or open a PR cleaning given branch
		// https://docs.github.com/en/developers/webhooks-and-events/webhook-events-and-payloads#push
		// Push on PR: there is no action. There may be multiple commits being pushed
		// Present for PR, PR_review and PR_review_comment
		Optional<Map<String, ?>> optPullRequest = PepperMapHelper.getOptionalAs(input, "pull_request");
		Optional<String> optAction = PepperMapHelper.getOptionalString(input, "action");
		// We are notified a PR has been open: its branch may be keep_cleaned or not
		boolean prOpen;
		Optional<GitPrHeadRef> optOpenPr;
		// We are notified of a commit: its branch may be explicitly keep_cleaned (e.g. master) or implicitly (e.g. it
		// has a PR)
		boolean pushBranch;
		boolean refHasOpenReviewRequest;
		// baseRef is optional: in case of PR even, it is trivial, but in case of commitPush event, we have to scan for
		// a compatible
		Optional<GitRepoBranchSha1> optBaseRef;
		// If not headRef: this event is not relevant (e.g. it is a comment event)
		Optional<GitRepoBranchSha1> optHeadRef;
		// TODO It is dumb to analyze the event, but we have to do that given we lost the header indicating the type of
		// events through API Gateway and SQS
		if (optPullRequest.isPresent()) {
			pushBranch = false;
			if (optAction.isEmpty()) {
				throw new IllegalStateException("We miss an action for a webhook holding a pull_request");
			} else if ("opened".equals(optAction.get()) || "reopened".equals(optAction.get())) {
				String headRef = PepperMapHelper.getRequiredString(optPullRequest.get(), "head", "ref");
				if (headRef.startsWith(GithubRefCleaner.PREFIX_REF_CLEANTHAT)) {
					// Do not process CleanThat own PR open events
					LOGGER.info("We discard as headRef is: {}", headRef);
					return new GithubWebhookRelevancyResult(false,
							false,
							false,
							Optional.empty(),
							Optional.empty(),
							Optional.empty());
				}
				// Some dirty commits may have been pushed while the PR was closed
				prOpen = true;
				refHasOpenReviewRequest = true;
				String baseRepoName =
						PepperMapHelper.getRequiredString(optPullRequest.get(), "base", "repo", "full_name");
				String baseRef = PepperMapHelper.getRequiredString(optPullRequest.get(), "base", "ref");
				long prId = PepperMapHelper.getRequiredNumber(optPullRequest.get(), "id").longValue();
				optOpenPr = Optional.of(new GitPrHeadRef(baseRepoName, prId));
				String headRepoName =
						PepperMapHelper.getRequiredString(optPullRequest.get(), "head", "repo", "full_name");
				String baseSha = PepperMapHelper.getRequiredString(optPullRequest.get(), "base", "sha");
				optBaseRef = Optional.of(new GitRepoBranchSha1(baseRepoName, baseRef, baseSha));
				String headSha = PepperMapHelper.getRequiredString(optPullRequest.get(), "head", "sha");
				optHeadRef = Optional.of(new GitRepoBranchSha1(headRepoName, headRef, headSha));
			} else {
				prOpen = false;
				refHasOpenReviewRequest = false;
				optOpenPr = Optional.empty();
				optBaseRef = Optional.empty();
				optHeadRef = Optional.empty();
			}
		} else {
			prOpen = false;
			optOpenPr = Optional.empty();
			if (optAction.isPresent()) {
				// Anything but a push
				// i.e. a push event has no action
				pushBranch = false;
				optBaseRef = Optional.empty();
				optHeadRef = Optional.empty();
				refHasOpenReviewRequest = false;
			} else {
				// 'ref' holds the branch name, but it would lead to issues in case on multiple commits: we prefer to
				// point directly to the sha1. Some codeProvider/events may have events leading to a branch reference,
				// but not a specific sha1
				// TODO Keeping this information may be useful to clean a branch by opening a PR, as the PR has to refer
				// to a branch, not a commit.
				// In fact, keeping only a sha1 is not relevant, as we need a ref/branch to record our cleaning anyway.
				// https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
				Optional<String> optSha = PepperMapHelper.getOptionalString(input, "after");
				Optional<String> optFullRefName = PepperMapHelper.getOptionalString(input, "ref");
				if (optSha.isPresent() && optFullRefName.isPresent()) {
					String pusherName = PepperMapHelper.getRequiredString(input, "pusher", "name");
					if (pusherName.toLowerCase(Locale.US).contains("cleanthat")) {
						LOGGER.info("We discard as pusherName is: {}", pusherName);
						return new GithubWebhookRelevancyResult(false,
								false,
								false,
								Optional.empty(),
								Optional.empty(),
								Optional.empty());
					}
					pushBranch = true;
					String ref = optFullRefName.get();
					String repoName = PepperMapHelper.getRequiredAs(input, "repository", "full_name");
					GitRepoBranchSha1 value = new GitRepoBranchSha1(repoName, ref, optSha.get());
					optHeadRef = Optional.of(value);

					// We need a Github installation instance to check for this, while current call has to be offline
					// (i.e. just analyzing the event)
					// try {
					// Optional<GHPullRequest> prMatchingHead =
					// new GithubFacade(github, value.getRepoName()).findFirstPrHeadMatchingRef(ref);
					// optBaseRef = prMatchingHead.map(pr -> {
					// GHCommitPointer base = pr.getBase();
					// return new GitRepoBranchSha1(base.getRepository().getName(), base.getRef(), base.getSha());
					// });
					// refHasOpenReviewRequest = prMatchingHead.isPresent();
					// } catch (IOException e) {
					// throw new UncheckedIOException(e);
					// }
					optBaseRef = Optional.empty();

					// TODO We could set a 'maybe' instead of 'false'
					refHasOpenReviewRequest = false;

				} else {
					// TODO Unclear which case this can be (no pull_request and no action)
					LOGGER.warn("WTF We miss at least one of sha1 and refName");
					pushBranch = false;
					optBaseRef = Optional.empty();
					optHeadRef = Optional.empty();
					refHasOpenReviewRequest = false;
				}
			}
		}
		// We log the payload temporarily, in order to have easy access to metadata
		if (!GCInspector.inUnitTest()) {
			try {
				LOGGER.info("TMP payload: {}", ConfigHelpers.getJson(objectMappers).writeValueAsString(input));
			} catch (JsonProcessingException e) {
				LOGGER.warn("Issue while printing the json of the webhook", e);
			}
		}
		return new GithubWebhookRelevancyResult(prOpen,
				pushBranch,
				refHasOpenReviewRequest,
				optHeadRef,
				optOpenPr,
				optBaseRef);
	}

	// TODO What if we target a branch which has no configuration, as cleanthat has been introduced in the meantime in
	// the base branch?
	@SuppressWarnings({ "PMD.NPathComplexity", "PMD.CognitiveComplexity" })
	@Override
	public WebhookRelevancyResult filterWebhookEventTargetRelevantBranch(ICodeCleanerFactory cleanerFactory,
			IWebhookEvent githubAcceptedEvent) {
		GithubWebhookEvent githubEvent = GithubWebhookEvent.fromCleanThatEvent(githubAcceptedEvent);
		GithubWebhookRelevancyResult offlineResult = filterWebhookEventRelevant(githubEvent);
		if (!offlineResult.isPrOpen() && !offlineResult.isPushBranch()) {
			throw new IllegalArgumentException("We should have rejected this earlier");
		}
		// https://developer.github.com/webhooks/event-payloads/
		Map<String, ?> input = githubEvent.getBody();
		long installationId = PepperMapHelper.getRequiredNumber(input, "installation", "id").longValue();
		GithubAndToken githubAuthAsInst = makeInstallationGithub(installationId);
		GitHub githubAsInst = githubAuthAsInst.getGithub();
		{
			GHRateLimit rateLimit;
			try {
				rateLimit = githubAsInst.getRateLimit();
			} catch (IOException e) {
				throw new UncheckedIOException("Issue checking rateLimit", e);
			}
			int rateLimitRemaining = rateLimit.getRemaining();
			if (rateLimitRemaining == 0) {
				Object resetIn = PepperLogHelper.humanDuration(
						rateLimit.getResetEpochSeconds() * TimeUnit.SECONDS.toMillis(1) - System.currentTimeMillis());
				return WebhookRelevancyResult.dismissed("Installation has hit its own RateLimit. Reset in: " + resetIn);
			}
		}
		// We suppose this is always the same as the base repository id
		long baseRepoId = PepperMapHelper.getRequiredNumber(input, "repository", "id").longValue();
		GHRepository baseRepo;
		try {
			baseRepo = githubAsInst.getRepositoryById(baseRepoId);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		Optional<GitPrHeadRef> optOpenPr = offlineResult.optOpenPr();
		if (offlineResult.isPushBranch() && !offlineResult.refHasOpenReviewRequest()) {
			assert optOpenPr.isEmpty();

			LOGGER.info("Search for a PR merging the commited branch");
			try {
				String repoName = offlineResult.optPushedRef().get().getRepoName();
				Optional<GHPullRequest> prMatchingHead = new GithubFacade(githubAsInst, repoName)
						.findFirstPrHeadMatchingRef(offlineResult.optPushedRef().get().getRef());
				// Optional<GitRepoBranchSha1> optBaseRef = prMatchingHead.map(pr -> {
				// GHCommitPointer base = pr.getBase();
				// return new GitRepoBranchSha1(base.getRepository().getName(), base.getRef(), base.getSha());
				// });

				if (prMatchingHead.isPresent()) {
					optOpenPr = prMatchingHead.map(b -> new GitPrHeadRef(repoName, prMatchingHead.get().getId()));
				}

				// refHasOpenReviewRequest = prMatchingHead.isPresent();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		// String defaultBranch = GitHelper.getDefaultBranch(Optional.ofNullable(repo.getDefaultBranch()));
		// final boolean isMainBranchCommit;
		GitRepoBranchSha1 theRef;
		if (optOpenPr.isPresent()) {
			Optional<GHPullRequest> optPr;
			try {
				String rawPrId = String.valueOf(optOpenPr.get().getId());
				int prIdAsInteger = Integer.parseInt(rawPrId);
				optPr = Optional.of(baseRepo.getPullRequest(prIdAsInteger));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			GHCommitPointer prHead = optPr.get().getHead();
			GHRepository prHeadRepository = prHead.getRepository();
			String headRepoFullname = prHeadRepository.getFullName();
			if (baseRepoId != prHeadRepository.getId()) {
				return WebhookRelevancyResult.dismissed(
						"PR in a fork are not managed (as we are not presumably allowed to write in the fork). head="
								+ headRepoFullname);
			}
			theRef = new GitRepoBranchSha1(headRepoFullname, prHead.getRef(), prHead.getSha());
		} else {
			// optPr = Optional.empty();
			// No PR: we are guaranteed to have a ref
			theRef = offlineResult.optPushedRef().get();
		}
		Optional<String> optSha1 = Optional.of(theRef.getSha());
		if (optSha1.isEmpty()) {
			throw new IllegalStateException("Should not happen");
		}
		if (optSha1.isPresent()) {
			// TODO Go into this only if we have 'checks:write' permission
			GHCheckRunBuilder checkRunBuilder = baseRepo.createCheckRun("CleanThat", optSha1.get());
			try {
				GHCheckRun checkRun = checkRunBuilder.create();
				checkRun.update().withStatus(Status.COMPLETED);
			} catch (IOException e) {
				// TODO Should we check we have the proper permission anyway?
				LOGGER.warn("Issue creating the CheckRun", e);
			}
		}
		// //
		// https://developer.github.com/apps/building-github-apps/authenticating-with-github-apps/#http-based-git-access-by-an-installation
		// // git clone https://x-access-token:<token>@github.com/owner/repo.git
		//
		IGithubRefCleaner cleaner = cleanerFactory.makeCleaner(githubAuthAsInst);
		// BEWARE this branch may not exist: either it is a cleanthat branch yet to create. Or it may be deleted in the
		// meantime (e.g. merged+deleted before cleanthat doing its work)
		Optional<String> refToClean = cleaner.prepareRefToClean(offlineResult, theRef);
		// offlineResult.
		if (refToClean.isEmpty()) {
			return WebhookRelevancyResult.dismissed(
					"After looking deeper, this event seems not relevant (e.g. no configuration, or forked|readonly head)");
		}
		return WebhookRelevancyResult.relevant(refToClean.get(), offlineResult.optBaseRef());
	}

	@SuppressWarnings("PMD.CognitiveComplexity")
	@Override
	public void doExecuteWebhookEvent(ICodeCleanerFactory cleanerFactory, IWebhookEvent githubAndBranchAcceptedEvent) {
		I3rdPartyWebhookEvent externalCodeEvent = GithubWebhookEvent.fromCleanThatEvent(githubAndBranchAcceptedEvent);
		GithubWebhookRelevancyResult offlineResult = filterWebhookEventRelevant(externalCodeEvent);
		if (!offlineResult.isPrOpen() && !offlineResult.isPushBranch()) {
			throw new IllegalArgumentException("We should have rejected this earlier");
		}
		WebhookRelevancyResult relevancyResult =
				filterWebhookEventTargetRelevantBranch(cleanerFactory, githubAndBranchAcceptedEvent);

		if (relevancyResult.getOptBranchToClean().isEmpty()) {
			// TODO May happen if the PR is closed in the meantime
			throw new IllegalArgumentException("We should have rejected this earlier");
		}
		// https://developer.github.com/webhooks/event-payloads/
		Map<String, ?> input = externalCodeEvent.getBody();
		long baseRepoId = PepperMapHelper.getRequiredNumber(input, "repository", "id").longValue();
		long installationId = PepperMapHelper.getRequiredNumber(input, "installation", "id").longValue();
		GithubAndToken githubAuthAsInst = makeInstallationGithub(installationId);
		GitHub github = githubAuthAsInst.getGithub();
		GHRepository repo;
		try {
			repo = github.getRepositoryById(baseRepoId);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		IGithubRefCleaner cleaner = cleanerFactory.makeCleaner(githubAuthAsInst);
		if (relevancyResult.getOptBaseToConsider().isPresent()) {
			cleaner.formatRefDiff(repo, () -> {
				try {
					return repo.getRef(relevancyResult.getOptBaseToConsider().get().getRef());
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, () -> {
				try {
					return repo.getRef(relevancyResult.getOptBranchToClean().get());
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} else {
			cleaner.formatRef(repo, () -> {
				try {
					return repo.getRef(relevancyResult.getOptBranchToClean().get());
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}
}
