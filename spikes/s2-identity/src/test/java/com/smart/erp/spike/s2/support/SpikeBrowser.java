package com.smart.erp.spike.s2.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Just enough browser for login flows across the app and Keycloak: one cookie jar per host, manual redirects and
 * Keycloak's login form. App hosts (*.erp.test) are reached the way Caddy reaches the app (design decision 1): plain
 * HTTP to 127.0.0.1 with the original Host and X-Forwarded-Proto: https. Every app response is recorded.
 */
public final class SpikeBrowser {

    private static final Pattern LOGIN_FORM = Pattern.compile("<form\\b[^>]*\\bid=\"kc-form-login\"[^>]*>");
    private static final Pattern ACTION = Pattern.compile("\\baction=\"([^\"]+)\"");
    private static final Pattern MAX_AGE_ZERO = Pattern.compile(";\\s*max-age=0\\s*(;|$)");

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final int appPort;
    private final Map<String, Map<String, String>> cookies = new HashMap<>();
    private final Map<String, List<String>> setCookieHeaders = new HashMap<>();
    private final List<Page> appResponses = new ArrayList<>();
    private UnaryOperator<URI> authorizationRewrite = UnaryOperator.identity();

    /** @param appPort the app's local HTTP port; 0 for a browser that only talks to Keycloak. */
    public SpikeBrowser(int appPort) {
        this.appPort = appPort;
    }

    public record Page(URI url, int status, HttpHeaders headers, String body) {
        /** The redirect target, resolved against the logical URL (the *.erp.test one, not 127.0.0.1). */
        public Optional<String> location() {
            return headers.firstValue("Location")
                    .map(location -> url.resolve(location).toString());
        }
    }

    /** Starts a login on {@code host} and follows it to the end (the landing page or the first non-redirect). */
    public Page login(String host, String username) {
        return follow(get("https://" + host + "/oauth2/authorization/keycloak", "Accept", "text/html"), username, null);
    }

    /**
     * Follows redirects and fills Keycloak's login form (identity-first: username, then password). Stops before
     * requesting a URL that starts with {@code stopBefore} and returns the redirect pointing there.
     */
    public Page follow(Page page, String username, @Nullable String stopBefore) {
        Page current = page;
        for (int step = 0; step < 20; step++) {
            Optional<String> location = current.location();
            if (location.isPresent()) {
                URI next = URI.create(location.get());
                if (stopBefore != null && next.toString().startsWith(stopBefore)) {
                    return current;
                }
                if (next.getPath().endsWith("/protocol/openid-connect/auth")) {
                    next = authorizationRewrite.apply(next);
                }
                current = get(next.toString(), "Accept", "text/html");
            } else if (current.status() == 200
                    && LOGIN_FORM.matcher(current.body()).find()) {
                current = submitLoginForm(current, username);
            } else {
                return current;
            }
        }
        throw new IllegalStateException("Login did not settle; last page: " + current.url());
    }

    /** Lets a test tamper with the authorization request on its way to Keycloak (e.g. swap the organization hint). */
    public void rewriteAuthorizationRequests(UnaryOperator<URI> rewrite) {
        this.authorizationRewrite = rewrite;
    }

    public Page get(String url, String... headerPairs) {
        return send(URI.create(url), "GET", null, headerPairs);
    }

    public Page post(String url, Map<String, String> form, String... headerPairs) {
        String body = form.entrySet().stream()
                .map(field ->
                        URLEncoder.encode(field.getKey(), UTF_8) + "=" + URLEncoder.encode(field.getValue(), UTF_8))
                .collect(Collectors.joining("&"));
        return send(URI.create(url), "POST", body, headerPairs);
    }

    /** The cookie's current value in the jar of {@code host} ({@code host:port} for Keycloak). */
    public Optional<String> cookie(String host, String name) {
        return Optional.ofNullable(cookies.getOrDefault(host, Map.of()).get(name));
    }

    /** Plants a cookie as if another page had set it (cookie tossing, a stolen session). */
    public void putCookie(String host, String name, String value) {
        cookies.computeIfAbsent(host, jar -> new LinkedHashMap<>()).put(name, value);
    }

    /**
     * The Spring Session id behind {@code __Host-SESSION} (spring_session.session_id). Spring Session's
     * DefaultCookieSerializer writes the cookie value Base64-encoded.
     */
    public Optional<String> sessionId(String host) {
        return cookie(host, "__Host-SESSION")
                .map(value -> new String(Base64.getDecoder().decode(value), UTF_8));
    }

    /** Every raw Set-Cookie header {@code host} sent, in order: attributes are asserted on these. */
    public List<String> setCookieHeaders(String host) {
        return List.copyOf(setCookieHeaders.getOrDefault(host, List.of()));
    }

    public List<Page> appResponses() {
        return List.copyOf(appResponses);
    }

    /** The URL's query parameters, decoded. */
    public static MultiValueMap<String, String> query(String url) {
        MultiValueMap<String, String> decoded = new LinkedMultiValueMap<>();
        UriComponentsBuilder.fromUriString(url)
                .build()
                .getQueryParams()
                .forEach((name, values) -> values.forEach(
                        value -> decoded.add(name, value == null ? null : URLDecoder.decode(value, UTF_8))));
        return decoded;
    }

    public static Map<String, Object> json(Page page) {
        return JsonParserFactory.getJsonParser().parseMap(page.body());
    }

    private Page submitLoginForm(Page page, String username) {
        Matcher form = LOGIN_FORM.matcher(page.body());
        Matcher action = ACTION.matcher(form.find() ? form.group() : "");
        if (!action.find()) {
            throw new IllegalStateException("Keycloak login form without an action at " + page.url());
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (page.body().contains("name=\"username\"")) {
            fields.put("username", username);
        }
        if (page.body().contains("name=\"password\"")) {
            fields.put("password", SpikeKeycloak.password(username));
        }
        String target =
                page.url().resolve(action.group(1).replace("&amp;", "&")).toString();
        return post(target, fields, "Accept", "text/html");
    }

    private Page send(URI url, String method, @Nullable String form, String... headerPairs) {
        boolean app = url.getHost().endsWith(".erp.test");
        URI target = app
                ? URI.create("http://127.0.0.1:" + appPort + url.getRawPath()
                        + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery()))
                : url;
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .method(method, form == null ? BodyPublishers.noBody() : BodyPublishers.ofString(form));
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded");
        }
        if (app) {
            request.header("Host", url.getHost()).header("X-Forwarded-Proto", "https");
        }
        for (int i = 0; i < headerPairs.length; i += 2) {
            request.header(headerPairs[i], headerPairs[i + 1]);
        }
        String jar = url.getPort() == -1 ? url.getHost() : url.getHost() + ":" + url.getPort();
        String cookieHeader = cookies.getOrDefault(jar, Map.of()).entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
        if (!cookieHeader.isEmpty()) {
            request.header("Cookie", cookieHeader);
        }
        HttpResponse<String> response = exchange(request.build());
        response.headers().allValues("Set-Cookie").forEach(header -> store(jar, header));
        Page page = new Page(url, response.statusCode(), response.headers(), response.body());
        if (app) {
            appResponses.add(page);
        }
        return page;
    }

    private HttpResponse<String> exchange(HttpRequest request) {
        try {
            return http.send(request, BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }

    /** Path and Domain are not modelled: one jar per host. Tests assert attributes on the raw headers. */
    private void store(String jar, String header) {
        setCookieHeaders.computeIfAbsent(jar, host -> new ArrayList<>()).add(header);
        String pair = header.split(";", 2)[0];
        int equals = pair.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        Map<String, String> jarCookies = cookies.computeIfAbsent(jar, host -> new LinkedHashMap<>());
        if (MAX_AGE_ZERO.matcher(header.toLowerCase(Locale.ROOT)).find()) {
            jarCookies.remove(name);
        } else {
            jarCookies.put(name, value);
        }
    }
}
