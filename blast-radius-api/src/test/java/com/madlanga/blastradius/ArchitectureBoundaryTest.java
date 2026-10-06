package com.madlanga.blastradius;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks the source boundaries without adding a runtime architecture library. */
class ArchitectureBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/madlanga/blastradius");
    private static final String BASE = "com.madlanga.blastradius";
    private static final Set<String> CAPABILITIES =
            Set.of("incident", "topology", "telemetry", "diagnosis", "lifecycle", "shared");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package ([\\w.]+);");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import (?:static )?([\\w.*]+);");

    @Test
    void sourcePackagesMatchTheirCapabilityAndDirectory() throws IOException {
        assertThat(ROOT).exists();
        for (Path file : sources()) {
            String source = Files.readString(file);
            var declaration = PACKAGE.matcher(source);
            assertThat(declaration.find()).as("Package declaration in %s", file).isTrue();
            String packageName = declaration.group(1);
            String directory = ROOT.relativize(file.getParent()).toString().replace('/', '.').replace('\\', '.');
            assertThat(packageName).as("Package path of %s", file)
                    .isEqualTo(directory.isEmpty() ? BASE : BASE + "." + directory);
            if (!directory.isEmpty()) {
                assertThat(CAPABILITIES).as("Capability of %s", file).contains(directory.split("\\.")[0]);
            }
        }
    }

    @Test
    void domainDependsOnlyOnJavaAndDomainConcepts() throws IOException {
        for (Path file : sources()) {
            if (!file.toString().replace('\\', '/').contains("/domain/")) continue;
            var imports = IMPORT.matcher(Files.readString(file));
            while (imports.find()) {
                String dependency = imports.group(1);
                assertThat(dependency.startsWith("java.")
                        || dependency.matches("com\\.madlanga\\.blastradius\\.[^.]+\\.domain\\..+"))
                        .as("Domain dependency %s in %s", dependency, file).isTrue();
            }
        }
    }

    @Test
    void applicationAndApiDoNotImportInfrastructureImplementations() throws IOException {
        for (Path file : sources()) {
            String path = file.toString().replace('\\', '/');
            if (!path.contains("/application/") && !path.contains("/api/")) continue;
            var imports = IMPORT.matcher(Files.readString(file));
            while (imports.find()) {
                String dependency = imports.group(1);
                assertThat(dependency).as("Layer dependency in %s", file)
                        .doesNotContain(".infrastructure.")
                        .doesNotStartWith("java.sql.")
                        .doesNotStartWith("javax.sql.")
                        .doesNotStartWith("org.springframework.jdbc.");
                if (path.contains("/application/")) {
                    assertThat(dependency).as("Application dependency in %s", file)
                            .doesNotContain(".api.")
                            .doesNotStartWith("org.springframework.web.");
                }
            }
        }
    }

    private List<Path> sources() throws IOException {
        try (var paths = Files.walk(ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
