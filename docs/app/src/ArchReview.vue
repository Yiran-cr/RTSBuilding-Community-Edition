<template>
  <div>
    <div class="list-header">
      <h1>RTS Building <span class="dot">·</span> {{ current.title }}</h1>
      <p>{{ current.desc }} · 来自 <code>{{ current.file }}</code></p>
    </div>

    <!-- 文档切换 -->
    <div class="search-row">
      <div class="filter-group">
        <button v-for="d in docs" :key="d.id" class="filter-chip"
                :class="{ active: activeId === d.id }" @click="activeId = d.id">
          {{ d.label }}
        </button>
      </div>
    </div>

    <!-- 统计条：从当前文档正文提炼的关键指标 -->
    <div class="stat-strip">
      <div v-for="(s, i) in stats" :key="i" class="stat">
        <b>{{ s.value }}</b><span>{{ s.label }}</span>
      </div>
    </div>

    <div class="card md-body" v-html="html"></div>

    <footer>RTS Building {{ current.label }} · 由 docs SPA 渲染</footer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { marked } from 'marked'

// 构建时内联 docs/ 下架构文档原始文本（相对 src/ 为 ../../architecture-*.md）
const modules = import.meta.glob('../../architecture-*.md', { eager: true, query: '?raw', import: 'default' })

// 文档元信息（glob 键为完整相对路径，按文件名末尾匹配）
const docs = [
  { id: 'review', label: '架构评审', file: 'docs/architecture-review.md',
    title: '架构评审', desc: '全仓模块架构走读 · 问题分级清单 · 改进路线图' },
  { id: 'optimization', label: '架构优化方案', file: 'docs/architecture-optimization.md',
    title: '架构优化方案', desc: '分阶段架构整改方案 · 止血 → 重构 → 基建' }
]

// 从 glob 产物中取指定文档的原始文本
function rawOf(id) {
  const entry = Object.entries(modules).find(([path]) => path.includes(`architecture-${id}.md`))
  return entry ? String(entry[1]) : `# ${id} 文档缺失`
}

const activeId = ref('review')
const current = computed(() => docs.find((d) => d.id === activeId.value) || docs[0])
const raw = computed(() => rawOf(activeId.value))
const html = computed(() => marked.parse(raw.value))

// 每个文档的统计指标（基于各自固定措辞提取，缺失回退默认值）
const statDefs = {
  review: [
    { label: 'Gradle 模块', re: /模块数 \| (\d+) 个 Gradle 子项目/, fb: '7' },
    { label: 'Java 行数（万）', re: /约 ([\d.]+) 万行 Java/, fb: '6.5' },
    { label: '同 JAR 内置 mod', re: /内置 mod 数 \| (\d+) 个 modId 共居一 JAR/, fb: '6' },
    { label: '待处理问题', count: /^\*\*P\d-\d/gm, fb: '0' }
  ],
  optimization: [
    { label: '优化阶段', count: /^## [1-6]\. 阶段/gm, fb: '6' },
    { label: '里程碑', count: /^\*\*M\d\*\*/gm, fb: '3' },
    { label: '风险取舍', count: /^\| (?:ActionType|集成统一|BD 保留|`Component\.translatable|上帝类拆分)/gm, fb: '5' },
    { label: '涉及文件', count: /^\| `(?:rtsbuilding|rtsaddon|\.github)/gm, fb: '14' }
  ]
}

const stats = computed(() => {
  const text = raw.value
  const defs = statDefs[activeId.value] || statDefs.review
  return defs.map((d) => {
    if (d.count) return { label: d.label, value: (text.match(d.count) || []).length || d.fb }
    const m = text.match(d.re)
    return { label: d.label, value: m ? m[1] : d.fb }
  })
})
</script>
