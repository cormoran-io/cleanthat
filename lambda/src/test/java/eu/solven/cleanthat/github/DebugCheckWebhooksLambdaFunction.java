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
package eu.solven.cleanthat.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.solven.cleanthat.lambda.step0_checkwebhook.CheckWebhooksLambdaFunction;
import eu.solven.cleanthat.lambda.step1_checkconfiguration.CheckConfigWebhooksLambdaFunction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;

/**
 * Equivalent of {@link CheckWebhooksLambdaFunction}, but in the test classpath, which has
 * spring-cloud-function-starter-web in addition
 *
 * @author Benoit Lacelle
 */
public class DebugCheckWebhooksLambdaFunction extends CheckConfigWebhooksLambdaFunction implements CommandLineRunner {

	@Autowired
	ApplicationContext appContext;

	public static void main(String[] args) {
		SpringApplication.run(DebugCheckWebhooksLambdaFunction.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		Function<Map<String, ?>, ?> function = appContext.getBean("ingressRawWebhook", Function.class);

		Map<String, ?> githubEvent = new ObjectMapper().readValue(
				new ClassPathResource("/github/webhook/pr_open-open_event.json").getInputStream(),
				Map.class);

		Map<String, Object> inputAsMap = new LinkedHashMap<>();
		inputAsMap.put("body", Map.of("github", Map.of("body", githubEvent, "headers", Map.of())));
		inputAsMap.put("headers", Map.of());

		function.apply(inputAsMap);
	}
}
