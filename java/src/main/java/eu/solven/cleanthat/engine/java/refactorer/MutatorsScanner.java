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
package eu.solven.cleanthat.engine.java.refactorer;

import com.google.common.reflect.ClassPath;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans dynamically for available rules
 * 
 * @author Benoit Lacelle
 *
 */
// https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection
public class MutatorsScanner {
	private static final Logger LOGGER = LoggerFactory.getLogger(MutatorsScanner.class);

	public List<IMutator> getMutators() {
		String packageName = "eu.solven.cleanthat.engine.java.refactorer.mutators";
		Set<String> classes;
		try {
			classes = getClasses(packageName);
		} catch (ClassNotFoundException | IOException e) {
			LOGGER.error("Issue loading mutators from {}", packageName, e);
			return Collections.emptyList();
		}

		if (classes.isEmpty()) {
			LOGGER.error("CleanThat failed detecting a single mutator in {}", packageName);
		}

		return classes.stream().map(s -> {
			try {
				return Class.forName(s);
			} catch (ClassNotFoundException e) {
				LOGGER.error("Issue with {}", s, e);
				return null;
			}
		}).filter(c -> c != null).filter(c -> IMutator.class.isAssignableFrom(c)).map(c -> {
			try {
				return c.asSubclass(IMutator.class).getConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				LOGGER.error("Issue with {}", c, e);
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());
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
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();

		Set<String> classNames = new TreeSet<>();
		try {

			ClassPath classpath = ClassPath.from(loader);
			for (ClassPath.ClassInfo classInfo : classpath.getTopLevelClasses(packageName)) {
				classNames.add(classInfo.getName());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Issue with " + packageName, e);
		}

		return classNames;
	}
}
