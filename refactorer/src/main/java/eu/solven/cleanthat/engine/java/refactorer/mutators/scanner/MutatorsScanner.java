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
package eu.solven.cleanthat.engine.java.refactorer.mutators.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.codehaus.plexus.languages.java.version.JavaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.ClassPath;

import eu.solven.cleanthat.config.GitService;
import eu.solven.cleanthat.engine.java.refactorer.meta.IConstructorNeedsJdkVersion;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;

/**
 * Scans dynamically for available rules
 * 
 * @author Benoit Lacelle
 *
 */
// https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection
public class MutatorsScanner {
	private static final Logger LOGGER = LoggerFactory.getLogger(MutatorsScanner.class);

	private static final AtomicInteger ERROR_COUNTS = new AtomicInteger();

	/**
	 * 
	 * @return the number of ERRORS which has been logged without failing the process.
	 */
	@VisibleForTesting
	public static int getErrorCount() {
		return ERROR_COUNTS.get();
	}

	public List<Class<? extends IMutator>> getPackageMutatorClasses(String packageName) {
		Set<String> classNames;
		try {
			classNames = getClasses(packageName);
		} catch (ClassNotFoundException | IOException e) {
			ERROR_COUNTS.incrementAndGet();
			LOGGER.error("Issue loading mutators from {}", packageName, e);
			return Collections.emptyList();
		}

		if (classNames.isEmpty()) {
			var cleanThatSha1 = GitService.safeGetSha1();

			LOGGER.warn("CleanThat failed detecting a single mutator in {} sha1={}", packageName, cleanThatSha1);
		}

		List<Class<? extends IMutator>> classes = classNames.stream().map(s -> {
			try {
				return Class.forName(s);
			} catch (ClassNotFoundException e) {
				ERROR_COUNTS.incrementAndGet();
				LOGGER.error("Issue with {}", s, e);
				return null;
			}
		})
				.filter(IMutator.class::isAssignableFrom)
				.filter(c -> !Modifier.isAbstract(c.getModifiers()))
				.map(c -> (Class<? extends IMutator>) c.asSubclass(IMutator.class))
				.collect(Collectors.toList());
		return classes;
	}

	/**
	 * The package is not search recursively.
	 * 
	 * @param classes
	 *            The IMutator classes to instantiate
	 * @return a {@link List} of {@link IMutator} detected in given package.
	 */
	public static <T extends IMutator> List<T> instantiate(JavaVersion sourceJdkVersion,
			List<Class<? extends T>> classes) {
		return classes.stream()
				.filter(Objects::nonNull)
				.filter(IMutator.class::isAssignableFrom)
				.map(c -> instantiate(sourceJdkVersion, c))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	public static <T extends IMutator> T instantiate(JavaVersion sourceJdkVersion, Class<? extends T> mutatorClass) {
		try {
			if (IConstructorNeedsJdkVersion.class.isAssignableFrom(mutatorClass)) {
				return mutatorClass.getConstructor(JavaVersion.class).newInstance(sourceJdkVersion);
			} else {
				return mutatorClass.getConstructor().newInstance();
			}
		} catch (ReflectiveOperationException e) {
			ERROR_COUNTS.incrementAndGet();
			LOGGER.error("Issue with {}", mutatorClass, e);
			return null;
		}
	}

	/**
	 * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
	 *
	 * @param packageName
	 *            The base package
	 * @return The classes
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	// https://stackoverflow.com/questions/28327389/how-can-i-do-to-get-all-class-of-a-given-package-with-guava
	private static Set<String> getClasses(String packageName) throws ClassNotFoundException, IOException {
		final var loader = Thread.currentThread().getContextClassLoader();

		Set<String> classNames = new TreeSet<>();
		try {

			var classpath = ClassPath.from(loader);
			classpath.getTopLevelClasses(packageName).forEach(classInfo -> classNames.add(classInfo.getName()));
		} catch (IOException e) {
			throw new UncheckedIOException("Issue with " + packageName, e);
		}

		return classNames;
	}

	public static Collection<Class<? extends IMutator>> scanPackageMutators(String packageName) {
		return new MutatorsScanner().getPackageMutatorClasses(packageName);
	}

}
