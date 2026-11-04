package cn.aslight.workhub.mcp.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 配置及脚本文本脱敏；不修改磁盘内容，不将内容写入审计。 */
public final class ServerFileContentMasker {
    private static final String SECRET = "[\\w.-]{0,128}(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credential|authorization)[\\w.-]{0,128}";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?(?:-----END [^-\\r\\n]*PRIVATE KEY-----|\\z)", Pattern.DOTALL);
    private static final Pattern XML_SECRET = Pattern.compile(
            "(<(" + SECRET + ")(?:\\s[^>]*)?>).*?(</\\2\\s*>|\\z)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WHOLE_LINE = Pattern.compile(
            "^(\\s*(?:export\\s+)?[\"']?" + SECRET + "[\"']?\\s*[:=]).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YAML_BLOCK = Pattern.compile(
            "^(\\s*)[\"']?" + SECRET + "[\"']?\\s*:\\s*(?:[|>][+-]?[1-9]?\\s*)?(?:#.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?<![\\w.-])(" + SECRET + "[\"']?\\s*[:=]\\s*)(\"(?:\\\\.|[^\"\\\\])*+(?:\"|$)|'(?:[^'])*(?:'|$)|[^\\s,;#}\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern ARGUMENT = Pattern.compile(
            "(?i)(--" + SECRET + "\\s+)(\"[^\"]*(?:\"|$)|'[^']*(?:'|$)|\\S+)");
    private static final Pattern URI_PASSWORD = Pattern.compile("(://[^\\s/:@]+:)[^\\s/@]+(@)");
    private final SensitiveDataMasker personalDataMasker = new SensitiveDataMasker();

    public String mask(String content) {
        String value = PRIVATE_KEY.matcher(content).replaceAll("[REDACTED PRIVATE KEY]");
        value = XML_SECRET.matcher(value).replaceAll("$1[REDACTED]$3");
        StringBuilder result = new StringBuilder();
        int blockIndent = -1;
        boolean continuation = false;
        char secretQuote = 0;
        for (String line : value.split("\\n", -1)) {
            if (secretQuote != 0) {
                result.append("[REDACTED]\n");
                if (hasClosingQuote(line, 0, secretQuote)) {
                    secretQuote = 0;
                }
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (continuation || (blockIndent >= 0 && (line.isBlank() || indent > blockIndent))) {
                result.append("[REDACTED]\n");
                continuation = line.stripTrailing().endsWith("\\");
                continue;
            }
            blockIndent = -1;
            Matcher block = YAML_BLOCK.matcher(line);
            if (block.matches()) {
                blockIndent = block.group(1).length();
            }
            Matcher whole = WHOLE_LINE.matcher(line);
            if (whole.matches()) {
                result.append(whole.group(1)).append(" [REDACTED]\n");
                secretQuote = unclosedQuote(line.substring(whole.group(1).length()).stripLeading());
                continuation = secretQuote == 0 && line.stripTrailing().endsWith("\\");
                continue;
            }
            line = AUTH.matcher(line).replaceAll("[REDACTED AUTH]");
            Matcher assignment = ASSIGNMENT.matcher(line);
            while (assignment.find()) {
                char quote = unclosedQuote(assignment.group(2));
                if (quote != 0) {
                    secretQuote = quote;
                }
            }
            line = assignment.replaceAll("$1[REDACTED]");
            line = ARGUMENT.matcher(line).replaceAll("$1[REDACTED]");
            line = URI_PASSWORD.matcher(line).replaceAll("$1[REDACTED]$2");
            result.append(line).append('\n');
        }
        result.setLength(result.length() - 1);
        return personalDataMasker.maskText(result.toString());
    }

    private char unclosedQuote(String value) {
        if (value.isEmpty() || (value.charAt(0) != '\'' && value.charAt(0) != '"')) {
            return 0;
        }
        char quote = value.charAt(0);
        return hasClosingQuote(value, 1, quote) ? 0 : quote;
    }

    private boolean hasClosingQuote(String value, int from, char quote) {
        for (int index = from; index < value.length(); index++) {
            if (value.charAt(index) == '\\' && quote == '"') {
                index++;
            } else if (value.charAt(index) == quote) {
                return true;
            }
        }
        return false;
    }
}
