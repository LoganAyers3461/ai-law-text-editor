import java.io.IOException;
import java.util.List;

/** Credential-free checks for delegated-model selection and API error classification. */
public final class ChatGPTIntegrationTests {
    public static void main(String[] args) throws Exception {
        modelCatalogSelectsOnlyVisibleAccountModels();
        configuredModelMustBePresentInCatalog();
        classifiesAccountAndAuthenticationFailures();
        classifiesUsageAndModelFailures();
        classifiesServiceFailures();
        System.out.println("ChatGPT integration checks passed.");
    }

    private static void modelCatalogSelectsOnlyVisibleAccountModels() throws Exception {
        String catalog = "{\"models\":["
                + "{\"slug\":\"hidden\",\"display_name\":\"Hidden\",\"visibility\":\"internal\"},"
                + "{\"slug\":\"model-free-a\",\"display_name\":\"Available A\",\"visibility\":\"list\",\"tools\":[{\"name\":\"web_search\"}]},"
                + "{\"slug\":\"model-free-b\",\"display_name\":\"Available B\",\"visibility\":\"list\"}]}";
        List<ChatGPTModelCatalog.Model> models = ChatGPTModelCatalog.parse(catalog);
        equal(2, models.size(), "only displayable models are retained");
        equal("model-free-a", ChatGPTModelCatalog.select(models, null).slug, "server's first visible model is default");
        equal("model-free-b", ChatGPTModelCatalog.select(models, "model-free-b").slug, "configured visible model is selected");
    }

    private static void configuredModelMustBePresentInCatalog() throws Exception {
        List<ChatGPTModelCatalog.Model> models = ChatGPTModelCatalog.parse(
                "{\"models\":[{\"slug\":\"available\",\"visibility\":\"list\"}]}");
        expect(ChatGPTModelCatalog.UnsupportedModelException.class,
                () -> ChatGPTModelCatalog.select(models, "not-available"), "unsupported configured model");
        expect(IOException.class, () -> ChatGPTModelCatalog.select(List.of(), null), "empty catalog");
        expect(IOException.class, () -> ChatGPTModelCatalog.parse("{}"), "malformed catalog shape");
    }

    private static void classifiesAccountAndAuthenticationFailures() {
        contains(ChatGPTApiErrors.httpFailure(403,
                "{\"error\":{\"code\":\"subscription_sharing_user_not_eligible\",\"message\":\"not eligible\"}}", "req-1"),
                "not eligible", "account eligibility");
        contains(ChatGPTApiErrors.httpFailure(401, "{\"detail\":\"bad token\"}", "req-2"),
                "sign in again", "authentication");
    }

    private static void classifiesUsageAndModelFailures() {
        contains(ChatGPTApiErrors.streamFailure(
                "{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\",\"message\":\"limit\"}}}"),
                "reached a usage limit", "plan usage limit");
        contains(ChatGPTApiErrors.httpFailure(429,
                "{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"slow down\"}}", null),
                "rate-limited", "request rate limit");
        contains(ChatGPTApiErrors.httpFailure(503,
                "{\"error\":{\"code\":\"subscription_sharing_usage_unavailable\"}}", null),
                "could not be checked", "usage availability");
        contains(ChatGPTApiErrors.httpFailure(400,
                "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\",\"param\":\"tools\"}}", null),
                "does not support a requested capability", "unsupported capability");
        contains(ChatGPTApiErrors.httpFailure(400,
                "{\"error\":{\"code\":\"model_not_found\",\"param\":\"model\",\"message\":\"unknown model\"}}", null),
                "model", "unsupported model");
    }

    private static void classifiesServiceFailures() {
        contains(ChatGPTApiErrors.httpFailure(503, "{\"detail\":\"temporarily unavailable\"}", "req-3"),
                "temporarily unavailable", "service failure detail");
        contains(ChatGPTApiErrors.httpFailure(503, "{\"detail\":\"temporarily unavailable\"}", "req-3"),
                "req-3", "request ID");
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
    private static void contains(Object actual, String expected, String label) {
        String message = ((IOException) actual).getMessage();
        if (!message.contains(expected)) throw new AssertionError(label + ": missing '" + expected + "' in " + message);
    }
    private static void expect(Class<? extends Throwable> type, ThrowingAction action, String label) throws Exception {
        try { action.run(); }
        catch (Throwable ex) { if (type.isInstance(ex)) return; throw ex; }
        throw new AssertionError(label + ": expected " + type.getSimpleName());
    }
    private interface ThrowingAction { void run() throws Exception; }
}
