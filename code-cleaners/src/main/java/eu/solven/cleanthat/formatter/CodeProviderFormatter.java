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
package eu.solven.cleanthat.formatter;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import eu.solven.cleanthat.codeprovider.CodeProviderHelpers;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.codeprovider.IListOnlyModifiedFiles;
import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.IncludeExcludeHelpers;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.engine.EngineAndLinters;
import eu.solven.cleanthat.engine.ICodeFormatterApplier;
import eu.solven.cleanthat.engine.IEngineFormatterFactory;
import eu.solven.cleanthat.engine.IEngineLintFixerFactory;
import eu.solven.cleanthat.language.IEngineProperties;
import eu.solven.cleanthat.language.ISourceCodeProperties;
import eu.solven.pepper.thread.PepperExecutorsHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Unclear what is the point of this class
 *
 * @author Benoit Lacelle
 */
public class CodeProviderFormatter implements ICodeProviderFormatter {
	private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CodeProviderFormatter.class);

	public static final String EOL = "\r\n";
	private static final int MAX_LOG_MANY_FILES = 128;

	final IEngineFormatterFactory formatterFactory;
	final ICodeFormatterApplier formatterApplier;

	final SourceCodeFormatterHelper sourceCodeFormatterHelper;

	final ConfigHelpers configHelpers;

	public CodeProviderFormatter(ConfigHelpers configHelpers,
			IEngineFormatterFactory formatterFactory,
			ICodeFormatterApplier formatterApplier) {
		this.configHelpers = configHelpers;
		this.formatterFactory = formatterFactory;
		this.formatterApplier = formatterApplier;

		this.sourceCodeFormatterHelper = new SourceCodeFormatterHelper();
	}

	@SuppressWarnings("PMD.CognitiveComplexity")
	@Override
	public CodeFormatResult formatCode(CleanthatRepositoryProperties repoProperties,
			ICodeProviderWriter codeWriter,
			boolean dryRun) {
		// A config change may be spotless.yaml, or a processor configuration file

		// TODO or an indirect change leading to a full re-compute (e.g. a implicit
		// version upgrade led to a change of some engine, which should trigger a full re-compute)
		AtomicBoolean configIsChanged = new AtomicBoolean();

		List<String> prComments = new ArrayList<>();

		if (codeWriter instanceof IListOnlyModifiedFiles) {
			// TODO Check if number of files is compatible with RateLimit
			try {
				codeWriter.listFilesForFilenames(fileChanged -> {
					if (CodeProviderHelpers.PATHES_CLEANTHAT.contains(fileChanged.getPath())) {
						configIsChanged.set(true);
						prComments.add("Spotless configuration has changed");
					}
				});
			} catch (IOException e) {
				throw new UncheckedIOException("Issue while checking for config change", e);
			}
		} else {
			// We are in a branch (but no base-branch as reference): meaningless to check for config change, and anyway
			LOGGER.debug("We will clean everything");
		}

		AtomicLongMap<String> languageToNbAddedFiles = AtomicLongMap.create();
		AtomicLongMap<String> languagesCounters = AtomicLongMap.create();
		Map<String, String> pathToMutatedContent = new LinkedHashMap<>();

		// We make a FileSystem per ICodeProvider
		// try (FileSystem fileSystem = MemoryFileSystemBuilder.newEmpty().build()) {
		CleanthatSession cleanthatSession =
				new CleanthatSession(codeWriter.getFileSystem(), codeWriter, repoProperties);

		repoProperties.getEngines().stream().filter(lp -> !lp.isSkip()).forEach(dirtyLanguageConfig -> {
			IEngineProperties languageP = prepareLanguageConfiguration(repoProperties, dirtyLanguageConfig);

			// TODO Process all languages in a single pass
			// Beware about concurrency as multiple processors/languages may impact the same file
			AtomicLongMap<String> languageCounters =
					processFiles(cleanthatSession, languageToNbAddedFiles, pathToMutatedContent, languageP);

			String details = languageCounters.asMap()
					.entrySet()
					.stream()
					.map(e -> e.getKey() + ": " + e.getValue())
					.collect(Collectors.joining(EOL));

			prComments.add("engine=" + languageP.getEngine() + EOL + details);
			languageCounters.asMap().forEach((l, c) -> {
				languagesCounters.addAndGet(l, c);
			});
		});
		// } catch (IOException e) {
		// throw new UncheckedIOException(e);
		// }

		boolean isEmpty;
		if (languageToNbAddedFiles.isEmpty() && !configIsChanged.get()) {
			LOGGER.info("Not a single file to commit ({})", codeWriter);
			isEmpty = true;
			// } else if (configIsChanged.get()) {
			// LOGGER.info("(Config change) About to check and possibly commit any files into {} ({})",
			// codeWriter.getHtmlUrl(),
			// codeWriter.getTitle());
			// if (dryRun) {
			// LOGGER.info("Skip persisting changes as dryRun=true");
			// isEmpty = true;
			// } else {
			// codeWriter.persistChanges(pathToMutatedContent, prComments, repoProperties.getMeta().getLabels());
			// }
		} else {
			LOGGER.info("(No config change) About to commit+push {} files into {}",
					languageToNbAddedFiles.sum(),
					codeWriter);
			if (dryRun) {
				LOGGER.info("Skip persisting changes as dryRun=true");
				isEmpty = true;
			} else {
				codeWriter.persistChanges(pathToMutatedContent, prComments, repoProperties.getMeta().getLabels());
				isEmpty = false;
			}
		}

		codeWriter.cleanTmpFiles();

		return new CodeFormatResult(isEmpty, new LinkedHashMap<>(languagesCounters.asMap()));
	}

	private IEngineProperties prepareLanguageConfiguration(CleanthatRepositoryProperties repoProperties,
			CleanthatEngineProperties dirtyEngine) {

		IEngineProperties cleanEngine = configHelpers.mergeEngineProperties(repoProperties, dirtyEngine);

		String language = cleanEngine.getEngine();
		LOGGER.info("About to prepare files for language: {}", language);

		ISourceCodeProperties sourceCodeProperties = cleanEngine.getSourceCode();
		List<String> includes = cleanEngine.getSourceCode().getIncludes();
		if (includes.isEmpty()) {
			Set<String> defaultIncludes = formatterFactory.getDefaultIncludes(cleanEngine.getEngine());

			LOGGER.info("Default includes to: {}", defaultIncludes);
			// https://github.com/spring-io/spring-javaformat/blob/master/spring-javaformat-maven/spring-javaformat-maven-plugin/...
			// .../src/main/java/io/spring/format/maven/FormatMojo.java#L47
			cleanEngine = configHelpers.forceIncludes(cleanEngine, defaultIncludes);
			sourceCodeProperties = cleanEngine.getSourceCode();
			includes = cleanEngine.getSourceCode().getIncludes();
		}

		LOGGER.info("language={} Applying includes rules: {}", language, includes);
		LOGGER.info("language={} Applying excludes rules: {}", language, sourceCodeProperties.getExcludes());
		return cleanEngine;
	}

	// PMD.CloseResource: False positive as we did not open it ourselves
	@SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.CloseResource" })
	protected AtomicLongMap<String> processFiles(CleanthatSession cleanthatSession,
			AtomicLongMap<String> languageToNbMutatedFiles,
			Map<String, String> pathToMutatedContent,
			IEngineProperties engineP) {
		ISourceCodeProperties sourceCodeProperties = engineP.getSourceCode();

		AtomicLongMap<String> languageCounters = AtomicLongMap.create();

		FileSystem fs = cleanthatSession.getFileSystem();
		List<PathMatcher> includeMatchers =
				IncludeExcludeHelpers.prepareMatcher(fs, sourceCodeProperties.getIncludes());
		List<PathMatcher> excludeMatchers =
				IncludeExcludeHelpers.prepareMatcher(fs, sourceCodeProperties.getExcludes());

		// https://github.com/diffplug/spotless/issues/1555
		// If too many threads, we would load too many Spotless engines
		ListeningExecutorService executor = PepperExecutorsHelper.newShrinkableFixedThreadPool("CodeFormatter");
		CompletionService<Boolean> cs = new ExecutorCompletionService<>(executor);

		// We rely on a ThreadLocal as Engines may not be threadSafe
		// Engine, each new thread will compile its own engine
		ThreadLocal<EngineAndLinters> currentThreadEngine =
				ThreadLocal.withInitial(() -> buildProcessors(engineP, cleanthatSession));

		try {
			cleanthatSession.getCodeProvider().listFilesForContent(file -> {
				Path filePath = fs.getPath(file.getPath());

				Optional<PathMatcher> matchingInclude = IncludeExcludeHelpers.findMatching(includeMatchers, filePath);
				Optional<PathMatcher> matchingExclude = IncludeExcludeHelpers.findMatching(excludeMatchers, filePath);
				if (matchingInclude.isPresent()) {
					if (matchingExclude.isEmpty()) {
						cs.submit(() -> {
							EngineAndLinters engineSteps = currentThreadEngine.get();

							try {
								return doFormat(cleanthatSession, engineSteps, pathToMutatedContent, filePath);
							} catch (IOException e) {
								throw new UncheckedIOException("Issue with file: " + filePath, e);
							} catch (RuntimeException e) {
								throw new RuntimeException("Issue with file: " + filePath, e);
							}
						});
					} else {
						languageCounters.incrementAndGet("nb_files_both_included_excluded");
					}
				} else if (matchingExclude.isPresent()) {
					languageCounters.incrementAndGet("nb_files_excluded_not_included");
				} else {
					languageCounters.incrementAndGet("nb_files_neither_included_nor_excluded");
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException("Issue listing files", e);
		} finally {
			// TODO Should wait given time left in Lambda
			if (!MoreExecutors.shutdownAndAwaitTermination(executor, 1, TimeUnit.DAYS)) {
				LOGGER.warn("Executor not terminated");
			}
		}

		// Once here, we are guaranteed all tasks has been pushed: we can poll until null.
		while (true) {
			try {
				Future<Boolean> polled = cs.poll();

				if (polled == null) {
					break;
				}
				boolean result = polled.get();

				if (result) {
					languageToNbMutatedFiles.incrementAndGet(engineP.getEngine());
					languageCounters.incrementAndGet("nb_files_formatted");
				} else {
					languageCounters.incrementAndGet("nb_files_already_formatted");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException("Issue while one of the asynchronous tasks", e);
			}
		}

		return languageCounters;
	}

	private boolean doFormat(CleanthatSession cleanthatSession,
			EngineAndLinters engineAndLinters,
			Map<String, String> pathToMutatedContent,
			Path filePath) throws IOException {
		// Rely on the latest code (possibly formatted by a previous processor)
		Optional<String> optCode =
				loadCodeOptMutated(cleanthatSession.getCodeProvider(), pathToMutatedContent, filePath);

		if (optCode.isEmpty()) {
			LOGGER.warn("Skip processing {} as its content is not available", filePath);
			return false;
		}
		String code = optCode.get();

		LOGGER.debug("Processing path={}", filePath);
		String output = doFormat(engineAndLinters, new PathAndContent(filePath, code));
		if (!Strings.isNullOrEmpty(output) && !code.equals(output)) {
			LOGGER.info("Path={} successfully cleaned by {}", filePath, engineAndLinters);
			pathToMutatedContent.put(filePath.toString(), output);

			if (pathToMutatedContent.size() > MAX_LOG_MANY_FILES
					&& Integer.bitCount(pathToMutatedContent.size()) == 1) {
				LOGGER.warn("We are about to commit {} files. That's quite a lot.", pathToMutatedContent.size());
			}

			return true;
		} else {
			return false;
		}
	}

	/**
	 * The file may be missing for various reasons (e.g. too big to be fetched)
	 * 
	 * @param codeProvider
	 * @param pathToMutatedContent
	 * @param filePath
	 * @return an {@link Optional} of the content.
	 */
	public Optional<String> loadCodeOptMutated(ICodeProvider codeProvider,
			Map<String, String> pathToMutatedContent,
			Path filePath) {
		Optional<String> optAlreadyMutated = Optional.ofNullable(pathToMutatedContent.get(filePath));

		if (optAlreadyMutated.isPresent()) {
			return optAlreadyMutated;
		} else {
			try {
				return codeProvider.loadContentForPath(filePath);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private EngineAndLinters buildProcessors(IEngineProperties properties, CleanthatSession cleanthatSession) {
		IEngineLintFixerFactory formattersFactory = formatterFactory.makeLanguageFormatter(properties);

		return sourceCodeFormatterHelper.compile(properties, cleanthatSession, formattersFactory);
	}

	private String doFormat(EngineAndLinters compiledProcessors, PathAndContent pathAndContent) throws IOException {
		return formatterApplier.applyProcessors(compiledProcessors, pathAndContent);
	}
}
