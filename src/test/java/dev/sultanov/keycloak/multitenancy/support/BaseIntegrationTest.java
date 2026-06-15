package dev.sultanov.keycloak.multitenancy.support;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class BaseIntegrationTest {

    private static final Integer MAILHOG_HTTP_PORT = 8025;

    private static final Network network = Network.newNetwork();
    private static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.6.3")
            .withRealmImportFiles("/realm-export.json", "/idp-realm-export.json")
            .withProviderClassesFrom("target/classes")
            .withProviderLibsFrom(getProviderLibs())
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withEnv("KC_LOGLEVEL", "DEBUG")
            .withAccessToHost(true);

    private static final GenericContainer<?> mailhog = new GenericContainer<>("mailhog/mailhog")
            .withExposedPorts(MAILHOG_HTTP_PORT)
            .waitingFor(Wait.forHttp("/"))
            .withNetwork(network)
            .withNetworkAliases("mailhog")
            .withAccessToHost(true);

    private static Client client;
    private static Playwright playwright;
    private static java.io.FileOutputStream keycloakLogStream;

    @BeforeAll
    static void beforeAll() {
        keycloak.start();
        try {
            java.io.File logFile = new java.io.File("target/keycloak.log");
            logFile.getParentFile().mkdirs();
            // Long-lived: stays open for the async followOutput callback; closed in afterAll().
            keycloakLogStream = new java.io.FileOutputStream(logFile);
            keycloak.followOutput(outFrame -> {
                try {
                    keycloakLogStream.write(outFrame.getBytes());
                    keycloakLogStream.flush();
                } catch (Exception e) {
                    System.err.println("Failed to write Keycloak log output: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to set up Keycloak log capture: " + e.getMessage());
        }
        mailhog.start();

        client = ClientBuilder.newClient();
        playwright = Playwright.create();
        var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        var keycloakUrl = keycloak.getAuthServerUrl();
        var mailhogUrl = "http://%s:%d/".formatted(mailhog.getHost(), mailhog.getMappedPort(MAILHOG_HTTP_PORT));

        IntegrationTestContextHolder.setContext(new IntegrationTestContext(client, browser, keycloakUrl, mailhogUrl));
    }

    @AfterAll
    static void afterAll() {
        client.close();
        playwright.close();
        if (keycloakLogStream != null) {
            try {
                keycloakLogStream.close();
            } catch (Exception e) {
                System.err.println("Failed to close Keycloak log stream: " + e.getMessage());
            }
        }
        IntegrationTestContextHolder.clearContext();
    }

    private static java.util.List<java.io.File> getProviderLibs() {
        java.io.File dependencyDir = new java.io.File("target/dependency");
        java.io.File[] files = dependencyDir.isDirectory()
                ? dependencyDir.listFiles((dir, name) -> name.endsWith(".jar"))
                : null;
        if (files == null || files.length == 0) {
            throw new IllegalStateException(
                    "No provider libraries found in target/dependency — the extension's runtime "
                            + "dependencies (e.g. brave/zipkin tracing) would not be deployed to the "
                            + "Keycloak container, causing NoClassDefFoundError at runtime. Run the "
                            + "Maven build through the 'process-test-resources' phase (e.g. 'mvn verify') "
                            + "so maven-dependency-plugin copies dependencies before the tests run.");
        }
        var libs = new java.util.ArrayList<>(java.util.Arrays.asList(files));
        findThemeJar().ifPresent(libs::add);
        return libs;
    }

    // Locates the custom KC theme JAR from the adjacent theme repo so render tests
    // can verify templates against the real FTL. Silently absent → theme unavailable.
    private static java.util.Optional<java.io.File> findThemeJar() {
        java.io.File themeTargetDir = new java.io.File(
                System.getProperty("user.home") + "/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme/target");
        if (!themeTargetDir.isDirectory()) {
            return java.util.Optional.empty();
        }
        java.io.File[] jars = themeTargetDir.listFiles((dir, name) ->
                name.startsWith("azguards-keycloak-custom-theme") && name.endsWith(".jar")
                        && !name.contains("sources") && !name.contains("javadoc"));
        if (jars == null || jars.length == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(jars[0]);
    }
}
