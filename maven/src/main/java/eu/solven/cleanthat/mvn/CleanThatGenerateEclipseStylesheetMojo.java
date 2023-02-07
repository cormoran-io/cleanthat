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
package eu.solven.cleanthat.mvn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.io.ByteStreams;
import eu.solven.cleanthat.code_provider.github.CodeCleanerSpringConfig;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderWriter;
import eu.solven.cleanthat.codeprovider.resource.CleanthatUrlLoader;
import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.config.pojo.ICleanthatStepParametersProperties;
import eu.solven.cleanthat.engine.java.eclipse.checkstyle.XmlProfileWriter;
import eu.solven.cleanthat.engine.java.eclipse.generator.EclipseStylesheetGenerator;
import eu.solven.cleanthat.engine.java.eclipse.generator.IEclipseStylesheetGenerator;
import eu.solven.cleanthat.git.GitIgnoreParser;
import eu.solven.cleanthat.language.spotless.CleanthatSpotlessStepParametersProperties;
import eu.solven.cleanthat.language.spotless.SpotlessFormattersFactory;
import eu.solven.cleanthat.spotless.FormatterFactory;
import eu.solven.cleanthat.spotless.language.JavaFormatterStepFactory;
import eu.solven.cleanthat.spotless.pojo.SpotlessEngineProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessFormatterProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepParametersProperties;
import eu.solven.cleanthat.spotless.pojo.SpotlessStepProperties;
import eu.solven.cleanthat.utils.ResultOrError;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * The mojo generates an Eclipse formatter stylesheet minimyzing modifications over existing codebase.
 * 
 * @author Benoit Lacelle
 *
 */
// https://maven.apache.org/guides/plugin/guide-java-plugin-development.html
@SuppressWarnings("PMD.GodClass")
@Mojo(name = CleanThatGenerateEclipseStylesheetMojo.GOAL_ECLIPSE,
		defaultPhase = LifecyclePhase.NONE,
		threadSafe = true,
		aggregator = true,
		requiresProject = false)
public class CleanThatGenerateEclipseStylesheetMojo extends ACleanThatSpringMojo {
	private static final Logger LOGGER = LoggerFactory.getLogger(CleanThatCleanThatMojo.class);

	// ".*/src/[main|test]/java/.*/.*\\.java"
	public static final String DEFAULT_JAVA_REGEX = ".*\\.java";

	public static final String GOAL_ECLIPSE = "eclipse_formatter-stylesheet";

	// https://stackoverflow.com/questions/3084629/finding-the-root-directory-of-a-multi-module-maven-reactor-project
	@Parameter(property = "eclipse_formatter.url",
			// defaultValue = "${maven.multiModuleProjectDirectory}/.cleanthat/eclipse_formatter-stylesheet.xml"
			defaultValue = "${session.request.multiModuleProjectDirectory}"
					+ JavaFormatterStepFactory.DEFAULT_ECLIPSE_FILE)
	private String eclipseConfigPath;

	// Generate the stylesheet can be very slow: make it faster by considering a subset of files
	// e.g. -Djava.regex=MyClass\.java
	@Parameter(property = "java.regex", defaultValue = DEFAULT_JAVA_REGEX)
	private String javaRegex;

	// PnYnMnDTnHnMnS
	// https://en.wikipedia.org/wiki/ISO_8601#Durations
	@Parameter(property = "duration.limit", defaultValue = "PT1M")
	private String rawDurationLimit;

	@VisibleForTesting
	protected void setConfigPath(String eclipseConfigPath) {
		this.eclipseConfigPath = eclipseConfigPath;
	}

	@VisibleForTesting
	protected void setJavaRegex(String javaRegex) {
		this.javaRegex = javaRegex;
	}

	@Override
	protected List<? extends Class<?>> springClasses() {
		return Arrays.asList(EclipseStylesheetGenerator.class,
				// Used to parse the existing config, to inject Eclipse stylesheet
				CodeCleanerSpringConfig.class);
	}

	@Override
	public void doClean(ApplicationContext appContext) throws IOException, MojoFailureException {
		IEclipseStylesheetGenerator generator = appContext.getBean(IEclipseStylesheetGenerator.class);

		Map<Path, String> pathToContent = loadAnyJavaFile(generator);

		Duration durationLimit = Duration.parse(rawDurationLimit);
		OffsetDateTime timeLimit = OffsetDateTime.now().plus(durationLimit);
		LOGGER.info(
				"Job is limitted to duration={} (can be adjusted with '-Dduration.limit=PT1M') It will end at most at: {}",
				rawDurationLimit,
				timeLimit);

		Map<String, String> settings = generator.generateSettings(timeLimit, pathToContent);
		Path eclipseConfigPath = writeSettings(settings);

		String rawConfigPath = getConfigPath();
		if (Strings.isNullOrEmpty(rawConfigPath)) {
			LOGGER.warn(
					"configPath is empty (-Dcleanthat.configPath=xxx): please adjust your cleanthat.yaml manually, in order to rely on '{}'",
					eclipseConfigPath);
			return;
		}

		Path configPath = Paths.get(rawConfigPath);
		if (!configPath.toFile().isFile()) {
			LOGGER.warn("configPath={} does not exists: please adjust your cleanthat.yaml manually", eclipseConfigPath);
			return;
		}

		LOGGER.info("About to inject '{}' into '{}'", eclipseConfigPath, rawConfigPath);

		Path normalizedEclipsePath = relativizeFromGitRootAsFSRoot(eclipseConfigPath, configPath);
		injectStylesheetInConfig(appContext, normalizedEclipsePath, configPath);
	}

	protected Path relativizeFromGitRootAsFSRoot(Path eclipseConfigPath, Path configPath) {
		Path rootFolder = configPath.getParent();
		if (rootFolder == null) {
			throw new IllegalArgumentException("Issue with configPath: " + configPath + " (no root)");
		}

		return Paths.get("/").resolve(rootFolder.relativize(eclipseConfigPath));
	}

	/**
	 * This will generate an Eclipse stylesheet based on current repository, and persist its configuration into
	 * cleanthat+spotless configuration files
	 * 
	 * @param appContext
	 * @param eclipseConfigPath
	 * @param configPath
	 * @throws MojoFailureException
	 * @throws IOException
	 */
	public void injectStylesheetInConfig(ApplicationContext appContext, Path eclipseConfigPath, Path configPath)
			throws MojoFailureException, IOException {
		LOGGER.info("You need to wire manually the Eclipse stylesheet path (e.g. into '/.cleanthat/spotless.yaml'");
		ICodeProviderWriter codeProvider = CleanThatMavenHelper.makeCodeProviderWriter(this);
		MavenCodeCleaner codeCleaner = CleanThatMavenHelper.makeCodeCleaner(appContext);
		ResultOrError<CleanthatRepositoryProperties, String> optResult =
				codeCleaner.loadAndCheckConfiguration(codeProvider);

		if (optResult.getOptError().isPresent()) {
			String error = optResult.getOptError().get();
			throw new MojoFailureException("ARG", error, error);
		}

		CleanthatRepositoryProperties loadedConfig = optResult.getOptResult().get();

		Optional<CleanthatEngineProperties> optSpotlessProperties = loadedConfig.getEngines()
				.stream()
				.filter(lp -> CleanthatSpotlessStepParametersProperties.ENGINE_ID.equals(lp.getEngine()))
				.findAny();

		boolean needToSaveCleanthat;

		CleanthatEngineProperties spotlessEngine;
		if (optSpotlessProperties.isEmpty()) {
			spotlessEngine = appContext.getBean(SpotlessFormattersFactory.class).makeDefaultProperties();

			loadedConfig.setEngines(ImmutableList.<CleanthatEngineProperties>builder()
					.addAll(loadedConfig.getEngines())
					.add(spotlessEngine)
					.build());

			LOGGER.info("Append Spotless engine into Cleanthat configuration");
			needToSaveCleanthat = true;
		} else {
			spotlessEngine = optSpotlessProperties.get();
			needToSaveCleanthat = false;
		}

		ConfigHelpers configHelpers = appContext.getBean(ConfigHelpers.class);
		ObjectMapper objectMapper = configHelpers.getObjectMapper();

		ICleanthatStepParametersProperties spotlessParameters = spotlessEngine.getSteps().get(0).getParameters();
		String pathToSpotlessConfig =
				objectMapper.convertValue(spotlessParameters, CleanthatSpotlessStepParametersProperties.class)
						.getConfiguration();

		SpotlessEngineProperties spotlessEngineProperties =
				loadOrInitSpotlessEngineProperties(codeProvider, objectMapper, pathToSpotlessConfig);

		Optional<SpotlessFormatterProperties> optJavaFormatter = spotlessEngineProperties.getFormatters()
				.stream()
				.filter(f -> FormatterFactory.ID_JAVA.equals(f.getFormat()))
				.findFirst();

		SpotlessFormatterProperties javaFormatter;

		boolean needToSaveSpotless = false;
		if (optJavaFormatter.isEmpty()) {
			javaFormatter = SpotlessFormatterProperties.builder().format(FormatterFactory.ID_JAVA).build();

			LOGGER.info("Append java formatter into Spotless engine");
			needToSaveSpotless = true;
		} else {
			javaFormatter = optJavaFormatter.get();
		}

		SpotlessStepProperties eclipseStep;
		Optional<SpotlessStepProperties> optEclipseStep = javaFormatter.getSteps()
				.stream()
				.filter(f -> JavaFormatterStepFactory.ID_ECLIPSE.equalsIgnoreCase(f.getId()))
				.findFirst();
		if (optEclipseStep.isEmpty()) {
			eclipseStep = JavaFormatterStepFactory.makeDefaultEclipseStep();

			javaFormatter.setSteps(ImmutableList.<SpotlessStepProperties>builder()
					.addAll(javaFormatter.getSteps())
					.add(eclipseStep)
					.build());

			LOGGER.info("Append eclipse step into Java formatter");
			needToSaveSpotless = true;
		} else {
			eclipseStep = optEclipseStep.get();
		}

		SpotlessStepParametersProperties eclipseParameters = eclipseStep.getParameters();
		if (eclipseParameters == null) {
			eclipseParameters = JavaFormatterStepFactory.makeDefaultEclipseStep().getParameters();
			needToSaveSpotless = true;
		}

		String eclipseStylesheetFile =
				eclipseParameters.getCustomProperty(JavaFormatterStepFactory.KEY_ECLIPSE_FILE, String.class);
		if (eclipseStylesheetFile != null && !eclipseStylesheetFile.equals(eclipseStylesheetFile)) {
			// TODO We would prefer writing the new stylesheet in the previously configured path
			eclipseParameters.putProperty(pathToSpotlessConfig, pathToSpotlessConfig);
			needToSaveSpotless = true;
		}

		persistConfigurationFiles(appContext, configPath, loadedConfig, needToSaveCleanthat, needToSaveSpotless);
	}

	private SpotlessEngineProperties loadOrInitSpotlessEngineProperties(ICodeProvider codeProvider,
			ObjectMapper objectMapper,
			String pathToSpotlessConfig) throws IOException {
		return loadSpotlessEngineProperties(codeProvider, objectMapper, pathToSpotlessConfig);
	}

	public static SpotlessEngineProperties loadSpotlessEngineProperties(ICodeProvider codeProvider,
			ObjectMapper objectMapper,
			String pathToSpotlessConfig) throws IOException {
		Resource spotlessConfigAsResource = CleanthatUrlLoader.loadUrl(codeProvider, pathToSpotlessConfig);

		try (InputStream inputStream = spotlessConfigAsResource.getInputStream()) {
			return objectMapper.readValue(inputStream, SpotlessEngineProperties.class);
		}
	}

	private void persistConfigurationFiles(ApplicationContext appContext,
			Path configPath,
			CleanthatRepositoryProperties loadedConfig,
			boolean needToSaveCleanthat,
			boolean needToSaveSpotless) {
		List<ObjectMapper> objectMappers =
				appContext.getBeansOfType(ObjectMapper.class).values().stream().collect(Collectors.toList());
		ObjectMapper yamlObjectMapper = ConfigHelpers.getYaml(objectMappers);

		if (needToSaveCleanthat) {
			// Prepare the configuration as yaml
			String asYaml;
			try {
				asYaml = yamlObjectMapper.writeValueAsString(loadedConfig);
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Issue converting " + loadedConfig + " to YAML", e);
			}

			// Write at given path
			try {
				Files.writeString(configPath, asYaml, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException("Issue writing YAML into: " + configPath, e);
			}
		}

		if (needToSaveSpotless) {
			// Prepare the configuration as yaml
			String asYaml;
			try {
				asYaml = yamlObjectMapper.writeValueAsString(loadedConfig);
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Issue converting " + loadedConfig + " to YAML", e);
			}

			// Write at given path
			try {
				Files.writeString(configPath, asYaml, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException("Issue writing YAML into: " + configPath, e);
			}
		}
	}

	/**
	 * Convert an OS dependent path to a '/root/folder/file' String.
	 * 
	 * @param path
	 * @return
	 */
	public static String toString(Path path) {
		return Streams.stream(path.iterator())
				.map(p -> p.getFileName().toString())
				.collect(Collectors.joining("/", "/", ""));
	}

	protected Map<Path, String> loadAnyJavaFile(IEclipseStylesheetGenerator generator) {
		MavenProject mavenProject = getProject();

		List<MavenProject> collectedProjects = mavenProject.getCollectedProjects();

		Path executionRoot = getBaseDir().toPath();
		Set<String> gitIgnorePatterns = loadGitIgnorePatterns(executionRoot);

		Set<Path> roots;
		if (collectedProjects == null) {
			LOGGER.info("Processing a folder with no 'pom.xml'. We will then process anything in '{}' matching '{}'",
					executionRoot,
					javaRegex);
			roots = Collections.singleton(executionRoot);
		} else {
			roots = collectedProjects.stream().flatMap(p -> {
				Path projectBaseDir = p.getBasedir().toPath();
				List<String> sourceRoots = p.getCompileSourceRoots();
				List<String> testRoots = p.getTestCompileSourceRoots();

				LOGGER.debug("Consider for baseDir '{}': {} and {}", projectBaseDir, sourceRoots, testRoots);

				// We make path relative to the baseDir, even through it seems mvn provides absolute paths is default
				// case
				return Stream.concat(sourceRoots.stream(), testRoots.stream())
						.map(sourceFolder -> projectBaseDir.resolve(sourceFolder));
			}).collect(Collectors.toSet());
		}

		return loadAnyJavaFile(gitIgnorePatterns, generator, roots);
	}

	protected Set<String> loadGitIgnorePatterns(Path executionRoot) {
		// TODO This assumes the command is run from the repository root
		Path gitIgnore = executionRoot.resolve(".gitignore");
		File gitIgnoreFile = gitIgnore.toFile();

		Set<String> gitIgnorePatterns;
		if (gitIgnoreFile.isFile()) {
			LOGGER.info("We detected a .gitignore ({})", gitIgnore);

			String gitIgnoreContent;
			try {
				gitIgnoreContent =
						new String(ByteStreams.toByteArray(new FileSystemResource(gitIgnoreFile).getInputStream()));
			} catch (IOException e) {
				throw new UncheckedIOException("Issue loading: " + gitIgnore, e);
			}
			gitIgnorePatterns = GitIgnoreParser.parsePatterns(gitIgnoreContent);
		} else {
			gitIgnorePatterns = Collections.emptySet();
		}
		return gitIgnorePatterns;
	}

	protected Map<Path, String> loadAnyJavaFile(Set<String> gitIgnorePatterns,
			IEclipseStylesheetGenerator generator,
			Set<Path> roots) {
		Map<Path, String> pathToContent = new LinkedHashMap<>();

		AtomicInteger nbFilteredByGitignore = new AtomicInteger();

		roots.forEach(rootAsPath -> {
			try {
				if (!rootAsPath.toFile().exists()) {
					LOGGER.debug("The root folder '{}' does not exist", rootAsPath);
					return;
				}

				Pattern compiledRegex = Pattern.compile(javaRegex);
				Map<Path, String> fileToContent = generator.loadFilesContent(rootAsPath, compiledRegex);

				if (!gitIgnorePatterns.isEmpty()) {
					// Enable mutability
					Map<Path, String> gitIgnoreFiltered = new HashMap<>(fileToContent);

					gitIgnoreFiltered.keySet().removeIf(path -> !GitIgnoreParser.accept(gitIgnorePatterns, path));

					nbFilteredByGitignore.addAndGet(fileToContent.size() - gitIgnoreFiltered.size());
					fileToContent = gitIgnoreFiltered;
				}

				LOGGER.info("Loaded {} files from {}", fileToContent.size(), rootAsPath);

				if (!gitIgnorePatterns.isEmpty()) {
					LOGGER.info("#files ignored by .gitignore: {}", nbFilteredByGitignore);
				}

				pathToContent.putAll(fileToContent);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		return pathToContent;
	}

	protected Path writeSettings(Map<String, String> settings) throws IOException {
		if (eclipseConfigPath.startsWith("${")) {
			throw new IllegalArgumentException("Issue with mvn placeholders: " + eclipseConfigPath);
		}
		Path whereToWrite = Paths.get(eclipseConfigPath);

		File whereToWriteAsFile = whereToWrite.toFile().getAbsoluteFile();
		if (whereToWriteAsFile.exists()) {
			if (whereToWriteAsFile.isFile()) {
				LOGGER.warn("We are going to write over '{}'", whereToWrite);
			} else {
				throw new IllegalStateException("There is something but not a file/folder at: " + whereToWriteAsFile);
			}
		} else {
			LOGGER.info("About to write Eclipse configuration at: {}", whereToWriteAsFile);
			whereToWriteAsFile.getParentFile().mkdirs();
		}

		try (InputStream is = XmlProfileWriter.writeFormatterProfileToStream("cleanthat", settings);
				OutputStream outputStream = Files.newOutputStream(whereToWrite, StandardOpenOption.CREATE)) {
			ByteStreams.copy(is, outputStream);
		} catch (TransformerException | ParserConfigurationException e) {
			throw new IllegalArgumentException(e);
		}

		return whereToWrite;
	}
}