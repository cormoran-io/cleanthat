/*
 * Copyright 2023 Solven
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
package eu.solven.cleanthat.language.java.spotless;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.ICodeProviderFile;
import eu.solven.cleanthat.config.ConfigHelpers;
import eu.solven.cleanthat.config.pojo.CleanthatEngineProperties;
import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;
import eu.solven.cleanthat.do_not_format_me.CleanClass;
import eu.solven.cleanthat.do_not_format_me.ManySpacesBetweenImportsSimpleClass;
import eu.solven.cleanthat.engine.ICodeFormatterApplier;
import eu.solven.cleanthat.formatter.CleanthatSession;
import eu.solven.cleanthat.formatter.CodeFormatterApplier;
import eu.solven.cleanthat.formatter.SourceCodeFormatterHelper;
import eu.solven.cleanthat.language.IEngineProperties;
import eu.solven.cleanthat.language.spotless.SpotlessFormattersFactory;
import eu.solven.pepper.resource.PepperResourceHelper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileCopyUtils;

public class TestSpotlessFormatter_Eclipse {

	final ObjectMapper objectMapper = ConfigHelpers.makeYamlObjectMapper();

	// https://www.baeldung.com/spring-load-resource-as-string
	public static String asString(Resource resource) {
		try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
			return FileCopyUtils.copyToString(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	final SpotlessFormattersFactory formatter =
			new SpotlessFormattersFactory(new ConfigHelpers(Arrays.asList(objectMapper)));
	final ICodeFormatterApplier applier = new CodeFormatterApplier();
	final SourceCodeFormatterHelper helper = new SourceCodeFormatterHelper(objectMapper);

	final CleanthatRepositoryProperties repositoryProperties = new ConfigHelpers(Arrays.asList(objectMapper))
			.loadRepoConfig(new ClassPathResource("/config/" + "cleanthat_requestSpotless_requestEclipse.yaml"));

	private IEngineProperties getEngineProperties() throws IOException, JsonParseException, JsonMappingException {

		List<CleanthatEngineProperties> languages = repositoryProperties.getEngines();
		Assert.assertEquals(1, languages.size());
		IEngineProperties engineP = new ConfigHelpers(Arrays.asList(objectMapper))
				.mergeEngineProperties(repositoryProperties, languages.get(0));
		return engineP;
	}

	final FileSystem fileSystem;
	final ICodeProvider classpathCodeProvider = new ICodeProvider() {

		@Override
		public Optional<String> loadContentForPath(String path) throws IOException {
			return Optional.of(PepperResourceHelper.loadAsString(path));
		}

		@Override
		public void listFilesForContent(Consumer<ICodeProviderFile> consumer) throws IOException {
			throw new IllegalArgumentException("Not implemented");
		}

		@Override
		public String getRepoUri() {
			return Thread.currentThread().getContextClassLoader().getName();
		}
	};

	CleanthatSession cleanthatSession;
	{
		try {
			fileSystem = MemoryFileSystemBuilder.newEmpty().build();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		cleanthatSession = new CleanthatSession(fileSystem, classpathCodeProvider, repositoryProperties);
	}

	@Test
	public void testFormat_Clean() throws IOException {
		IEngineProperties languageP = getEngineProperties();

		URL location = CleanClass.class.getProtectionDomain().getCodeSource().getLocation();
		String classAsString = asString(new UrlResource(location));

		String cleaned = applier
				.applyProcessors(helper.compile(languageP, cleanthatSession, formatter), "someFilePath", classAsString);
		Assert.assertEquals(cleaned, classAsString);

		// Assert.assertEquals(1, formatter.getCacheSize());
	}

	@Test
	public void testFormat_WrongIndentation() throws IOException {
		IEngineProperties languageP = getEngineProperties();

		URL location = ManySpacesBetweenImportsSimpleClass.class.getProtectionDomain().getCodeSource().getLocation();
		String classAsString = asString(new UrlResource(location));

		String cleaned = applier
				.applyProcessors(helper.compile(languageP, cleanthatSession, formatter), "someFilePath", classAsString);
		Assert.assertEquals(cleaned, classAsString);

		// Assert.assertEquals(1, formatter.getCacheSize());
	}

	@Test
	public void testFormat_WrongIndentation_Multiple() throws IOException {
		IEngineProperties languageP = getEngineProperties();

		URL location = ManySpacesBetweenImportsSimpleClass.class.getProtectionDomain().getCodeSource().getLocation();
		String classAsString = asString(new UrlResource(location));

		// Format twice
		applier.applyProcessors(helper.compile(languageP, cleanthatSession, formatter), "someFilePath", classAsString);
		applier.applyProcessors(helper.compile(languageP, cleanthatSession, formatter), "someFilePath", classAsString);

		// Check the cache is used properly
		// Assert.assertEquals(1, formatter.getCacheSize());
	}
}
