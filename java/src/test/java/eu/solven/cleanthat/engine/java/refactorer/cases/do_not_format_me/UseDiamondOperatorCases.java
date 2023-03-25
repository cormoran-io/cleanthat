package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedMethod;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.UseDiamondOperator;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

public class UseDiamondOperatorCases extends AJavaparserRefactorerCases {
	@Override
	public IJavaparserMutator getTransformer() {
		return new UseDiamondOperator();
	}

	@CompareMethods
	public static class CaseCollection {
		public Map<String, List<String>> pre() {
			return new HashMap<String, List<String>>();
		}

		public Map<String, List<String>> post() {
			return new HashMap<>();
		}
	}

	// https://youtrack.jetbrains.com/issue/IDEA-67125
	@UnmodifiedMethod
	public static class CaseAnonymousClass {

		public Map<String, List<String>> post() {
			return new HashMap<String, List<String>>() {
				private static final long serialVersionUID = 1L;

				{
					this.put("k", List.of());
				}
			};
		}
	}

}
