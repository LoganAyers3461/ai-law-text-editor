import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Base64;
import java.security.interfaces.RSAPublicKey;
import java.security.KeyFactory;

/** OAuth client for ChatGPT plan access using OpenAI's open-source token-sharing flow. */
public final class ChatGPTPlanAuth {
    private static final String AUTH = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final Path CREDENTIALS = Path.of(System.getProperty("user.home"), ".config",
            "ai-law-text-editor", "credentials.properties");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public boolean isSignedIn() { return load().getProperty("refresh_token") != null; }

    public synchronized void signIn() throws Exception {
        signIn(true);
    }

    private void signIn(boolean retryInvalidGrant) throws Exception {
        Properties p = load();
        String clientId = p.getProperty("client_id", "dynamic_agent_client");
        boolean firstRegistration = "dynamic_agent_client".equals(clientId);
        String hostId = p.getProperty("host_id");
        if (hostId == null) { hostId = "urn:uuid:" + UUID.randomUUID(); p.setProperty("host_id", hostId); }
        save(p); // Keep the host identity stable even if authorization needs to be retried.
        String state = random(32), nonce = random(32), verifier = random(48);
        String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        int port = server.getAddress().getPort();
        String redirect = "http://127.0.0.1:" + port + "/auth/callback";
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        String expectedState = state;
        server.createContext("/auth/callback", ex -> callback(ex, expectedState, codeFuture));
        server.start();
        try {
            String url = AUTH + "?" + form("client_id", clientId) + "&" + form("response_type", "code")
                    + "&" + form("redirect_uri", redirect) + "&" + form("scope", SCOPES)
                    + "&" + form("resource", RESOURCE) + "&" + form("state", state)
                    + "&" + form("nonce", nonce) + "&" + form("code_challenge", challenge)
                    + "&code_challenge_method=S256&" + form("ext_agent_host_id", hostId)
                    + (firstRegistration ? "&" + form("agent_name_hint", "AI Law Text Editor") : "")
                    + (!firstRegistration && p.getProperty("id_token") != null ? "&" + form("id_token_hint", p.getProperty("id_token")) : "");
            if (!Desktop.isDesktopSupported()) throw new IOException("No desktop browser is available for sign-in.");
            Desktop.getDesktop().browse(URI.create(url));
            String callbackResult = codeFuture.get(3, TimeUnit.MINUTES);
            String[] callbackValues = callbackResult.split("\\n", 2);
            String code = callbackValues[0];
            String callbackClientId = callbackValues.length > 1 ? callbackValues[1] : "";
            if (firstRegistration && callbackClientId.isBlank())
                throw new IOException("ChatGPT did not return the registered client ID. The authorization may not be enabled for this app; try again after checking the OpenAI sign-in setup.");
            if (!firstRegistration && !callbackClientId.isBlank() && !clientId.equals(callbackClientId))
                throw new IOException("ChatGPT returned a different client ID than the saved sign-in.");
            String tokenClientId = firstRegistration ? callbackClientId : clientId;
            if (code == null || code.isBlank()) throw new IOException("ChatGPT returned no authorization code.");
            // Dynamic registration issues a client ID before code exchange. Keep it if the
            // one-time code is rejected so the next authorization uses that registered ID.
            if (firstRegistration) {
                p.setProperty("client_id", tokenClientId);
                save(p);
            }
            String payload = "grant_type=authorization_code&" + form("client_id", tokenClientId) + "&" + form("code", code)
                    + "&" + form("code_verifier", verifier) + "&" + form("redirect_uri", redirect)
                    + "&" + form("resource", RESOURCE);
            HttpResponse<String> response = postForm(TOKEN, payload);
            if (response.statusCode() / 100 != 2) {
                if (retryInvalidGrant && "invalid_grant".equals(jsonValue(response.body(), "error"))) {
                    // OAuth codes are single-use. OpenAI recommends discarding a rejected
                    // code and starting a fresh authorization using the issued client ID.
                    server.stop(0);
                    signIn(false);
                    return;
                }
                String detail = jsonValue(response.body(), "error_description");
                if (detail == null) detail = jsonValue(response.body(), "error");
                if (detail == null) detail = response.body().replaceAll("\\s+", " ").trim();
                String requestId = response.headers().firstValue("x-request-id")
                        .orElse(response.headers().firstValue("openai-request-id").orElse("unavailable"));
                String recovery = "invalid_grant".equals(jsonValue(response.body(), "error"))
                        ? " The one-time code was rejected; it cannot be reused. A new attempt will use the saved client ID."
                        : "";
                throw new IOException("ChatGPT sign-in failed (HTTP " + response.statusCode() + "): "
                        + detail.substring(0, Math.min(300, detail.length())) + ". OAuth request ID: " + requestId + "." + recovery);
            }
            String body = response.body();
            String access = jsonValue(body, "access_token"), refresh = jsonValue(body, "refresh_token"), id = jsonValue(body, "id_token");
            if (access == null || refresh == null || id == null) throw new IOException("Sign-in response omitted required tokens.");
            verifyIdToken(id, nonce, tokenClientId);
            String granted = jsonValue(body, "scope");
            if (granted == null || !Arrays.asList(granted.split("\\s+")).contains("chatgpt.tokens.use.direct"))
                throw new IOException("ChatGPT did not grant Responses access for this app.");
            String issuedClient = jsonValue(body, "client_id");
            p.setProperty("client_id", issuedClient == null ? tokenClientId : issuedClient);
            p.setProperty("host_id", hostId); p.setProperty("access_token", access);
            p.setProperty("refresh_token", refresh); p.setProperty("id_token", id);
            p.setProperty("scope", granted);
            String expires = jsonValue(body, "expires_in");
            p.setProperty("expires_at", Long.toString(System.currentTimeMillis() + (expires == null ? 3600 : Long.parseLong(expires)) * 1000L));
            save(p);
        } finally { server.stop(0); }
    }

    public synchronized void signOut() throws IOException { Properties p = load(); p.remove("access_token"); p.remove("refresh_token"); p.remove("id_token"); p.remove("expires_at"); save(p); }

    public synchronized String accessToken() throws IOException, InterruptedException {
        Properties p = load(); String refresh = p.getProperty("refresh_token");
        if (refresh == null) throw new IllegalStateException("Choose AI → Login to ChatGPT first.");
        if (Long.parseLong(p.getProperty("expires_at", "0")) > System.currentTimeMillis() + 60000)
            return p.getProperty("access_token");
        String payload = "grant_type=refresh_token&" + form("client_id", p.getProperty("client_id"))
                + "&" + form("refresh_token", refresh) + "&" + form("resource", RESOURCE);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(TOKEN)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            String error = jsonValue(response.body(), "error");
            if ("invalid_grant".equals(error) || "invalid_refresh_token".equals(error)
                    || "refresh_token_expired".equals(error) || "refresh_token_invalidated".equals(error)
                    || "refresh_token_reused".equals(error)) {
                p.remove("access_token"); p.remove("refresh_token"); p.remove("id_token"); p.remove("expires_at");
                save(p);
                throw new IOException("The saved ChatGPT authorization expired or was revoked. Sign in to ChatGPT again.");
            }
            String detail = jsonValue(response.body(), "error_description");
            if (detail == null) detail = error;
            if (detail == null) detail = "request failed";
            throw new IOException("Could not refresh ChatGPT authorization (HTTP " + response.statusCode() + "): " + detail);
        }
        String body = response.body();
        String access = jsonValue(body, "access_token"), rotated = jsonValue(body, "refresh_token");
        if (access == null) throw new IOException("Token refresh did not return an access token.");
        p.setProperty("access_token", access); if (rotated != null) p.setProperty("refresh_token", rotated);
        String expires = jsonValue(body, "expires_in");
        p.setProperty("expires_at", Long.toString(System.currentTimeMillis() + (expires == null ? 3600 : Long.parseLong(expires)) * 1000L));
        save(p); return access;
    }

    private void verifyIdToken(String jwt, String nonce, String audience) throws Exception {
        String[] parts = jwt.split("\\."); if (parts.length != 3) throw new IOException("Invalid identity token.");
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        if (!"RS256".equals(jsonValue(header, "alg"))) throw new IOException("Unsupported identity token signature.");
        String kid = jsonValue(header, "kid");
        HttpResponse<String> keys = http.send(HttpRequest.newBuilder(URI.create(JWKS)).GET().build(), HttpResponse.BodyHandlers.ofString());
        String q = Pattern.quote("\"");
        Matcher keyMatcher = Pattern.compile("\\{[^{}]*" + q + "kid" + q + "\\s*:\\s*" + q + Pattern.quote(kid) + q + "[^{}]*\\}").matcher(keys.body());
        if (!keyMatcher.find()) throw new IOException("Identity token signing key not found.");
        String n = jsonValue(keyMatcher.group(), "n"), e = jsonValue(keyMatcher.group(), "e");
        RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, Base64.getUrlDecoder().decode(n)), new BigInteger(1, Base64.getUrlDecoder().decode(e))));
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initVerify(key);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new IOException("Identity token signature is invalid.");
        if (!"https://auth.openai.com".equals(jsonValue(claims, "iss")) || !audience.equals(jsonValue(claims, "aud"))
                || !nonce.equals(jsonValue(claims, "nonce"))) throw new IOException("Identity token claims did not match this sign-in.");
        String expiry = jsonValue(claims, "exp");
        if (expiry == null || Long.parseLong(expiry) <= System.currentTimeMillis() / 1000) throw new IOException("Identity token has expired.");
    }

    private static void callback(HttpExchange ex, String state, CompletableFuture<String> result) throws IOException {
        Map<String,String> query = query(ex.getRequestURI().getRawQuery());
        if (!state.equals(query.get("state"))) result.completeExceptionally(new IOException("OAuth state validation failed."));
        else if (query.containsKey("error")) result.completeExceptionally(new IOException("ChatGPT sign-in was declined: " + query.get("error")));
        else if (query.get("code") == null || query.get("code").isBlank())
            result.completeExceptionally(new IOException("ChatGPT returned no authorization code."));
        else result.complete(query.get("code") + "\n" + query.getOrDefault("client_id", ""));
        byte[] page = "<html><body>ChatGPT sign-in received. You may close this tab.</body></html>".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); ex.sendResponseHeaders(200, page.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(page); }
    }
    private HttpResponse<String> postForm(String uri, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static Properties load() { Properties p = new Properties(); try { if (Files.exists(CREDENTIALS)) try (Reader r=Files.newBufferedReader(CREDENTIALS)) { p.load(r); } } catch(IOException e) { throw new IllegalStateException("Could not read ChatGPT credentials.", e); } return p; }
    private static void save(Properties p) throws IOException {
        Files.createDirectories(CREDENTIALS.getParent());
        try { Files.setPosixFilePermissions(CREDENTIALS.getParent(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)); } catch(UnsupportedOperationException ignored) {}
        Path tmp = Files.createTempFile(CREDENTIALS.getParent(), "credentials", ".tmp");
        try { try { Files.setPosixFilePermissions(tmp, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); } catch(UnsupportedOperationException ignored) {}
            try (Writer w=Files.newBufferedWriter(tmp)) { p.store(w, "ChatGPT sign-in tokens; keep private"); }
            Files.move(tmp, CREDENTIALS, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(tmp); }
    }
    private static String random(int bytes) { byte[] b=new byte[bytes]; new SecureRandom().nextBytes(b); return b64(b); }
    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String form(String k,String v) { return URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8); }
    private static Map<String,String> query(String q) { Map<String,String> m=new HashMap<>(); if(q!=null) for(String part:q.split("&")){int i=part.indexOf('='); if(i>=0)m.put(URLDecoder.decode(part.substring(0,i),StandardCharsets.UTF_8),URLDecoder.decode(part.substring(i+1),StandardCharsets.UTF_8));} return m; }
    static String jsonValue(String json,String key) { String q=Pattern.quote("\""); Matcher m=Pattern.compile(q+Pattern.quote(key)+q+"\\s*:\\s*(?:"+q+"((?:\\\\.|[^\"\\\\])*)"+q+"|(-?\\d+|true|false|null))").matcher(json); if(!m.find())return null; return m.group(1)!=null?unescape(m.group(1)):m.group(2); }
    private static String unescape(String s) { return s.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r"); }
}
