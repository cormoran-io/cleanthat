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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.google.common.collect.ImmutableSet;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserNodeMutator;
import eu.solven.cleanthat.engine.java.refactorer.NodeAndSymbolSolver;
import eu.solven.cleanthat.engine.java.refactorer.helpers.ImportDeclarationHelpers;
import eu.solven.cleanthat.engine.java.refactorer.helpers.MethodCallExprHelpers;
import eu.solven.cleanthat.engine.java.refactorer.helpers.ResolvedTypeHelpers;

/**
 * Turns `.stream(s -> s.size())` into `.stream(String::size)`
 *
 * @author Benoit Lacelle
 */
@SuppressWarnings("PMD.GodClass")
public class LambdaIsMethodReference extends AJavaparserNodeMutator {

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_8;
	}

	@Override
	public Set<String> getTags() {
		return ImmutableSet.of("Stream");
	}

	@Override
	public boolean isDraft() {
		// Beware of Objects import management
		return true;
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("RSPEC-1612");
	}

	@Override
	public Optional<String> getJSparrowId() {
		return Optional.of("LambdaToMethodReference");
	}

	@Override
	public String jSparrowUrl() {
		return "https://jsparrow.github.io/rules/lambda-to-method-reference.html#code-changes";
	}

	@SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
	@Override
	protected boolean processNotRecursively(NodeAndSymbolSolver<?> nodeAndSymbolSolver) {
		Node node = nodeAndSymbolSolver.getNode();
		if (!(node instanceof LambdaExpr)) {
			return false;
		}

		var lambdaExpr = (LambdaExpr) node;

		if (lambdaExpr.getParameters().size() == 1) {
			var singleParameter = lambdaExpr.getParameters().get(0);

			return hasOneVariable(nodeAndSymbolSolver, lambdaExpr, singleParameter);
		} else {
			return false;
		}

	}

	@SuppressWarnings("PMD.CognitiveComplexity")
	private boolean hasOneVariable(NodeAndSymbolSolver<?> context, LambdaExpr lambdaExpr, Parameter singleParameter) {
		var body = lambdaExpr.getBody();

		if (!body.isExpressionStmt()) {
			return false;
		}

		var asExpressionStmt = body.asExpressionStmt();
		var expression = asExpressionStmt.getExpression();
		if (expression.isInstanceOfExpr()) {
			return onInstanceOf(lambdaExpr, singleParameter, expression);
		} else if (expression.isCastExpr()) {
			return onCast(lambdaExpr, singleParameter, expression);
		} else if (expression.isBinaryExpr()) {
			var binaryExpr = expression.asBinaryExpr();
			var operator = binaryExpr.getOperator();
			var left = binaryExpr.getLeft();
			var right = binaryExpr.getRight();
			if ((operator == Operator.EQUALS || operator == Operator.NOT_EQUALS)
					&& (left.isNullLiteralExpr() || right.isNullLiteralExpr())) {

				Expression notNunull;
				if (left.isNullLiteralExpr()) {
					if (right.isNullLiteralExpr()) {
						// comparing null with null
						return tryReplace(binaryExpr, new BooleanLiteralExpr(true));
					} else {
						notNunull = right;
					}
				} else {
					notNunull = left;
				}

				if (!notNunull.isNameExpr() || !notNunull.asNameExpr().getName().equals(singleParameter.getName())) {
					return false;
				}

				// `a -> a == null` -> `Objects::isNull`
				String methodIdentifier;
				if (operator == Operator.EQUALS) {
					methodIdentifier = "isNull";
				} else {
					assert operator == Operator.NOT_EQUALS;
					methodIdentifier = "nonNull";
				}

				// TODO This lack the additional Import?
				// What if there is already an import to guava Objects?
				var methodReference = new MethodReferenceExpr(
						new TypeExpr(new ClassOrInterfaceType(null, Objects.class.getSimpleName())),
						new NodeList<>(),
						methodIdentifier);
				return lambdaExpr.replace(methodReference);
			}
		} else if (expression.isMethodCallExpr()) {
			var methodCallExpr = expression.asMethodCallExpr();

			return onMethodCall(context, lambdaExpr, singleParameter, methodCallExpr);

		}
		return false;
	}

	private boolean onCast(LambdaExpr lambdaExpr, Parameter singleParameter, Expression expression) {
		var castExpr = expression.asCastExpr();

		if (!castExpr.getType().isClassOrInterfaceType()) {
			return false;
		}

		var asClassOrInterfaceType = castExpr.getType().asClassOrInterfaceType();
		if (asClassOrInterfaceType.getTypeArguments().isPresent()) {
			// We can not have expression like `Class<? extends XXX>.class`
			return false;
		}

		Optional<ResolvedType> optResolvedType = ResolvedTypeHelpers.optResolvedType(asClassOrInterfaceType);
		if (optResolvedType.isEmpty()) {
			return false;
		} else if (optResolvedType.get().isTypeVariable()) {
			// The type is a generic bound
			// e.g. `<T> void method() {...}`
			return false;
		}

		if (!castExpr.getExpression().isNameExpr()
				|| !castExpr.getExpression().asNameExpr().getName().equals(singleParameter.getName())) {
			return false;
		}

		// `a -> (SomeClass) a` -> `SomeClass.class::cast`
		var newScope = new ClassExpr(castExpr.getType());
		var methodReference = new MethodReferenceExpr(newScope, new NodeList<>(), "cast");
		return tryReplace(lambdaExpr, methodReference);
	}

	private boolean onInstanceOf(LambdaExpr lambdaExpr, Parameter singleParameter, Expression expression) {
		var instanceOfExpr = expression.asInstanceOfExpr();

		if (!instanceOfExpr.getExpression().isNameExpr()
				|| !instanceOfExpr.getExpression().asNameExpr().getName().equals(singleParameter.getName())) {
			return false;
		}

		// `a -> a instanceof B` -> `B.class::isInstance`
		var newScope = new ClassExpr(instanceOfExpr.getType());
		var methodReference = new MethodReferenceExpr(newScope, new NodeList<>(), "isInstance");
		return tryReplace(lambdaExpr, methodReference);
	}

	private boolean onMethodCall(NodeAndSymbolSolver<?> context,
			LambdaExpr lambdaExpr,
			Parameter singleParameter,
			MethodCallExpr methodCallExpr) {
		Optional<Expression> optScope = methodCallExpr.getScope();
		if (optScope.isEmpty()) {
			return false;
		}
		var scope = optScope.get();

		if (!scope.isNameExpr() && !scope.isFieldAccessExpr()) {
			// https://github.com/solven-eu/cleanthat/issues/847
			// There is a risk of side-effect if the scope is not the lambda parameter (e.g. if the lambda chains
			// multiple calls)
			return false;
		}

		if (methodCallExpr.getArguments().size() == 1 && methodCallExpr.getArguments().get(0).isNameExpr()
				&& methodCallExpr.getArguments().get(0).asNameExpr().getName().equals(singleParameter.getName())) {

			Optional<ResolvedType> scopeType = MethodCallExprHelpers.optResolvedType(context.editNode(scope));

			if (scopeType.isEmpty()) {
				return false;
			} else if (!scopeType.get().isReferenceType()) {
				return false;
			}

			// `b -> System.out.println(b)` into `System.out::println`
			var methodReference = new MethodReferenceExpr(scope, new NodeList<>(), methodCallExpr.getNameAsString());
			return tryReplace(lambdaExpr, methodReference);
		} else if (methodCallExpr.getArguments().isEmpty() && optScope.get().isNameExpr()
				&& optScope.get().asNameExpr().getName().equals(singleParameter.getName())) {

			Optional<ResolvedType> scopeType = MethodCallExprHelpers.optResolvedType(context.editNode(scope));

			if (scopeType.isEmpty()) {
				return false;
			} else if (!scopeType.get().isConstraint()) {
				return false;
			}

			var constraint = scopeType.get().asConstraintType();
			var resolvedBound = constraint.getBound();
			if (!resolvedBound.isReferenceType()) {
				return false;
			}

			var refType = resolvedBound.asReferenceType();

			Optional<ResolvedReferenceTypeDeclaration> optTypeDeclaration = refType.getTypeDeclaration();
			if (optTypeDeclaration.isEmpty()) {
				return false;
			}

			var typeDeclaration = optTypeDeclaration.get();
			var packageName = typeDeclaration.getPackageName();
			var qualifiedName = refType.getQualifiedName();

			// TODO nameOrQualifiedName(compilationUnit.get(), getClass());
			String methodRefClassName;
			if (ImportDeclarationHelpers.isImported(context, packageName, qualifiedName)) {
				methodRefClassName = typeDeclaration.getName();
			} else {
				methodRefClassName = qualifiedName;
			}

			// `r -> r.run()` into `Runnable::run`
			var methodReference = new MethodReferenceExpr(new NameExpr(methodRefClassName),
					methodCallExpr.getTypeArguments().orElseGet(() -> new NodeList<>()),
					methodCallExpr.getNameAsString());
			return tryReplace(lambdaExpr, methodReference);
		} else {
			return false;
		}
	}
}
