package eu.solven.cleanthat.language.xml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import eu.solven.cleanthat.formatter.ILintFixer;
import eu.solven.cleanthat.formatter.LineEnding;
import eu.solven.cleanthat.language.SourceCodeProperties;
import eu.solven.cleanthat.language.xml.revelc.RevelcXmlFormatter;
import eu.solven.cleanthat.language.xml.revelc.RevelcXmlFormatterProperties;

public class TestRevelcXmlFormatter {
	final ILintFixer formatter = new RevelcXmlFormatter(new SourceCodeProperties(), new RevelcXmlFormatterProperties());

	@Test
	public void testFormatNote_lf() throws IOException {
		String expectedXml = StreamUtils.copyToString(new ClassPathResource("/xml/note.xml").getInputStream(),
				StandardCharsets.UTF_8);

		String formatted = formatter.doFormat(expectedXml, LineEnding.LF);

		// TODO Investigate why it is not expected EOL by system.eol which is applied
		Assertions.assertThat(formatted.split(LineEnding.LF.getChars()))
				.hasSize(8)
				.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
				.contains("<!-- view-source:https://www.w3schools.com/xml/note.xml -->");
	}

	@Test
	public void testFormatNote_crlf() throws IOException {
		// TODO This tests is OK only under Windows
		Assume.assumeTrue(System.lineSeparator().equals("\r\n"));

		String expectedXml = StreamUtils.copyToString(new ClassPathResource("/xml/note.xml").getInputStream(),
				StandardCharsets.UTF_8);

		String formatted = formatter.doFormat(expectedXml, LineEnding.CRLF);

		// TODO Investigate why it is not expected EOL by system.eol which is applied
		Assertions.assertThat(formatted.split(LineEnding.CRLF.getChars()))
				.hasSize(8)
				.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
				.contains("<!-- view-source:https://www.w3schools.com/xml/note.xml -->");
	}
}