package com.smart.erp.spike.s2.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.json.JsonWriter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Keycloak 26.8 per test run with the erp realm (doc §4.4, ADR-0005, ADR-0029). The realm file holds clients only;
 * users, organizations, memberships and passwords are set up here through the Admin API, the way the provisioning
 * adapter will (Phase 2). No secret is a literal (K9): the admin password is generated per run, user passwords are
 * random and client secrets are read back from Keycloak.
 */
public final class SpikeKeycloak {

    public static final String IMAGE = "quay.io/keycloak/keycloak:26.8.0";

    private static final String REALM = "erp";
    private static final String REALM_FILE = "keycloak/erp-realm.json";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();

    /** Test users and their organizations (plan: test users and tenants). */
    private static final Map<String, List<String>> MEMBERS = members();

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonParser JSON = JsonParserFactory.getJsonParser();
    private static final Map<String, String> PASSWORDS = new ConcurrentHashMap<>();
    private static final Map<String, String> SECRETS = new ConcurrentHashMap<>();

    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USERNAME)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD) // UUID per run (K9)
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withCopyToContainer(
                    MountableFile.forClasspathResource(REALM_FILE), "/opt/keycloak/data/import/erp-realm.json")
            .withExposedPorts(8080, 9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
        allowTwoLetterUsernames();
        createOrganizationsWithMembers(createUsers());
        offerOrganizationScopeToWebClient();
        for (String clientId : realmClientIds()) {
            clientSecret(clientId);
        }
    }

    private SpikeKeycloak() {}

    public record Tokens(String idToken, String accessToken) {}

    /** The realm's issuer as the application and the test browser both reach it. */
    public static String issuer() {
        return baseUrl() + "/realms/" + REALM;
    }

    /** What an installer would write into the application's configuration. */
    public static Map<String, String> applicationProperties() {
        return Map.of(
                "erp.identity.issuer-uri", issuer(),
                "erp.identity.web-client-secret", clientSecret("erp-web"));
    }

    public static String password(String username) {
        String password = PASSWORDS.get(username);
        if (password == null) {
            throw new IllegalArgumentException("No such test user: " + username);
        }
        return password;
    }

    /** The confidential client's secret as Keycloak generated it; read once, then cached. */
    public static String clientSecret(String clientId) {
        return SECRETS.computeIfAbsent(clientId, id -> {
            Map<String, Object> secret =
                    JSON.parseMap(admin("GET", "/clients/" + clientUuid(id) + "/client-secret", null)
                            .body());
            Object value = secret.get("value");
            if (!(value instanceof String text) || text.isEmpty()) {
                throw new IllegalStateException("Keycloak holds no secret for " + id + ": " + secret);
            }
            return text;
        });
    }

    /** An authorization request with a random state; callers append PKCE and nonce when they need them. */
    public static String authorizationUrl(String clientId, String redirectUri, String scope) {
        return issuer() + "/protocol/openid-connect/auth?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + "&state=" + randomUrlSafe(16);
    }

    /** Logs {@code username} in on erp-web with {@code scope} and redeems the code: tokens without the application. */
    public static Tokens codeFlow(String username, String scope) {
        String redirectUri =
                "https://acme.erp.test/login/oauth2/code/keycloak"; // registered on erp-web; never followed
        String verifier = randomUrlSafe(32);
        String url = authorizationUrl("erp-web", redirectUri, scope) + "&code_challenge=" + s256(verifier)
                + "&code_challenge_method=S256&nonce=" + randomUrlSafe(16);
        SpikeBrowser browser = new SpikeBrowser(0);
        SpikeBrowser.Page callback = browser.follow(browser.get(url, "Accept", "text/html"), username, redirectUri);
        String code = callback.location()
                .filter(location -> location.startsWith(redirectUri))
                .map(location -> SpikeBrowser.query(location).getFirst("code"))
                // Keycloak refused before the callback (an error page, not a token) or sent an error to it
                // (?error=invalid_scope): say what it showed, it is a finding.
                .orElseThrow(() -> new IllegalStateException("Keycloak did not reach the callback for " + username
                        + " / " + scope + ": " + callback.status() + " " + callback.url() + " -> "
                        + callback.location().orElse("(no redirect)") + " "
                        + callback.body()
                                .substring(0, Math.min(500, callback.body().length()))));
        Map<String, Object> tokens = tokenRequest(Map.of(
                "grant_type",
                "authorization_code",
                "code",
                code,
                "redirect_uri",
                redirectUri,
                "code_verifier",
                verifier,
                "client_id",
                "erp-web",
                "client_secret",
                clientSecret("erp-web")));
        return new Tokens((String) tokens.get("id_token"), (String) tokens.get("access_token"));
    }

    /** A service account's access token (the integration clients of Task 6). */
    public static String clientCredentials(String clientId) {
        Map<String, Object> tokens = tokenRequest(Map.of(
                "grant_type", "client_credentials", "client_id", clientId, "client_secret", clientSecret(clientId)));
        return (String) tokens.get("access_token");
    }

    /** Creates a client from its JSON representation; returns the Admin API's status (201 when created). */
    public static int createClient(String json) {
        return admin("POST", "/clients", json).statusCode();
    }

    /** Creates an enabled organization named after its alias; returns the Admin API's status (201 when created). */
    public static int createOrganization(String alias, String domain) {
        return postOrganization(alias, domain).statusCode();
    }

    /**
     * Keycloak 26.8's default user profile requires username length >= 3; this test realm lowers it to 2 for the
     * plan's test user {@code mm}. Production usernames are e-mails or tenant-prefixed (doc §6.3.1). Only that one
     * validator changes: the realm's current profile is read, edited and written back.
     */
    private static void allowTwoLetterUsernames() {
        Map<String, Object> profile =
                JSON.parseMap(admin("GET", "/users/profile", null).body());
        Map<String, Object> usernameLength = asList(profile.get("attributes")).stream()
                .map(SpikeKeycloak::asMap)
                .filter(attribute -> "username".equals(attribute.get("name")))
                .map(attribute -> asMap(asMap(attribute.get("validations")).get("length")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No username length validator in " + profile));
        usernameLength.put("min", 2);
        expect(
                admin("PUT", "/users/profile", JsonWriter.standard().writeToString(profile)),
                200,
                "username length in the user profile");
    }

    /** Returns the user ids by username. */
    private static Map<String, String> createUsers() {
        Map<String, String> userIds = new LinkedHashMap<>();
        for (String username : MEMBERS.keySet()) {
            String email = username + "@" + emailDomain(username);
            HttpResponse<String> created =
                    admin("POST", "/users", """
                    {"username": %s, "email": %s, "firstName": %s, "lastName": "Test", "enabled": true,
                     "emailVerified": true}""".formatted(quote(username), quote(email), quote(username)));
            String userId = createdId(created, "user " + username);
            String password = randomUrlSafe(24);
            expect(
                    admin("PUT", "/users/" + userId + "/reset-password", """
                            {"type": "password", "value": %s, "temporary": false}""".formatted(quote(password))),
                    204,
                    "password of " + username);
            PASSWORDS.put(username, password);
            userIds.put(username, userId);
        }
        return userIds;
    }

    private static void createOrganizationsWithMembers(Map<String, String> userIds) {
        for (String alias : List.of("acme", "globex", "initech")) {
            String organizationId = createdId(postOrganization(alias, alias + ".example"), "organization " + alias);
            MEMBERS.forEach((username, organizations) -> {
                if (organizations.contains(alias)) {
                    expect(
                            admin(
                                    "POST",
                                    "/organizations/" + organizationId + "/members",
                                    quote(userIds.get(username))),
                            201,
                            username + " joining " + alias);
                }
            });
        }
    }

    /** Idempotent: binding a scope the client already has is a no-op in Keycloak. */
    private static void offerOrganizationScopeToWebClient() {
        String scopeId = JSON.parseList(admin("GET", "/client-scopes", null).body()).stream()
                .map(SpikeKeycloak::asMap)
                .filter(scope -> "organization".equals(scope.get("name")))
                .map(scope -> (String) scope.get("id"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The erp realm has no 'organization' client scope"));
        expect(
                admin("PUT", "/clients/" + clientUuid("erp-web") + "/optional-client-scopes/" + scopeId, null),
                204,
                "organization scope on erp-web");
    }

    private static HttpResponse<String> postOrganization(String alias, String domain) {
        return admin("POST", "/organizations", """
                {"name": %s, "alias": %s, "enabled": true, "domains": [{"name": %s}]}""".formatted(quote(alias), quote(alias), quote(domain)));
    }

    private static String clientUuid(String clientId) {
        List<Object> clients = JSON.parseList(
                admin("GET", "/clients?clientId=" + encode(clientId), null).body());
        if (clients.size() != 1) {
            throw new IllegalStateException("Expected one client " + clientId + ", found " + clients.size());
        }
        return (String) asMap(clients.getFirst()).get("id");
    }

    /** The confidential clients the realm file declares. */
    private static List<String> realmClientIds() {
        try (InputStream realm = SpikeKeycloak.class.getClassLoader().getResourceAsStream(REALM_FILE)) {
            if (realm == null) {
                throw new IllegalStateException("Missing classpath resource " + REALM_FILE);
            }
            Object clients =
                    JSON.parseMap(new String(realm.readAllBytes(), UTF_8)).get("clients");
            return asList(clients).stream()
                    .map(SpikeKeycloak::asMap)
                    .filter(client -> Boolean.FALSE.equals(client.get("publicClient")))
                    .map(client -> (String) client.get("clientId"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> tokenRequest(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                .collect(Collectors.joining("&"));
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(body))
                        .build());
        expect(response, 200, "token request (" + form.get("grant_type") + ", " + form.get("client_id") + ")");
        return JSON.parseMap(response.body());
    }

    /** An Admin API call on the erp realm with a fresh admin token: master's access tokens live only 60 s. */
    private static HttpResponse<String> admin(String method, String path, @Nullable String json) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + "/admin/realms/" + REALM + path))
                .header("Authorization", "Bearer " + adminToken())
                .method(method, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json));
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        return send(request.build());
    }

    private static String adminToken() {
        String body = "grant_type=password&client_id=admin-cli&username=" + encode(ADMIN_USERNAME) + "&password="
                + encode(ADMIN_PASSWORD);
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(baseUrl() + "/realms/master/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(body))
                        .build());
        expect(response, 200, "admin token");
        return (String) JSON.parseMap(response.body()).get("access_token");
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }

    private static String createdId(HttpResponse<String> response, String what) {
        expect(response, 201, what);
        String location = response.headers()
                .firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("No Location for created " + what));
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static void expect(HttpResponse<String> response, int status, String what) {
        if (response.statusCode() != status) {
            throw new IllegalStateException("Keycloak answered " + response.statusCode() + " instead of " + status
                    + " for " + what + " (" + response.request().method() + " " + response.uri() + "): "
                    + response.body());
        }
    }

    private static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    private static String emailDomain(String username) {
        List<String> organizations = MEMBERS.get(username);
        return organizations.size() == 1 ? organizations.getFirst() + ".example" : "musavir.example";
    }

    private static Map<String, List<String>> members() {
        Map<String, List<String>> members = new LinkedHashMap<>();
        members.put("ayse", List.of("acme"));
        members.put("zeynep", List.of("globex"));
        members.put("mm", List.of("acme", "globex"));
        members.put("ipek", List.of("initech"));
        return members;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(@Nullable Object json) {
        return (Map<String, Object>) json;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(@Nullable Object json) {
        return (List<Object>) json;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }

    private static String randomUrlSafe(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
