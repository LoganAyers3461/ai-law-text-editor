import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal client for sending editor content to the OpenAI Responses API. */
public final class OpenAIClient {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ChatGPTPlanAuth auth = new ChatGPTPlanAuth();
    private final String configuredModel;
    private volatile List<ChatGPTModelCatalog.Model> modelCatalog;
    public boolean isSignedIn() { return auth.isSignedIn(); }
    public void signIn() throws Exception { auth.signIn(); modelCatalog = null; }
    public void signOut() throws IOException { auth.signOut(); modelCatalog = null; }

    public OpenAIClient() {
        this.configuredModel = configuredModel();
    }

    private static String configuredModel() {
        String value = configValue("OPENAI_MODEL");
        return value == null || value.isBlank() ? null : value;
    }

    // Process environment takes precedence; .env is a local fallback and is never written by the app.
    private static String configValue(String name) {
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) return environmentValue;
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) return null;
        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int separator = line.indexOf('=');
                if (separator < 0 || !line.substring(0, separator).trim().equals(name)) continue;
                String value = line.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read .env file", ex);
        }
        return null;
    }

    public String analyze(String document, List<String> contextDocuments)
            throws IOException, InterruptedException {
        return chat(document, contextDocuments, "", "", "", "Analyze the current draft. Identify issues and ambiguities.");
    }

    public String chat(String document, List<String> contextDocuments, String jurisdiction,
                       String situationNotes, String conversationHistory, String message)
            throws IOException, InterruptedException {
        String accessToken = auth.accessToken();
        String model = selectModel(accessToken).slug;
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are assisting with one legal document in a self-contained workspace. Use only the "
                + "current document, its attached context documents, its case metadata, this conversation, "
                + "and sources you retrieve with web search. Do not assume or refer to other conversations, "
                + "ChatGPT memory, or facts not supplied or cited.\n"
                + "For claims based on an attached document, cite its exact filename and, when available, "
                + "page or section. For external factual or legal claims, use web search and give explicit "
                + "inline references with source title and URL. Prefer primary official sources for law "
                + "(courts, legislatures, and government sites), especially those for the specified jurisdiction. "
                + "If you cannot verify a claim, say so and do not invent authorities. Distinguish supplied facts, "
                + "verified external information, and analysis. This is drafting assistance, not legal advice.\n\n")
                .append("DOCUMENT METADATA\nApplicable jurisdiction(s): ").append(emptyAsUnspecified(jurisdiction))
                .append("\nSituation notes: ").append(emptyAsUnspecified(situationNotes))
                .append("\n\nCURRENT DOCUMENT:\n").append(document);
        for (int i = 0; i < contextDocuments.size(); i++) {
            prompt.append("\n\nCONTEXT DOCUMENT ").append(i + 1).append(":\n")
                    .append(contextDocuments.get(i));
        }
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            prompt.append("\n\nTHIS DOCUMENT'S CHAT HISTORY:\n").append(conversationHistory);
        }
        prompt.append("\n\nUSER MESSAGE:\n").append(message);
        String instructions = "Keep this work scoped to the supplied document, its attached context and metadata, this document's chat, and sources retrieved with web search. Give explicit source title and URL for external claims; prefer official legal sources, and do not invent authorities.";
        String body = "{\"model\":" + json(model) + ",\"instructions\":" + json(instructions)
                + ",\"store\":false,\"stream\":true,\"input\":[{\"role\":\"user\",\"content\":"
                + json(prompt.toString()) + "}],\"tools\":[{\"type\":\"web_search\"}]}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.util.stream.Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (java.util.stream.Stream<String> lines = response.body()) {
                String error = lines.limit(20).reduce("", (a, b) -> a + b);
                String requestId = response.headers().firstValue("x-request-id")
                        .orElse(response.headers().firstValue("openai-request-id").orElse(null));
                throw ChatGPTApiErrors.httpFailure(response.statusCode(), error, requestId);
            }
        }
        StringBuilder output = new StringBuilder();
        String completedEvent = "";
        boolean completed = false;
        try (java.util.stream.Stream<String> lines = response.body()) {
            java.util.Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                String type = ChatGPTPlanAuth.jsonValue(data, "type");
                if ("response.output_text.delta".equals(type)) {
                    String delta = ChatGPTPlanAuth.jsonValue(data, "delta");
                    if (delta != null) output.append(delta);
                } else if ("response.completed".equals(type)) { completed = true; completedEvent = data; }
                else if ("response.failed".equals(type) || "error".equals(type))
                    throw ChatGPTApiErrors.streamFailure(data);
                else if ("response.incomplete".equals(type))
                    throw new IOException("ChatGPT stopped before completing the response. Try a shorter document or request.");
            }
        }
        if (!completed) throw new IOException("ChatGPT response stream ended before completion.");
        String result = appendCitations(output.toString(), completedEvent);
        if (result.isBlank()) throw new IOException("ChatGPT returned no analysis text.");
        return result;
    }

    private ChatGPTModelCatalog.Model selectModel(String accessToken) throws IOException, InterruptedException {
        List<ChatGPTModelCatalog.Model> models = modelCatalog;
        if (models == null) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String requestId = response.headers().firstValue("x-request-id")
                        .orElse(response.headers().firstValue("openai-request-id").orElse(null));
                throw ChatGPTApiErrors.httpFailure(response.statusCode(), response.body(), requestId);
            }
            models = ChatGPTModelCatalog.parse(response.body());
            modelCatalog = models;
        }
        return ChatGPTModelCatalog.select(models, configuredModel);
    }

    private static String emptyAsUnspecified(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private static String appendCitations(String output, String body) {
        Matcher citation = Pattern.compile("\\{[^{}]*\\\"type\\\"\\s*:\\s*\\\"url_citation\\\"[^{}]*\\}").matcher(body);
        List<String> references = new ArrayList<>();
        while (citation.find()) {
            String object = citation.group();
            Matcher urlMatch = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(object);
            Matcher titleMatch = Pattern.compile("\\\"title\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(object);
            if (urlMatch.find()) {
                String url = unescape(urlMatch.group(1));
                String title = titleMatch.find() ? unescape(titleMatch.group(1)) : url;
                String ref = "- " + title + " — " + url;
                if (!references.contains(ref)) references.add(ref);
            }
        }
        if (!references.isEmpty()) return output + "\n\nReferences (web sources):\n" + String.join("\n", references);
        return output;
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    // The Responses API emits output_text as text objects within output[].content[].
    private static String extractText(String body) throws IOException {
        Matcher matcher = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(body);
        List<String> chunks = new ArrayList<>();
        while (matcher.find()) chunks.add(unescape(matcher.group(1)));
        if (chunks.isEmpty()) throw new IOException("Could not find analysis text in the API response.");
        return String.join("\n", chunks);
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) { out.append(c); continue; }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'u':
                    if (i + 4 < value.length()) {
                        out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); i += 4;
                    }
                    break;
                default: out.append(escaped);
            }
        }
        return out.toString();
    }
}
