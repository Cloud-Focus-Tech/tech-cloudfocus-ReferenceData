# Development and verification

## Toolchain and commands

The project declares Java 25, Spring Boot 4.1.2, and a Maven wrapper distribution of 3.10.0. A compatible JDK must be available through `JAVA_HOME`/`PATH`. Initial wrapper and dependency resolution require access to the configured artifact repositories or an existing cache.

Run from the repository root on Windows PowerShell:

```powershell
java -version
.\mvnw.cmd --version
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The run command starts the service and stays attached to the terminal. Stop it before continuing with packaging, or use another terminal:

```powershell
.\mvnw.cmd package
java -jar target/ReferenceData-0.0.1-SNAPSHOT.jar
```

For a POSIX shell, use `./mvnw` in place of `.\mvnw.cmd`. An installed `mvn` can also run these goals, but its version may differ from the wrapper. Use the [API manual checks](api.md#manual-checks-after-starting-the-application) after startup.

The POM declares GraalVM native build tooling. Native-image and container builds were not verified during this review. They require their own GraalVM/native-image or Docker prerequisites; plugin presence alone does not prove that packaging works.

## Tests and verification status

The only tracked test is [ReferenceDataApplicationTests](../src/test/java/tech/cloudfocus/ReferenceData/ReferenceDataApplicationTests.java). Its `@SpringBootTest` method, `contextLoads()`, has no assertions and checks that the application context can start. It does not verify endpoint responses, collection counts, JSON parsing, missing IDs, or authorization.

During the documentation review on 2026-11-13:

| Check | Result |
| --- | --- |
| Read all five main Java files, the test, POM, properties, and wrapper configuration | Completed |
| Parse `data/index.json` and all 28 dataset files | Completed; all parsed successfully |
| Resolve each index `blob` relative to `data/` | All seven files present |
| Compare four hard-coded counts to the corresponding dataset sizes | All four matched |
| Inspect workflow `hasSortOrder` entries | All records in the three workflow files contain `sortOrder` |
| Run installed Maven with `mvn -o test` | Stopped before compilation: Spring Boot parent POM 4.1.2 was absent from the accessible local cache |
| Run the application, HTTP smoke checks, native builds, or container builds | Not performed |

The Maven failure is an environment/dependency-resolution limitation, not evidence of a Java compilation or test failure. Existing ignored `target/` reports show an earlier passing context test, but those artifacts are not verification of the current working tree. A fresh build/test remains necessary in an environment with dependencies available.

## Working on the project

- Read [docs/README.md](README.md) first and inspect current source before relying on these notes.
- Check `git status` before editing and preserve unrelated working-tree changes. A controller change already existed during this review; this documentation task did not edit it.
- Keep package case consistent: `tech.cloudfocus.ReferenceData` contains uppercase `ReferenceData`.
- The existing controller class/file is named `collections`. Renaming it is a separate code change, not something required by these docs.
- When changing routes or response bodies, update [api.md](api.md). For functional changes, add tests that exercise the changed behavior; the context-load test alone does not cover the contract.
- When changing dataset contents, validate JSON, catalog references, and relevant counts, then update [data-catalog.md](data-catalog.md). Dataset changes currently have no effect on API output.
- When adding data loading, consider packaging and startup failure behavior explicitly. A successful readiness response currently says nothing about dataset availability.
- Document any newly implemented authentication, deployment, configuration, or storage behavior instead of treating catalog fields or generated guidance as existing features.

There are no tracked CI checks or deployment definitions to run in this checkout. Avoid using ignored `target/` experiments or generated `HELP.md` as the authoritative design.
