package com.rtsbuilding.rtsbuilding.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 拼音/首字母模糊搜索。
 *
 * <p><b>资源注入约定</b>：字典默认从 classpath 加载（{@code /assets/rtsbuilding/pinyin/data.txt}），
 * 但该资源实际位于 main 模块 —— common 单独发版时 classpath 中不存在，会静默降级为空字典。
 * 为打破跨模块资源耦合，main 启动时应调用 {@link #setDictionarySource(Supplier)} 注入真实资源源
 * （如 Minecraft {@code ResourceManager} 的打开器），见阶段三 3.2 边界净化。
 */
public final class RtsPinyinSearch {
    private static final String DICT_PATH = "/assets/rtsbuilding/pinyin/data.txt";

    /** 字典来源：默认 classpath，可由 main 注入。volatile 保证跨线程可见性。 */
    private static volatile Supplier<InputStream> dictionarySource = () ->
            RtsPinyinSearch.class.getResourceAsStream(DICT_PATH);

    /** 来源变更后标记：下次 contains 需用新来源重载字典。 */
    private static volatile boolean needsReload = false;

    private static final Map<Character, String[]> PINYIN_BY_CHAR = loadDictionary();

    private RtsPinyinSearch() {
    }

    /**
     * 注入字典来源（main 启动时调用，用于提供真实资源打开器）。
     *
     * <p>注入后标记重载，在首次 {@link #contains} 调用时用新来源重新加载字典
     * （资源管理器可能晚于注入就绪）。调用方负责线程安全（建议 commonSetup 阶段单线程调用）。
     *
     * @param source 返回字典 {@link InputStream} 的供应器；传 null 恢复 classpath 默认
     */
    public static void setDictionarySource(Supplier<InputStream> source) {
        if (source == null) {
            dictionarySource = () -> RtsPinyinSearch.class.getResourceAsStream(DICT_PATH);
        } else {
            dictionarySource = source;
        }
        needsReload = true;
    }

    /** 当前字典是否非空（拼音搜索实际可用）。 */
    public static boolean isDictionaryLoaded() {
        return !PINYIN_BY_CHAR.isEmpty();
    }

    /**
     * 延迟重载：来源变更后首次调用时，用当前来源重新加载字典（仅触发一次）。
     *
     * <p>防御性设计：新来源加载<b>失败</b>（返回空/异常）时<b>保留原字典</b>，
     * 避免把已正常加载的 classpath 字典清空成空导致拼音搜索静默失效。
     */
    private static void ensureDictionaryLoaded() {
        if (!needsReload) {
            return;
        }
        synchronized (PINYIN_BY_CHAR) {
            if (!needsReload) {
                return;
            }
            needsReload = false;
            Map<Character, String[]> fresh = loadDictionary();
            if (!fresh.isEmpty()) {
                PINYIN_BY_CHAR.clear();
                PINYIN_BY_CHAR.putAll(fresh);
            }
            // fresh 为空（新来源不可用）→ 保留原字典，拼音搜索继续可用
        }
    }

    public static boolean contains(String text, String query) {
        if (text == null || text.isBlank() || query == null || query.isBlank()) {
            return false;
        }
        ensureDictionaryLoaded();
        if (!containsCjk(text)) {
            return false;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.isEmpty()) {
            return false;
        }
        if (text.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (matchesInitials(text, normalizedQuery)) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (matchesFrom(text, i, normalizedQuery, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFrom(String text, int textIndex, String query, int queryIndex) {
        if (queryIndex >= query.length()) {
            return true;
        }
        if (textIndex >= text.length()) {
            return false;
        }
        char ch = text.charAt(textIndex);
        String[] pinyins = PINYIN_BY_CHAR.get(ch);
        if (pinyins != null) {
            for (String pinyin : pinyins) {
                if (matchesTokenOption(text, textIndex, query, queryIndex, pinyin)) {
                    return true;
                }
            }
        }
        String literal = normalizeLiteral(ch);
        return !literal.isEmpty() && matchesTokenOption(text, textIndex, query, queryIndex, literal);
    }

    private static boolean matchesTokenOption(String text, int textIndex, String query, int queryIndex, String option) {
        if (option == null || option.isEmpty()) {
            return false;
        }
        String remaining = query.substring(queryIndex);
        if (option.startsWith(remaining)) {
            return true;
        }
        return query.startsWith(option, queryIndex)
                && matchesFrom(text, textIndex + 1, query, queryIndex + option.length());
    }

    private static boolean matchesInitials(String text, String query) {
        if (query.length() > text.length()) {
            return false;
        }
        StringBuilder initials = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String[] pinyins = PINYIN_BY_CHAR.get(ch);
            if (pinyins != null && pinyins.length > 0 && !pinyins[0].isEmpty()) {
                initials.append(pinyins[0].charAt(0));
                continue;
            }
            String literal = normalizeLiteral(ch);
            if (!literal.isEmpty()) {
                initials.append(literal.charAt(0));
            }
        }
        return initials.indexOf(query) >= 0;
    }

    private static String normalizeLiteral(char ch) {
        if (Character.isLetterOrDigit(ch)) {
            return String.valueOf(Character.toLowerCase(ch));
        }
        return "";
    }

    private static Map<Character, String[]> loadDictionary() {
        Map<Character, String[]> result = new HashMap<>();
        try (InputStream in = dictionarySource.get()) {
            if (in == null) {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseDictionaryLine(line, result);
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return result;
    }

    private static void parseDictionaryLine(String line, Map<Character, String[]> into) {
        if (line == null || line.length() < 4) {
            return;
        }
        int colon = line.indexOf(':');
        if (colon <= 0 || colon >= line.length() - 1) {
            return;
        }
        char ch = line.charAt(0);
        String[] rawPinyins = line.substring(colon + 1).trim().split("\\s+");
        String[] normalized = new String[rawPinyins.length];
        int count = 0;
        for (String raw : rawPinyins) {
            String clean = normalizePinyin(raw);
            if (clean.isEmpty()) {
                continue;
            }
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (normalized[i].equals(clean)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                normalized[count++] = clean;
            }
        }
        if (count > 0) {
            String[] pinyins = new String[count];
            System.arraycopy(normalized, 0, pinyins, 0, count);
            into.put(ch, pinyins);
        }
    }

    private static String normalizePinyin(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if (ch >= 'a' && ch <= 'z') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
