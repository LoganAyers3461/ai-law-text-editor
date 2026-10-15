import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Responses API failures into actionable, non-billing-bypass diagnostics. */
final class ChatGPTApiErrors {
    enum Kind { ELIGIBILITY, AUTHENTICATION, RATE_LIMIT, PLAN_USAGE_LIMIT, USAGE_UNAVAILABLE,
        UNSUPPORTED_MODEL, UNSUPPORTED_CAPABILITY, SERVICE, REQUEST }

    static IOException httpFailure(int status, String body, String requestId) {
        String code = errorField(body, "code");
        String param = errorField(body, "param");
        String message = errorField(body, "message");
        if (message == null) message = directString(body, "detail");
        return new IOException(format(kind(status, code, param), status, code, message, requestId));
    }

    static IOException streamFailure(String event) {
        String code = errorField(event, "code");
        String param = errorField(event, "param");
        String message = errorField(event, "message");
        return new IOException(format(kind(200, code, param), 200, code, message, null));
    }

    private static Kind kind(int status, String code, String param) {
        if ("subscription_sharing_user_not_eligible".equals(code)
                || "chatpass_v2_scope_not_authorized".equals(code)
                || "chatpass_v2_invalid_authorization_context".equals(code)
                || "subscription_sharing_route_not_supported".equals(code)
                || status == 403) return Kind.ELIGIBILITY;
        if ("subscription_sharing_usage_limit_exceeded".equals(code)) return Kind.PLAN_USAGE_LIMIT;
        if ("subscription_sharing_usage_unavailable".equals(code)) return Kind.USAGE_UNAVAILABLE;
        if (status == 429) return Kind.RATE_LIMIT;
        if (status == 401) return Kind.AUTHENTICATION;
        if ("model_not_found".equals(code) || "unsupported_model".equals(code)
                || "model".equals(param)) return Kind.UNSUPPORTED_MODEL;
        if ("subscription_sharing_unsupported_capability".equals(code)) return Kind.UNSUPPORTED_CAPABILITY;
        if (status >= 500 || "server_error".equals(code)) return Kind.SERVICE;
        return Kind.REQUEST;
    }

    private static String format(Kind kind, int status, String code, String message, String requestId) {
        String prefix;
        switch (kind) {
            case ELIGIBILITY: prefix = "This ChatGPT account, workspace, or app is not eligible for this request"; break;
            case AUTHENTICATION: prefix = "ChatGPT authorization failed; sign in again"; break;
            case RATE_LIMIT: prefix = "The request was rate-limited; wait before trying again"; break;
            case PLAN_USAGE_LIMIT: prefix = "This ChatGPT plan or app has reached a usage limit; check ChatGPT usage settings"; break;
            case USAGE_UNAVAILABLE: prefix = "ChatGPT usage availability could not be checked; try again later"; break;
            case UNSUPPORTED_MODEL: prefix = "The selected model or capability is unavailable to this ChatGPT account"; break;
            case UNSUPPORTED_CAPABILITY: prefix = "This ChatGPT integration does not support a requested capability"; break;
            case SERVICE: prefix = "OpenAI's service is temporarily unavailable"; break;
            default: prefix = "ChatGPT rejected the request";
        }
        StringBuilder out = new StringBuilder(prefix).append(" (HTTP ").append(status);
        if (code != null) out.append(", ").append(code);
        out.append(')');
        if (message != null && !message.isBlank()) out.append(": ").append(message);
        if (requestId != null && !requestId.isBlank()) out.append(". Request ID: ").append(requestId);
        return out.toString();
    }

    private static String errorField(String json, String key) {
        int error = json.indexOf("\"error\"");
        if (error >= 0) {
            int start = json.indexOf('{', error);
            if (start >= 0) {
                int end = matchingObjectEnd(json, start);
                if (end > start) return directString(json.substring(start, end + 1), key);
            }
        }
        return directString(json, key);
    }

    private static int matchingObjectEnd(String json, int start) {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String directString(String json, String key) {
        String q = Pattern.quote("\"");
        Matcher matcher = Pattern.compile(q + Pattern.quote(key) + q
                + "\\s*:\\s*" + q + "((?:\\\\.|[^\"\\\\])*)" + q).matcher(json);
        if (!matcher.find()) return null;
        return matcher.group(1).replace("\\\\", "\\").replace("\\\"", "\"")
                .replace("\\n", "\n").replace("\\r", "\r");
    }
}
