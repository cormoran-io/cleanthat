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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.UnaryExpr.Operator;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserStmtMutator;
import eu.solven.cleanthat.engine.java.refactorer.meta.ApplyAfterMe;
import eu.solven.cleanthat.engine.java.refactorer.meta.ApplyMeBefore;
import eu.solven.cleanthat.engine.java.refactorer.meta.RepeatOnSuccess;

/**
 * Turns `boolean b=false; if(X) b=true;` into `boolean b=X;`
 * 
 * BEWARE: One may argue this is a relevant change only if the boolean is not written after its initialization, hence if
 * the boolean can be turned into a `final` variable.
 *
 * @author Benoit Lacelle
 */
@RepeatOnSuccess
@ApplyMeBefore({ RedundantLogicalComplementsInStream.class })
@ApplyAfterMe({ SimplifyBooleanInitialization.class })
public class SimplifyBooleanInitialization extends AJavaparserStmtMutator {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimplifyBooleanInitialization.class);

	static final String ANY_MATCH = "anyMatch";

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_8;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("Redundancy");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/enhanced-for-loop-to-stream-any-match.html";
	}

	@Override
	public boolean isDraft() {
		return IS_PRODUCTION_READY;
	}

	@Override
	protected boolean processNotRecursively(Statement stmt) {
		if (!stmt.isBlockStmt()) {
			return false;
		}

		var blockStmt = stmt.asBlockStmt();

		var result = false;
		for (var i = 0; i < blockStmt.getStatements().size() - 1; i++) {
			var currentStmt = blockStmt.getStatement(i);
			var nextStmt = blockStmt.getStatement(i + 1);

			result |= trySimplifyingBoolean(currentStmt, nextStmt);
		}

		return result;

	}

	@SuppressWarnings({ "PMD.NPathComplexity", "PMD.CognitiveComplexity" })
	private boolean trySimplifyingBoolean(Statement currentStmt, Statement nextStmt) {
		if (!currentStmt.isExpressionStmt()
				|| !currentStmt.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
			return false;
		}
		var assignExpr = currentStmt.asExpressionStmt().getExpression().asVariableDeclarationExpr();
		if (assignExpr.getVariables().size() != 1) {
			return false;
		}

		if (!assignExpr.getElementType().isPrimitiveType()
				|| !PrimitiveType.Primitive.BOOLEAN.equals(assignExpr.getElementType().asPrimitiveType().getType())) {
			return false;
		}

		var singleVariable = assignExpr.getVariable(0);
		Optional<Expression> optInitializer = singleVariable.getInitializer();
		if (optInitializer.isEmpty()) {
			return false;
		} else if (!optInitializer.get().isBooleanLiteralExpr()) {
			return false;
		}

		if (!nextStmt.isIfStmt() || nextStmt.asIfStmt().getElseStmt().isPresent()) {
			return false;
		}
		var ifStmt = nextStmt.asIfStmt();
		Optional<AssignExpr> optIfAssignExpr = searchSingleAssignExpr(ifStmt.getThenStmt());
		if (optIfAssignExpr.isEmpty()) {
			return false;
		}

		var ifAssignExpr = optIfAssignExpr.get();
		if (isAssignOperator(ifAssignExpr) || !ifAssignExpr.getTarget().isNameExpr()
				|| !ifAssignExpr.getTarget().asNameExpr().getNameAsString().equals(singleVariable.getNameAsString())) {
			return false;
		} else if (!ifAssignExpr.getValue().isBooleanLiteralExpr()) {
			return false;
		}

		var defaultValue = optInitializer.get().asBooleanLiteralExpr().getValue();
		var ifTrueValue = ifAssignExpr.getValue().asBooleanLiteralExpr().getValue();

		if (!defaultValue && ifTrueValue) {
			if (tryRemove(ifStmt)) {
				singleVariable.setInitializer(ifStmt.getCondition());
				return true;
			} else {
				LOGGER.debug("Issue removing `{}`", ifStmt);
			}
		} else if (defaultValue && !ifTrueValue) {
			if (tryRemove(ifStmt)) {
				Expression positiveExpression = ifStmt.getCondition();
				if (negatedNeedEnclosing(positiveExpression)) {
					// Turns `a==b` into `!(a==b)`
					positiveExpression = new EnclosedExpr(positiveExpression);
				}
				singleVariable.setInitializer(new UnaryExpr(positiveExpression, Operator.LOGICAL_COMPLEMENT));
				return true;
			} else {
				LOGGER.debug("Issue removing `{}`", ifStmt);
			}
		}
		return false;
	}

	private boolean negatedNeedEnclosing(Expression positiveExpression) {
		if (positiveExpression.isMethodCallExpr()) {
			return false;
		} else if (positiveExpression.isEnclosedExpr()) {
			return false;
		} else {
			// This captures BinaryExpr (`a==b`, `a || b`). Does it catch other cases?
			// Yes, ConditionalExpr : `a > b ? true : false`
			return true;
		}
	}

	// False-positive from PMD
	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private boolean isAssignOperator(AssignExpr ifAssignExpr) {
		return ifAssignExpr.getOperator() != AssignExpr.Operator.ASSIGN;
	}

	private Optional<AssignExpr> searchSingleAssignExpr(Statement thenStmt) {
		if (thenStmt.isExpressionStmt() && thenStmt.asExpressionStmt().getExpression().isAssignExpr()) {
			return Optional.of(thenStmt.asExpressionStmt().getExpression().asAssignExpr());
		} else if (thenStmt.isBlockStmt() && thenStmt.asBlockStmt().getStatements().size() == 1) {
			var singleStmt = thenStmt.asBlockStmt().getStatement(0);

			return searchSingleAssignExpr(singleStmt);
		} else {
			return Optional.empty();
		}
	}
}
