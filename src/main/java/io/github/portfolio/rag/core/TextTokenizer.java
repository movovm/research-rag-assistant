package io.github.portfolio.rag.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextTokenizer {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[a-zA-Z0-9_+#.-]+");

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> raw = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            raw.add(matcher.group());
        }

        List<String> result = new ArrayList<>(raw);
        for (int i = 0; i < raw.size() - 1; i++) {
            if (isHan(raw.get(i)) && isHan(raw.get(i + 1))) {
                result.add(raw.get(i) + raw.get(i + 1));
            }
        }
        return result;
    }

    private boolean isHan(String token) {
        return token.length() == 1 && Character.UnicodeScript.of(token.charAt(0)) == Character.UnicodeScript.HAN;
    }
}
