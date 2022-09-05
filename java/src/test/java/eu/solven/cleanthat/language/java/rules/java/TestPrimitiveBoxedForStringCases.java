package eu.solven.cleanthat.language.java.rules.java;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import eu.solven.cleanthat.language.java.rules.cases.do_not_format_me.PrimitiveBoxedForStringCases;
import eu.solven.cleanthat.language.java.rules.test.ATestCases;

public class TestPrimitiveBoxedForStringCases extends ATestCases {

	@Test
	public void testCases() throws IOException {
		testCasesIn(new PrimitiveBoxedForStringCases());
	}
}
