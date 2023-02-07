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

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.types.ResolvedType;
import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaParserMutator;
import eu.solven.cleanthat.engine.java.refactorer.meta.IMutator;
import eu.solven.pepper.logging.PepperLogHelper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * cases inspired from #description
 *
 * @author Sébastien Collard
 */
public class CreateTempFilesUsingNio extends AJavaParserMutator implements IMutator {

	private static final Logger LOGGER = LoggerFactory.getLogger(UseIsEmptyOnCollections.class);

	@Override
	public String minimalJavaVersion() {
		// java.nio.Files has been introduced in JDK7
		return IJdkVersionConstants.JDK_7;
	}

	@Override
	public boolean isProductionReady() {
		return true;
	}

	@Override
	public String sonarUrl() {
		return "https://rules.sonarsource.com/java/RSPEC-2976";
	}

	@Override
	public String jsparrowUrl() {
		return "https://jsparrow.github.io/rules/create-temp-files-using-java-nio.html";
	}

	@Override
	public String getId() {
		return "CreateTempFilesUsingNio";
	}

	@Override
	protected boolean processNotRecursively(Node node) {
		LOGGER.debug("{}", PepperLogHelper.getObjectAndClass(node));
		// ResolvedMethodDeclaration test;
		if (!(node instanceof MethodCallExpr)) {
			return false;
		}
		MethodCallExpr methodCallExpr = (MethodCallExpr) node;
		if (!"createTempFile".equals(methodCallExpr.getName().getIdentifier())) {
			return false;
		}
		Optional<Boolean> optIsStatic = mayStaticCall(methodCallExpr);
		if (optIsStatic.isPresent() && !optIsStatic.get()) {
			return false;
		}
		Optional<Expression> optScope = methodCallExpr.getScope();
		if (optScope.isPresent()) {
			Optional<ResolvedType> type = optResolvedType(optScope.get());
			if (type.isEmpty() || !"java.io.File".equals(type.get().asReferenceType().getQualifiedName())) {
				return false;
			}
			LOGGER.debug("Found : {}", node);
			if (process(methodCallExpr)) {
				return true;
			}
		}
		return false;
	}

	private Optional<Boolean> mayStaticCall(MethodCallExpr methodCallExpr) {
		try {
			return Optional.of(methodCallExpr.resolve().isStatic());
		} catch (Exception e) {
			// Knowing if class is static requires a SymbolResolver:
			// 'java.lang.IllegalStateException: Symbol resolution not configured: to configure consider setting a
			// SymbolResolver in the ParserConfiguration'
			LOGGER.debug("arg", e);
			return Optional.empty();
		}
	}

	private boolean process(MethodCallExpr methodExp) {
		List<Expression> arguments = methodExp.getArguments();
		Optional<MethodCallExpr> optToPath;
		NameExpr newStaticClass = new NameExpr("Files");
		String newStaticMethod = "createTempFile";
		int minArgSize = 2;
		if (arguments.size() == minArgSize) {
			// Create in default tmp directory
			LOGGER.debug("Add java.nio.file.Files to import");
			methodExp.tryAddImportToParentCompilationUnit(Files.class);
			optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, methodExp.getArguments()));
		} else if (arguments.size() == minArgSize + 1) {
			Expression arg0 = methodExp.getArgument(0);
			Expression arg1 = methodExp.getArgument(1);
			Expression arg3 = methodExp.getArgument(2);
			if (arg3.isObjectCreationExpr()) {
				methodExp.tryAddImportToParentCompilationUnit(Paths.class);
				ObjectCreationExpr objectCreation = (ObjectCreationExpr) methodExp.getArgument(minArgSize);
				NodeList<Expression> objectCreationArguments = objectCreation.getArguments();
				NodeList<Expression> replaceArguments =
						new NodeList<>(new MethodCallExpr(new NameExpr("Paths"), "get", objectCreationArguments),
								arg0,
								arg1);
				optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, replaceArguments));
			} else if (arg3.isNameExpr()) {
				NodeList<Expression> replaceArguments = new NodeList<>(new MethodCallExpr(arg3, "toPath"), arg0, arg1);
				optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, replaceArguments));
			} else if (arg3.isNullLiteralExpr()) {
				// 'null' is managed specifically as Files.createTempFile does not accept a null as directory
				NodeList<Expression> replaceArguments = new NodeList<>(arg0, arg1);
				optToPath = Optional.of(new MethodCallExpr(newStaticClass, newStaticMethod, replaceArguments));
			} else {
				optToPath = Optional.empty();
			}
		} else {
			optToPath = Optional.empty();
		}
		optToPath.ifPresent(toPath -> {
			methodExp.tryAddImportToParentCompilationUnit(Files.class);
			LOGGER.info("Turning {} into {}", methodExp, toPath);
			methodExp.replace(new MethodCallExpr(toPath, "toFile"));
		});
		return optToPath.isPresent();
	}
}
