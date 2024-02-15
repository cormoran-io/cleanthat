package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Ignore;

import eu.solven.cleanthat.engine.java.refactorer.annotations.CaseNotYetImplemented;
import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedMethod;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserAstMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.StreamWrappedVariableToMap;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

public class TestStreamWrappedVariableToMapCases extends AJavaparserRefactorerCases {

	@Override
	public IJavaparserAstMutator getTransformer() {
		return new StreamWrappedVariableToMap();
	}

	@CompareMethods
	public static class IntStream_forEach_intermediateInt {
		public void pre(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).forEach(j -> {
				int c = s.charAt(j);
				sb.append(c);
				if (c == '\'')
					sb.append(c);
			});
		}

		public void post(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).map(j -> s.charAt(j)).forEach(c -> {
				sb.append(c);
				if (c == '\'')
					sb.append(c);
			});
		}
	}

	// This case is skipped as `char` has not dedicated Stream
	@UnmodifiedMethod
	public static class IntStream_intermediateChar {
		public void pre(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).forEach(j -> {
				char c = s.charAt(j);
				sb.append(c);
				if (c == '\'')
					sb.append(c);
			});
		}
	}

	@CompareMethods
	public static class IntStream_forEach_intermediateObject {
		public void pre(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).forEach(j -> {
				Object o = "A".repeat(j);
				sb.append(o);
			});
		}

		public void post(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).mapToObj(j -> "A".repeat(j)).forEach(o -> {
				sb.append(o);
			});
		}
	}

	@CompareMethods
	public static class Stream_splittableMap {
		public List<String> pre(List<String> strings) {
			return strings.stream().map(s -> {
				String withoutLead = s.substring(1);

				return withoutLead + "Suffix";
			}).collect(Collectors.toList());
		}

		public List<String> post(List<String> strings) {
			return strings.stream().map(s -> s.substring(1)).map(withoutLead -> {
				return withoutLead + "Suffix";
			}).collect(Collectors.toList());
		}
	}

	@CompareMethods
	public static class Stream_changeType {
		public int pre(List<String> strings) {
			return strings.stream().mapToInt(s -> {
				int length = s.length();

				return Math.max(length, 5);
			}).sum();
		}

		public int post(List<String> strings) {
			return strings.stream().mapToInt(s -> s.length()).map(length -> {
				return Math.max(length, 5);
			}).sum();
		}
	}

	@CompareMethods
	public static class isEnclosing {
		public Set<String> pre(Set<String> classNames) {
			return classNames.stream().map((className) -> {
				String name = className.substring(className.lastIndexOf('.') + 1);
				return name;
			}).collect(Collectors.toCollection(TreeSet::new));
		}

		public Set<String> post(Set<String> classNames) {
			return classNames.stream()
					.map(className -> className.substring(className.lastIndexOf('.') + 1))
					.map((name) -> {
						return name;
					})
					.collect(Collectors.toCollection(TreeSet::new));
		}
	}

	@UnmodifiedMethod
	public static class variableGenerates2Intermediates {
		public Set<List<String>> pre(Set<String> classNames) {
			return classNames.stream().map((className) -> {
				String path = className.replace('.', '/');
				String name = className.substring(className.lastIndexOf('.') + 1);
				return Arrays.asList(name, path);
			}).collect(Collectors.toCollection(HashSet::new));
		}
	}

	@UnmodifiedMethod
	public static class variableIsIdentity {
		public void pre(String s, StringBuilder sb) {
			IntStream.range(0, s.length()).forEach(j -> {
				int c = j;
				sb.append(c);
			});
		}
	}

	@UnmodifiedMethod
	public static class IssueIdempotency {
		private char anonymize(Character c) {
			return c;
		}

		public void pre(String input, StringBuilder output) {
			input.chars().mapToObj(rawChar -> (char) rawChar).map(c -> anonymize(c)).forEach(anonymizedChar -> {
				output.append(anonymizedChar);
			});
		}
	}

	@Ignore("We need to restore generics in the .map")
	@CompareMethods
	@CaseNotYetImplemented
	public static class IssueWithGenerics {
		List<? extends Map<String, ?>> pre(Map<String, ?> properties, List<Map<String, ?>> documentFields) {
			return documentFields.stream().map(m -> {
				Map<String, Object> enriched = new LinkedHashMap<>(m);

				enriched.putAll(properties);

				return enriched;
			}).collect(Collectors.toList());
		}

		List<? extends Map<String, ?>> post(Map<String, ?> properties, List<Map<String, ?>> documentFields) {
			return documentFields.stream().map(m -> new LinkedHashMap<String, Object>(m)).map(enriched -> {
				enriched.putAll(properties);
				return enriched;
			}).collect(Collectors.toList());
		}
	}

}
