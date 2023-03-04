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

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaparserExprMutator;

/**
 * Turns `new Double(d)` into `Double.valueOf(d)`
 *
 * @author Benoit Lacelle
 */
public class PrimitiveWrapperInstantiation extends AJavaparserExprMutator {
	@Override
	public String minimalJavaVersion() {
		// java.lang.Boolean.valueOf(boolean): 1.4
		// java.lang.Double.valueOf(double): 1.5
		return IJdkVersionConstants.JDK_5;
	}

	@Override
	public Optional<String> getCleanthatId() {
		return Optional.of("BoxedPrimitiveConstructor");
	}

	@Override
	public Optional<String> getPmdId() {
		return Optional.of("PrimitiveWrapperInstantiation");
	}

	@Override
	public String pmdUrl() {
		return "https://pmd.github.io/latest/pmd_rules_java_bestpractices.html#primitivewrapperinstantiation";
	}

	@Override
	protected boolean processNotRecursively(Expression expr) {
		if (!expr.isObjectCreationExpr()) {
			return false;
		}

		var objectCreationExpr = expr.asObjectCreationExpr();

		var type = objectCreationExpr.getType();

		if (!isBoxedPrimitive(type)) {
			return false;
		}

		return objectCreationExpr.replace(
				new MethodCallExpr(new NameExpr(type.getName()), "valueOf", objectCreationExpr.getArguments()));
	}

	private boolean isBoxedPrimitive(ClassOrInterfaceType type) {
		return type.isBoxedType();
	}
}