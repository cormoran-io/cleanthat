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
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.UnaryExpr.Operator;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserExprMutator;
import eu.solven.cleanthat.engine.java.refactorer.NodeAndSymbolSolver;

/**
 * Turns '!!someBoolean()' into 'someBoolean()'
 *
 * @author Benoit Lacelle
 */
public class AvoidMultipleUnaryOperators extends AJavaparserExprMutator {
	/**
	 * List the operators which can be removed if doubled.
	 */
	private static final Set<Operator> REDUNDANT_IF_DOUBLED =
			ImmutableSet.of(Operator.LOGICAL_COMPLEMENT, Operator.MINUS, Operator.PLUS, Operator.BITWISE_COMPLEMENT);

	@Override
	public boolean isDraft() {
		return false;
	}

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_1;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("Redundancy");
	}

	@Override
	public Optional<String> getPmdId() {
		return Optional.of("AvoidMultipleUnaryOperators");
	}

	@Override
	public String pmdUrl() {
		return "https://pmd.github.io/pmd/pmd_rules_java_errorprone.html#avoidmultipleunaryoperators";
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("RSPEC-2761");
	}

	@Override
	public Optional<String> getJSparrowId() {
		return Optional.of("RemoveDoubleNegation");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/remove-double-negation.html";
	}

	@Override
	protected boolean processExpression(NodeAndSymbolSolver<Expression> expr) {
		if (!expr.getNode().isUnaryExpr()) {
			return false;
		}
		var unaryExpr = expr.getNode().asUnaryExpr();

		Operator unaryOperator = unaryExpr.getOperator();
		if (!REDUNDANT_IF_DOUBLED.contains(unaryOperator)) {
			return false;
		}

		if (!unaryExpr.getParentNode().isPresent() || !(unaryExpr.getParentNode().get() instanceof UnaryExpr)) {
			return false;
		}
		UnaryExpr parentUnaryExpr = (UnaryExpr) unaryExpr.getParentNode().get();

		if (parentUnaryExpr.getOperator() != unaryOperator) {
			// The 2 consecutive operators are different
			return false;
		}

		// Turns '!!someExpr' into 'someExpr'
		return tryReplace(parentUnaryExpr, unaryExpr.getExpression());

	}
}
