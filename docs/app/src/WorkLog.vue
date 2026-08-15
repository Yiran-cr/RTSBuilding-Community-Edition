<template>
  <div>
    <div class="list-header">
      <h1>RTS Building <span class="dot">·</span> 工作日志</h1>
      <p>每日代码修改总结 · 来自 <code>docs/change-log/</code></p>
    </div>

    <div v-if="entries.length === 0" class="note">暂无日志记录。</div>

    <div v-for="e in entries" :key="e.date" class="log-card">
      <div class="log-head">
        <h2>{{ e.date }}</h2>
        <span class="tag">{{ e.items.length }} 条</span>
      </div>
      <ul class="log-list">
        <li v-for="(item, i) in e.items" :key="i" v-html="item"></li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

// 构建时内联 docs/change-log/*.md 原始文本
const modules = import.meta.glob('../../change-log/*.md', { eager: true, query: '?raw', import: 'default' })

// 把文件名（YYYY-MM-DD.md）转为日期，并解析 markdown 为条目
const entries = computed(() => {
  return Object.entries(modules)
    .map(([path, raw]) => {
      const match = path.match(/(\d{4}-\d{2}-\d{2})\.md$/)
      const date = match ? match[1] : path
      return { date, html: marked.parse(String(raw)) }
    })
    .sort((a, b) => (a.date < b.date ? 1 : -1))
    .map((e) => ({
      date: e.date,
      items: extractItems(e.html)
    }))
})

// 把渲染后的 HTML 拆成列表条目（<li> 内单个 <code> 需包裹，v-html 只接受片段）
function extractItems(html) {
  // 简单拆分：按 <li>...</li> 提取
  const items = []
  const re = /<li>([\s\S]*?)<\/li>/g
  let m
  while ((m = re.exec(html)) !== null) {
    // 去掉可能的包裹层，保留内联格式
    items.push(m[1].trim())
  }
  return items
}
</script>
<!-- 样式由全局 style.css（DeepSeek 浅色主题）统一提供 -->
