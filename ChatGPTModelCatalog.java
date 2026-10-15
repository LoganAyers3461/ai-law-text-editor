import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the account-scoped model catalog documented for ChatGPT plan tokens. */
final class ChatGPTModelCatalog {
    static final class Model {
        final String slug;
        final String displayName;

        Model(String slug, String displayName) {
            this.slug = slug;
            this.displayName = displayName;
        }
    }

    static List<Model> parse(String json) throws IOException {
        int modelsKey = json.indexOf("\"models\"");
        if (modelsKey < 0) throw new IOException("The ChatGPT model catalog response did not include a models list.");
        int arrayStart = json.indexOf('[', modelsKey);
        if (arrayStart < 0) throw new IOException("The ChatGPT model catalog returned an invalid models list.");
        List<Model> models = new ArrayList<>();
        boolean inString = false, escaped = false;
        int depth = 0, objectStart = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') {
                if (depth++ == 0) objectStart = i;
            } else if (c == '}' && depth > 0 && --depth == 0) {
                String object = json.substring(objectStart, i + 1);
                String visibility = value(object, "visibility");
                String slug = value(object, "slug");
                if ("list".equals(visibility) && slug != null && !slug.isBlank()) {
                    String name = value(object, "display_name");
                    models.add(new Model(slug, name == null || name.isBlank() ? slug : name));
                }
                objectStart = -1;
            } else if (c == ']' && depth == 0) break;
        }
        return Collections.unmodifiableList(models);
    }

    static Model select(List<Model> models, String configuredSlug) throws IOException {
        if (models.isEmpty()) throw new IOException("ChatGPT returned no models available for this account.");
        if (configuredSlug != null && !configuredSlug.isBlank()) {
            for (Model model : models) if (model.slug.equals(configuredSlug)) return model;
            throw new UnsupportedModelException(configuredSlug);
        }
        // OpenAI documents preserving server ordering; the first displayable model is the default.
        return models.get(0);
    }

    private static String value(String json, String name) {
        String q = Pattern.quote("\"");
        Matcher matcher = Pattern.compile(q + Pattern.quote(name) + q
                + "\\s*:\\s*" + q + "((?:\\\\.|[^\"\\\\])*)" + q).matcher(json);
        if (!matcher.find()) return null;
        return matcher.group(1).replace("\\\\", "\\").replace("\\\"", "\"")
                .replace("\\n", "\n").replace("\\r", "\r");
    }

    static final class UnsupportedModelException extends IOException {
        UnsupportedModelException(String slug) {
            super("Configured model '" + slug + "' is not available to this ChatGPT account. "
                    + "Remove OPENAI_MODEL to use the account's first available model, or choose a listed model.");
        }
    }
}
