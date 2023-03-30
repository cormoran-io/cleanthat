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

import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserExprMutator;

/**
 * Turns `s.replaceAll("abc", "")` into `s.replace("abc", "")`
 *
 * @author Benoit Lacelle
 */
public class StringReplaceAllWithQuotableInput extends AJavaparserExprMutator {
	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_1;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("String");
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("RSPEC-5361");
	}

	@SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
	@Override
	protected boolean processNotRecursively(Expression expr) {
		if (!expr.isMethodCallExpr()) {
			return false;
		}
		var methodCall = expr.asMethodCallExpr();

		if (!"replaceAll".equals(methodCall.getNameAsString())) {
			return false;
		} else if (methodCall.getArguments().size() != 2) {
			return false;
		} else if (!scopeHasRequiredType(methodCall.getScope(), String.class)) {
			return false;
		} else if (!scopeHasRequiredType(Optional.of(methodCall.getArgument(0)), String.class)) {
			return false;
		} else if (!methodCall.getArgument(0).isLiteralStringValueExpr()) {
			return false;
		}

		LiteralStringValueExpr literalRegex = methodCall.getArgument(0).asLiteralStringValueExpr();
		String regex = literalRegex.getValue();

		if (!regex.matches("[\\w \\-_(\\\\.)]*")) {
			// The regex is not a simple quote
			return false;
		}

		// Escape character can be removed as we will replace as a quote
		var regexAsQuote = regex.replaceAll("\\\\", "");

		literalRegex.setValue(regexAsQuote);
		methodCall.setName("replace");

		return true;
	}
}