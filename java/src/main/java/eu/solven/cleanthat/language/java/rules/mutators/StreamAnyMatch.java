package eu.solven.cleanthat.language.java.rules.mutators;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;

import eu.solven.cleanthat.language.java.IJdkVersionConstants;
import eu.solven.cleanthat.language.java.rules.AJavaParserRule;
import eu.solven.cleanthat.language.java.rules.meta.IClassTransformer;
import eu.solven.pepper.logging.PepperLogHelper;

/**
 * Prefer 'o.isPresent()' over 'o.isEmpty() == 0'
 *
 * @author Benoit Lacelle
 */
public class StreamAnyMatch extends AJavaParserRule implements IClassTransformer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamAnyMatch.class);

	private static final String METHOD_FILTER = "filter";
	private static final String METHOD_FIND_ANY = "findAny";
	private static final String METHOD_IS_PRESENT = "isPresent";
	private static final String METHOD_IS_EMPTY = "isEmpty";

	private static final String METHOD_ANY_MATCH = "anyMatch";

	// Stream exists since 8
	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_8;
	}

	@Override
	public String getId() {
		return "StreamAnyMatch";
	}

	@SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
	@Override
	protected boolean processNotRecursively(Node node) {
		LOGGER.debug("{}", PepperLogHelper.getObjectAndClass(node));
		if (!(node instanceof MethodCallExpr)) {
			return false;
		}
		MethodCallExpr methodCall = (MethodCallExpr) node;
		String methodCallIdentifier = methodCall.getName().getIdentifier();
		if (!METHOD_IS_PRESENT.equals(methodCallIdentifier) && !METHOD_IS_EMPTY.equals(methodCallIdentifier)) {
			return false;
		}

		Optional<Expression> optScope = methodCall.getScope();
		if (optScope.isEmpty()) {
			return false;
		}
		Expression scope = optScope.get();
		if (!(scope instanceof MethodCallExpr)) {
			return false;
		}
		MethodCallExpr scopeAsMethodCallExpr = (MethodCallExpr) scope;
		if (!METHOD_FIND_ANY.equals(scopeAsMethodCallExpr.getName().getIdentifier())) {
			return false;
		}

		Optional<Expression> optParentScope = scopeAsMethodCallExpr.getScope();
		if (optParentScope.isEmpty()) {
			return false;
		}
		Expression parentScope = optParentScope.get();
		if (!parentScope.isMethodCallExpr()) {
			return false;
		}
		MethodCallExpr parentScopeAsMethodCallExpr = (MethodCallExpr) parentScope;
		if (!METHOD_FILTER.equals(parentScopeAsMethodCallExpr.getName().getIdentifier())) {
			return false;
		}

		Optional<Expression> optGrandParentScope = parentScopeAsMethodCallExpr.getScope();
		if (optGrandParentScope.isEmpty()) {
			return false;
		}
		Expression grandParentScope = optGrandParentScope.get();

		Expression filterPredicate = parentScopeAsMethodCallExpr.getArgument(0);

		boolean localTransformed = false;
		NodeList<Expression> replaceArguments = new NodeList<>(filterPredicate);
		Expression replacement = new MethodCallExpr(grandParentScope, METHOD_ANY_MATCH, replaceArguments);

		if (METHOD_IS_EMPTY.equals(methodCallIdentifier)) {
			replacement = new UnaryExpr(replacement, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
		}
		LOGGER.info("Turning {} into {}", methodCall, replacement);
		if (methodCall.replace(replacement)) {
			localTransformed = true;
		}

		if (localTransformed) {
			return true;
		} else {
			return false;
		}
	}
}