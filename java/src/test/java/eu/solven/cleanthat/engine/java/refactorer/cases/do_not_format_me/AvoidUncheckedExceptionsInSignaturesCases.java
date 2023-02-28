package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import java.io.IOException;

import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedMethod;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.AvoidUncheckedExceptionsInSignatures;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

public class AvoidUncheckedExceptionsInSignaturesCases extends AJavaparserRefactorerCases {
	@Override
	public IJavaparserMutator getTransformer() {
		return new AvoidUncheckedExceptionsInSignatures();
	}

	@CompareMethods
	public static class CaseRuntimeException {
		public void pre() throws RuntimeException {
		}

		public void post() {
		}
	}

	@CompareMethods
	public static class CaseInterlaced {
		public void pre() throws IOException, RuntimeException, ReflectiveOperationException, IllegalArgumentException {
		}

		public void post() throws IOException, ReflectiveOperationException {
		}
	}

	@CompareMethods
	public static class CaseIllegalArgumentException {
		public void pre() throws IllegalArgumentException {
		}

		public void post() {
		}
	}

	// Deep in hierarchy
	@CompareMethods
	public static class CaseArrayIndexOutOfBoundsException {
		public void pre() throws ArrayIndexOutOfBoundsException {
		}

		public void post() {
		}
	}

	@UnmodifiedMethod
	public static class CaseException {
		public void pre() throws Exception {
		}
	}

	@UnmodifiedMethod
	public static class CaseIOException {
		public void pre() throws IOException {
		}
	}

	@UnmodifiedMethod
	public static class CaseError {
		public void pre() throws Error {
		}
	}

	@UnmodifiedMethod
	public static class CaseThrowable {
		public void pre() throws Throwable {
		}
	}
}
