CleanThat

# Purpose

Cleanthat is a project enabling automatic code cleaning, from formatting to refactoring.

Beware CleanThat currently refers to 2 sub-projects:

1. `Cleanthat Refactorer`: A library enabling Java linting rules/mutators
2. `Cleanthat HouseKeeper`: A GitHub App enabling automatic house-keeping of your `Git` repository, for various languages, given external linting engines (e.g. `Spotless`, `OpenRewrite`, `Cleanthat Refactorer`, etc)

# List of mutators

[![javadoc](https://javadoc.io/badge2/io.github.solven-eu.cleanthat/java/javadoc.svg)](https://javadoc.io/doc/io.github.solven-eu.cleanthat/java)

[MUTATORS.generated.MD](MUTATORS.generated.MD)

# Motivation

The point of this project is to enable **automatic cleaning of your code-base**. By cleaning, we include :

1. formatting (with an Eclipse stylesheet, Google-Java-Format, etc)
2. linting (given any [IMutator](MUTATORS.generated.MD))
3. refactoring (WIP)
4. migrating (from JUnit4 to JUnit5)

As of 2022-12, it focuses on Java projects, but enabling formatting various languages through Spotless.

Related projects:

- https://jsparrow.github.io/ (paid)
- https://github.com/JnRouvignac/AutoRefactor (free)
- https://github.com/openrewrite/rewrite-migrate-java (free)
- https://www.moderne.io/ (paid)
- https://eslint.org/docs/latest/extend/custom-formatters (free)
- https://errorprone.info/docs/refaster (free)
- https://github.com/walkmod/walkmod-pmd-plugin (free)
- https://github.com/XenoAmess/remove-unused-imports-maven-plugin (free, focused on imports)
- https://github.com/SpoonLabs/sorald (free)
- https://github.com/redhat-developer/vscode-java/blob/master/document/_java.learnMoreAboutCleanUps.md (free)
- [Academic papers](https://github.com/SpoonLabs/sorald/issues/82)
- https://github.com/detekt/detekt (free)

# Changes

See [CHANGES.MD](CHANGES.MD)

- 2023-03-23: 2.12 Latest release
- ...
- 2023-02-06: 2.0 Major Release leveraging from/to [Spotless](https://github.com/diffplug/spotless)
- ...
- 2021-08-12: 1.0 Initial Release

# Language Coverage

## As a Robot (`Cleanthat HouseKeeper`)

`Cleanthat HouseKeeper` is currently compatible with the following languages:

- java (Spotless and OpenRewrite)
- pom.xml (Spotless)
- json (Spotless)
- xml (Spotless)
- yaml (Spotless)
- kotlin (Spotless)

see [FormatterFactory](spotless/src/main/java/eu/solven/cleanthat/spotless/FormatterFactory.java)

## As a library (`Cleanthat Refactorer`)

`Cleanthat Refactorer` is currently compatible with the following languages:

- java (Cleanthat)
- java (OpenRewrite)
- java (Eclipse Cleanup)

# Limitations

- CleanThat processes files individually, which indices limited `Type` resolution. This enables cleaning files on a per-impacted-file basis (e.g. in a Github Pull-Request).
- The type resolution may be lifted through `cleanthat-maven-plugin` (TODO)
- The project jhas poor support of charset others than `UTF-8`

# Installation

## Maven (Free)

### Spotless `mvn` plugin (for pure `mvn` users)

`mvn` integration is available through Spotless: https://github.com/diffplug/spotless/tree/main/plugin-maven#cleanthat

[![Maven central](https://img.shields.io/badge/mavencentral-com.diffplug.spotless%3Aspotless--maven--plugin-blue.svg)](https://mvnrepository.com/artifact/com.diffplug.spotless/spotless-maven-plugin)

One can then clean its codebase with `mvn spotless:apply`

### Cleanthat `mvn` plugin (for GitHub App users)

See README: https://github.com/solven-eu/cleanthat/tree/master/maven

![Maven Central](https://img.shields.io/maven-central/v/io.github.solven-eu.cleanthat/java)(https://mvnrepository.com/artifact/io.github.solven-eu.cleanthat/cleanthat-maven-plugin)

Features :

- Apply full refactoring as it would be executed by the Github App, based on `.cleanthat` configuration
- Generate automatically an optimal Eclipse stylesheet based on your code-case

One liner (even without a `pom.xml`):

        mvn io.github.solven-eu.cleanthat:cleanthat-maven-plugin:cleanthat

or simply `mvn cleanthat:apply`

## Gradle (Free)

`gradle` integration is available through Spotless: https://github.com/diffplug/spotless/tree/main/plugin-gradle#cleanthat

[![Gradle plugin](https://img.shields.io/badge/plugins.gradle.org-com.diffplug.spotless-blue.svg)](https://plugins.gradle.org/plugin/com.diffplug.spotless)

## Github App (Free+Paid)

If your repository is hosted by Github.com, get zero-configuration cleaning with our [Github App](https://github.com/marketplace/cleanthat/) on Github marketplace.

It can configured through a `/.cleanthat/cleanthat.yaml` file at the root of the repository (e.g. [https://github.com/solven-eu/cleanthat/blob/master/.cleanthat/cleanthat.yaml](https://github.com/solven-eu/cleanthat/blob/master/.cleanthat/cleanthat.yaml)).

It differs from mvn/gradle integration by fetching only relevant (e.g. modified) files, based on Github Events.

- https://docs.github.com/en/developers/webhooks-and-events/events/github-event-types#pullrequestevent
- https://docs.github.com/en/developers/webhooks-and-events/events/github-event-types#pushevent

### Key Features

- Automatic generation of the initial configuration
- Clean branches which are not protected and head of a PullRequest

### Example configurations of happy Users:

- [Cleanthat](https://github.com/solven-eu/cleanthat/tree/master/.cleanthat) itself
- [Pepper](https://github.com/solven-eu/pepper/tree/master/.cleanthat)

## CI/CD (Github Actions, CircleCI, Jenkins, etc)

If you integrated Cleanthat through its maven or gradle options, you can get automatic remote cleaning with:

        ./mvnw spotless:check || ./mvnw spotless:apply && git commit -m"Spotless" && git push

# Configuration

## Default configuration: [SafeAndConsensualMutators](java/src/main/java/eu/solven/cleanthat/engine/java/refactorer/mutators/composite/SafeAndConsensualMutators.java)

By default, we rely on a safe and consensual configuration. This composite mutators should be integrated by all projects as most projects would benefit from these rules, and most developers would agree this is better style.

Spotless:

```
cleanthat()
	[...]
	.addMutator('SafeAndConsensual')
```

## Customizations

Most integrations enable:

1. Adding some mutators
2. Excluding some mutators
3. Activating `isDraft` mutators (which is `false` by default, not to include not-production-ready mutators)

Spotless:

```
cleanthat()
	[...]
	.addMutator('SafeAndConsensual')
	.includeMutator('StreamForEachNestingForLoopToFlatMap')
	.excludeMutator('UseCollectionIsEmpty')
	.includeDraft(true) 
```

## More composite mutators

### [SafeButNotConsensualMutators](java/src/main/java/eu/solven/cleanthat/engine/java/refactorer/mutators/composite/SafeButNotConsensualMutators.java)

[SafeButNotConsensualMutators](java/src/main/java/eu/solven/cleanthat/engine/java/refactorer/mutators/composite/SafeButNotConsensualMutators.java) relates to SafeAndConsensualMutators, but it includes also some rules which may be rejected by a **minority** of developers.

Spotless:

```
cleanthat()
	[...]
	.addMutator('SafeAndConsensual')
	.addMutator('SafeButNotConsensual')
```

### [SafeButControversialMutators](java/src/main/java/eu/solven/cleanthat/engine/java/refactorer/mutators/composite/SafeButControversialMutators.java)

[SafeButControversialMutators](java/src/main/java/eu/solven/cleanthat/engine/java/refactorer/mutators/composite/SafeButControversialMutators.java) relates to SafeButNotConsensualMutators, but it includes also some rules which may be rejected by a **majority** of developers. This may be due to eager use of new language syntax.

Spotless:

```
cleanthat()
	[...]
	.addMutator('SafeAndConsensual')
	.addMutator('SafeButNotConsensual')
	.addMutator('SafeButControversial')
```

### Activate **all** mutators

One may switch to activate all mutators. This can be achieved through:

Spotless:

```
cleanthat()
	[...]
	.addMutator('SafeAndConsensual')
	.addMutator('SafeButNotConsensual')
	.addMutator('SafeButControversial')
	.includeDraft(true) 
```

# Key design decisions

As of 2022-12, this projects focuses on typical JVM projects. Hence, it enables:

- Advanced Linting of .java files
- Advanced Formatting of .java files (through Spotless)
- Advanced Formatting of pom.xml files (through Spotless)
- Basic Formatting of .json, .xml, etc files (through Spotless)

## Refactoring on a `per-single-source file` basis

One major goal of this project is to enable incremental refactoring on a per Pull-Request basis. Hence, the availability of the whole code-base and related binaries (e.g. `mvn` dependencies) is limited. Cleanthat focuses on cleaning individual source files.

Limitations :

- Can not refactor based on multiple source files information (e.g. type definition in a different file)
- Can not refactor based on binaries information (e.g. type definition from dependencies)
- Can refactor based on standard JRE classpath

## Github App does not rely on Maven and Gradle

While we work on integrating CleanThat into Spotless, the Github CleanThat App does not rely on existing Maven (https://github.com/diffplug/spotless/tree/main/plugin-maven) and Gradle (https://github.com/diffplug/spotless/tree/main/plugin-gradle) plugins. The main reason for that is security. Indeed, while it would enable very setup over a project already integrating Spotless, it would open dangerous security breach as one could easily inject custom code as dependency of the maven/gradle plugin, which would enable one to extract CleanThat secrets (Github token, GPG key, etc).

## About Advanced Formatting of .java files

Refactoring .java files would break the code conventions. Hence, any refactoring operation should be followed by a formatting operation.
With mvn integration, once should follow the cleanthat step with some mvn formatter step.
With github integration, once may rely on CleanThat own .java formatting abilities.

There is multiple good options for formatting Java files:

- https://github.com/revelc/formatter-maven-plugin
- https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md (e.g. https://github.com/JnRouvignac/AutoRefactor/blob/master/pom.xml#L240)

# Last considerations

This software is provided WITHOUT ANY WARRANTY, and is available under the Apache License, Version 2. Any code loss caused by using this plugin is not the responsibility of the author(s). Be sure to use some source repository management system such as GIT before using this plugin.

Contributions are welcome.

# Using Eclipse Formatter

An alternative to spring_formatter is eclipse_formatter. It takes as parameter an url like:

A public http(s) URL:

        https://raw.githubusercontent.com/solven-eu/pepper/master/static/src/main/resources/eclipse/eclipse_java_code_formatter.xml

A file in local repository (root being assumed based on Git root/Maven top module)

        code:/static/src/main/resources/eclipse/eclipse_java_code_formatter.xml

Eclipse Stylesheets can exported to XML through:

- Go into Preferences
- Select Java > Code Style > Formatter
- Click on 'Export All'

CleanThat will accept only configuration with a single profile in them. If multiple profiles are found, we will rely on the first profile.

## Automatic generation of Eclipse Stylesheet

The maven plugin enables generating an Eclipse Stylesheet minimizing changes over a clean repository:

        mvn io.github.solven-eu.cleanthat:cleanthat-maven-plugin:eclipse_formatter-stylesheet

see maven/README.MD

# Disclaimer

`cleanthat` is fairly new. While it is tested on many large projects, you may encounter issues of all kinds. [**Please report them**](https://github.com/solven-eu/cleanthat/issues).

[![codecov](https://codecov.io/github/solven-eu/cleanthat/branch/master/graph/badge.svg?token=4K4O5GNH32)](https://codecov.io/github/solven-eu/cleanthat)

# Contributors

- Thanks[Mark Chesney](https://github.com/mches) for reporting and fixing https://github.com/solven-eu/cleanthat/issues/842 and https://github.com/solven-eu/cleanthat/issues/843

