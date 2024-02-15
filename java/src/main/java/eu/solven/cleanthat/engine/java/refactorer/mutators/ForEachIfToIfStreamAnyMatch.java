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

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.UnknownType;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserStmtMutator;
import eu.solven.cleanthat.engine.java.refactorer.NodeAndSymbolSolver;
import eu.solven.cleanthat.engine.java.refactorer.helpers.LambdaExprHelpers;
import eu.solven.cleanthat.engine.java.refactorer.helpers.NameExprHelpers;
import eu.solven.cleanthat.engine.java.refactorer.meta.ApplyAfterMe;

/**
 * See TestForEachIfToIfStreamAnyMatchCases
 *
 * @author Benoit Lacelle
 */
@ApplyAfterMe({ RedundantLogicalComplementsInStream.class, LambdaIsMethodReference.class })
public class ForEachIfToIfStreamAnyMatch extends AJavaparserStmtMutator {
	static final String ANY_MATCH = "anyMatch";

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_8;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("Stream", "Loop");
	}

	@Override
	public Optional<String> getJSparrowId() {
		return Optional.of("EnhancedForLoopToStreamAnyMatch");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/enhanced-for-loop-to-stream-any-match.html";
	}

	@Override
	protected boolean processStatement(NodeAndSymbolSolver<Statement> stmt) {
		if (!stmt.getNode().isForEachStmt()) {
			return false;
		}

		var forEachStmt = stmt.getNode().asForEachStmt();

		Optional<IfStmt> optIfStmt = StreamMutatorHelpers.findSingleIfThenStmt(forEachStmt);
		if (optIfStmt.isEmpty()) {
			return false;
		}

		var ifStmt = optIfStmt.get();
		var thenStmt = ifStmt.getThenStmt();
		if (!thenStmt.isBlockStmt()) {
			return false;
		}

		var thenAsBlockStmt = thenStmt.asBlockStmt();
		if (thenAsBlockStmt.getStatements().isEmpty()) {
			// We expect a last statement to be either break or return
			return false;
		}

		NameExpr variableName = forEachStmt.getVariableDeclarator().getNameAsExpression();
		if (NameExprHelpers.isNameReferenced(variableName, thenAsBlockStmt)) {
			// We can not move the `if` condition into a `.anyMatch` else the variable would not be available anymore to
			// the `then` block
			return false;
		}

		var lastStmt = thenAsBlockStmt.getStatement(thenAsBlockStmt.getStatements().size() - 1);
		if (!lastStmt.isReturnStmt() && !lastStmt.isBreakStmt()) {
			return false;
		}

		var replaced = replaceForEachIfByIfStream(forEachStmt, ifStmt, thenAsBlockStmt);
		if (replaced && lastStmt.isBreakStmt()) {
			tryRemove(lastStmt);
		}
		return replaced;
	}

	protected boolean replaceForEachIfByIfStream(ForEachStmt forEachStmt, IfStmt ifStmt, BlockStmt thenAsBlockStmt) {
		var variable = forEachStmt.getVariable().getVariables().get(0);
		var lambdaExpr = ifConditionToLambda(ifStmt, variable);
		if (lambdaExpr.isEmpty()) {
			return false;
		}
		Expression withStream = new MethodCallExpr(forEachStmt.getIterable(), "stream");
		Expression withStream2 = new MethodCallExpr(withStream, ANY_MATCH, new NodeList<>(lambdaExpr.get()));

		var newif = new IfStmt(withStream2, thenAsBlockStmt, null);

		return tryReplace(forEachStmt, newif);
	}

	public static Optional<LambdaExpr> ifConditionToLambda(IfStmt ifStmt, VariableDeclarator variable) {
		if (LambdaExprHelpers.hasOuterAssignExpr(ifStmt.getCondition())) {
			// We can not put a variableAssignement in a lambda
			return Optional.empty();
		}

		// No need for variable.getType()
		var parameter = new Parameter(new UnknownType(), variable.getName());

		var condition = ifStmt.getCondition();

		var lambdaExpr = new LambdaExpr(parameter, condition);
		return Optional.of(lambdaExpr);
	}
}
