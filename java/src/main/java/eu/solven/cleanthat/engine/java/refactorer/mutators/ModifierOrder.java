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
package eu.solven.cleanthat.engine.java.refactorer.mutators;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserNodeMutator;
import eu.solven.cleanthat.engine.java.refactorer.NodeAndSymbolSolver;

/**
 * Order modifiers according the the Java specification.
 *
 * @author Benoit Lacelle
 * @see https://github.com/checkstyle/checkstyle/blob/master/src/xdocs/checks/modifier/modifierorder.xml
 * @see
 */
public class ModifierOrder extends AJavaparserNodeMutator {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModifierOrder.class);

	private static final List<String> ORDERED_MODIFIERS = ImmutableList.of("public",
			"protected",
			"private",
			"abstract",
			"default",
			"static",
			"sealed",
			"non-sealed",
			"final",
			"transient",
			"volatile",
			"synchronized",
			"native",
			"strictfp");

	@Override
	public boolean isDraft() {
		return IS_PRODUCTION_READY;
	}

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_1;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("Convention");
	}

	@Override
	public Optional<String> getCheckstyleId() {
		return Optional.of("ModifierOrder");
	}

	@Override
	public String checkstyleUrl() {
		return "https://checkstyle.sourceforge.io/apidocs/com/puppycrawl/tools/checkstyle/checks/modifier/ModifierOrderCheck.html";
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("RSPEC-1124");
	}

	@Override
	public Optional<String> getJSparrowId() {
		return Optional.of("ReorderModifiers");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/reorder-modifiers.html";
	}

	@Override
	protected boolean processNotRecursively(NodeAndSymbolSolver<?> nodeAndContext) {
		Node node = nodeAndContext.getNode();
		if (node instanceof NodeWithModifiers<?>) {
			NodeWithModifiers<?> nodeWithModifiers = (NodeWithModifiers<?>) node;
			NodeList<Modifier> modifiers = nodeWithModifiers.getModifiers();

			NodeList<Modifier> mutableModifiers = new NodeList<>(modifiers);

			Collections.sort(mutableModifiers, new Comparator<Modifier>() {

				@Override
				public int compare(Modifier o1, Modifier o2) {
					return compare2(o1.getKeyword().asString(), o2.getKeyword().asString());
				}

				private int compare2(String left, String right) {
					return Integer.compare(ORDERED_MODIFIERS.indexOf(left), ORDERED_MODIFIERS.indexOf(right));
				}
			});

			var changed = areSameReferences(modifiers, mutableModifiers);

			if (changed) {
				return applyModifiers(nodeWithModifiers, modifiers, mutableModifiers);
			}
		}

		return false;
	}

	private boolean applyModifiers(NodeWithModifiers<?> nodeWithModifiers,
			NodeList<Modifier> originalModifiers,
			NodeList<Modifier> sortedModifiers) {
		if (sortedModifiers.stream()
				.map(m -> m.getKeyword())
				.anyMatch(m -> m == Keyword.SEALED || m == Keyword.NON_SEALED)) {
			LOGGER.warn("We do not re-order {} into {} due to {}",
					originalModifiers,
					sortedModifiers,
					"https://github.com/javaparser/javaparser/issues/4245");
			return false;
		}

		// https://github.com/javaparser/javaparser/issues/3935
		nodeWithModifiers.setModifiers();

		LOGGER.debug("We fixed the ordering of modifiers");
		nodeWithModifiers.setModifiers(sortedModifiers);

		return true;
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private boolean areSameReferences(NodeList<Modifier> modifiers, NodeList<Modifier> mutableModifiers) {
		var changed = false;
		for (var i = 0; i < modifiers.size(); i++) {
			// Check by reference
			if (modifiers.get(i) != mutableModifiers.get(i)) {
				changed = true;
				break;
			}
		}
		return changed;
	}
}
