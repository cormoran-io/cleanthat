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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.solven.cleanthat.code_provider.github.event.pojo.WebhookRelevancyResult;
import eu.solven.cleanthat.codeprovider.git.GitWebhookRelevancyResult;
import eu.solven.cleanthat.lambda.step0_checkwebhook.I3rdPartyWebhookEvent;
import eu.solven.cleanthat.lambda.step0_checkwebhook.IWebhookEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.core.env.Environment;

/**
 * Factory for {@link GitHub}, on a per-installation basis
 *
 * @author Benoit Lacelle
 */
public class GithubWebhookHandlerFactory implements IGitWebhookHandlerFactory {
	// private static final Logger LOGGER = LoggerFactory.getLogger(GithubWebhookHandlerFactory.class);

	public static final String ENV_GITHUB_APP_PRIVATE_JWK = "github.app.private-jwk";

	// public static final String GITHUB_APP_PRIVATE_JWK_FORUNITTESTS = "forUnitTests";

	// https://github.com/organizations/solven-eu/settings/apps/cleanthat
	// https://github.com/apps/cleanthat
	public static final String GITHUB_DEFAULT_APP_ID = "65550";

	// https://github.community/t/expiration-time-claim-exp-is-too-far-in-the-future-when-creating-an-access-token/13830
	private static final int GITHUB_TIMEOUT_SAFETY_SECONDS = 15;

	private static final int GITHUB_TIMEOUT_JWK_MINUTES = 10;

	final Environment env;

	final List<ObjectMapper> objectMappers;

	final GithubCheckRunManager githubCheckRunManager;

	public GithubWebhookHandlerFactory(Environment env,
			List<ObjectMapper> objectMappers,
			GithubCheckRunManager githubCheckRunManager) {
		this.env = env;
		this.objectMappers = objectMappers;

		this.githubCheckRunManager = githubCheckRunManager;
	}

	@Override
	public IGitWebhookHandler makeNoAuth() throws IOException {
		GithubNoApiWebhookHandler underlying = makeUnderlyingNoAuth();
		return new IGitWebhookHandler() {

			@Override
			public GitWebhookRelevancyResult filterWebhookEventRelevant(I3rdPartyWebhookEvent input) {
				return underlying.filterWebhookEventRelevant(input);
			}

			@Override
			public WebhookRelevancyResult filterWebhookEventTargetRelevantBranch(ICodeCleanerFactory codeCleanerFactory,
					IWebhookEvent input) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void doExecuteClean(ICodeCleanerFactory codeCleanerFactory, IWebhookEvent input) {
				throw new UnsupportedOperationException();
			}
		};
	}

	public GithubNoApiWebhookHandler makeUnderlyingNoAuth() {
		GithubNoApiWebhookHandler underlying = new GithubNoApiWebhookHandler(objectMappers);
		return underlying;
	}

	@Override
	public IGitWebhookHandler makeWithFreshAuth() throws IOException {
		GithubWebhookHandler githubWebhookHandler = makeGithubWebhookHandler();

		IGitWebhookHandler noAuth = makeNoAuth();

		return new IGitWebhookHandler() {
			@Override
			public GitWebhookRelevancyResult filterWebhookEventRelevant(I3rdPartyWebhookEvent input) {
				return noAuth.filterWebhookEventRelevant(input);
			}

			@Override
			public WebhookRelevancyResult filterWebhookEventTargetRelevantBranch(ICodeCleanerFactory codeCleanerFactory,
					IWebhookEvent input) {
				try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build()) {
					return githubWebhookHandler
							.filterWebhookEventTargetRelevantBranch(fs.getPath("/"), codeCleanerFactory, input);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			@Override
			public void doExecuteClean(ICodeCleanerFactory codeCleanerFactory, IWebhookEvent input) {
				// We make a FileSystem per ICodeProvider
				try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build()) {
					githubWebhookHandler.doExecuteClean(fs.getPath("/"), codeCleanerFactory, input);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}

	public GithubWebhookHandler makeGithubWebhookHandler() throws IOException {
		String jwt;
		try {
			jwt = makeJWT();
		} catch (JOSEException e) {
			throw new IllegalStateException("Issue with configuration?", e);
		}
		GitHub github = new GitHubBuilder().withJwtToken(jwt)
				// This leads to 401. Why?
				// .withRateLimitChecker(new NoWaitRateLimitChecker())
				.withConnector(GithubWebhookHandler.createGithubConnector())
				.build();

		GithubWebhookHandler githubWebhookHandler =
				new GithubWebhookHandler(github.getApp(), objectMappers, githubCheckRunManager);
		return githubWebhookHandler;
	}

	// https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature
	public String makeJWT() throws JOSEException {
		String rawJwk = env.getRequiredProperty(ENV_GITHUB_APP_PRIVATE_JWK);

		// if (rawJwk.equals(GithubWebhookHandlerFactory.GITHUB_APP_PRIVATE_JWK_FORUNITTESTS)
		// && GCInspector.inUnitTest()) {
		// LOGGER.info("We are in a unit-test");
		// return GithubWebhookHandlerFactory.GITHUB_APP_PRIVATE_JWK_FORUNITTESTS;
		// }

		RSAKey rsaJWK;
		try {
			rsaJWK = RSAKey.parse(rawJwk);
		} catch (IllegalStateException | ParseException e) {
			throw new IllegalStateException("Issue parsing privateKey", e);
		}
		// Create RSA-signer with the private key
		JWSSigner signer = new RSASSASigner(rsaJWK);
		// Prepare JWT with claims set
		Date now = new Date();

		// https://developer.github.com/apps/building-github-apps/authenticating-with-github-apps/#authenticating-as-a-github-app
		Date expiresAt = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(GITHUB_TIMEOUT_JWK_MINUTES)
				- TimeUnit.SECONDS.toMillis(GITHUB_TIMEOUT_SAFETY_SECONDS));
		String githubAppId = env.getProperty("github.app.app-id", GITHUB_DEFAULT_APP_ID);
		JWTClaimsSet claimsSet =
				new JWTClaimsSet.Builder().issuer(githubAppId).issueTime(now).expirationTime(expiresAt).build();
		SignedJWT signedJWT =
				new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(), claimsSet);
		// Compute the RSA signature
		signedJWT.sign(signer);
		return signedJWT.serialize();
	}
}
