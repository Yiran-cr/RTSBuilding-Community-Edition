package com.rtsbuilding.rtsbuilding.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RtsPinyinSearch 字典注入机制测试。
 *
 * <p>验证 {@link RtsPinyinSearch#setDictionarySource} 注入自定义字典来源后能正常加载
 * 并参与搜索（阶段三 3.2：资源解耦——字典来源可注入，common 不依赖 classpath 资源路径）。
 */
class RtsPinyinSearchTest {

    private static final String TEST_DICT =
            "石:shi,dan\n" +
            "家:jia\n" +
            "木:mu\n" +
            "人:ren\n";

    @AfterEach
    void resetSource() {
        // 恢复 classpath 默认，避免污染其他测试
        RtsPinyinSearch.setDictionarySource(() -> defaultDictionaryStream());
    }

    /** 供测试访问 classpath 默认字典流的包私有入口。 */
    private static InputStream defaultDictionaryStream() {
        return RtsPinyinSearch.class.getResourceAsStream("/assets/rtsbuilding/pinyin/data.txt");
    }

    @Test
    void customSourceIsLoadedAndMatches() {
        RtsPinyinSearch.setDictionarySource(() ->
                new ByteArrayInputStream(TEST_DICT.getBytes(StandardCharsets.UTF_8)));

        // 首调 contains 会懒重载字典（来源变更后），随后命中拼音/首字母
        assertTrue(RtsPinyinSearch.contains("石头", "shi"), "全拼 'shi' 应命中 '石'");
        assertTrue(RtsPinyinSearch.contains("家人", "jr"), "首字母 'jr' 应命中 '家人'");
        assertTrue(RtsPinyinSearch.contains("木头", "mu"), "全拼 'mu' 应命中 '木'");
    }

    @Test
    void emptySourceKeepsExistingDictionary() {
        // 先加载一个有效自定义字典
        RtsPinyinSearch.setDictionarySource(() ->
                new ByteArrayInputStream(TEST_DICT.getBytes(StandardCharsets.UTF_8)));
        assertTrue(RtsPinyinSearch.contains("石头", "shi"), "自定义字典应命中");

        // 注入空源（新来源不可用）→ 保留原字典，搜索仍可用（防御性降级）
        RtsPinyinSearch.setDictionarySource(() ->
                new ByteArrayInputStream(new byte[0]));
        assertTrue(RtsPinyinSearch.contains("石头", "shi"), "新来源加载失败应保留原字典");
    }

    @Test
    void nonCjkTextReturnsFalse() {
        RtsPinyinSearch.setDictionarySource(() ->
                new ByteArrayInputStream(TEST_DICT.getBytes(StandardCharsets.UTF_8)));
        assertEquals(false, RtsPinyinSearch.contains("hello world", "shi"));
        assertEquals(false, RtsPinyinSearch.contains("", ""));
    }
}
