<template>
  <div>
    <div class="list-header">
      <h1>RTS Building <span class="dot">·</span> 功能总览</h1>
      <p>{{ catalog.note }}</p>
    </div>

    <!-- 统计条 -->
    <div class="stat-strip">
      <div class="stat"><b>{{ catalog.modules.length }}</b><span>功能模块</span></div>
      <div class="stat"><b>{{ total }}</b><span>功能点</span></div>
      <div class="stat"><b>{{ classTotal }}</b><span>关键类引用</span></div>
      <div class="stat"><b>{{ activeCount }}</b><span>当前展示</span></div>
    </div>

    <!-- 搜索 + 模块筛选 -->
    <div class="search-row">
      <input v-model="keyword" placeholder="搜索功能名 / 描述 / 关键类..." />
      <div class="filter-group">
        <button class="filter-chip" :class="{ active: activeModule === '' }" @click="activeModule = ''">全部</button>
        <button v-for="m in catalog.modules" :key="m.id" class="filter-chip"
                :class="{ active: activeModule === m.id }" @click="activeModule = m.id">
          {{ m.name }}<span class="chip-n">{{ m.features.length }}</span>
        </button>
      </div>
    </div>

    <div v-if="filtered.length === 0" class="note">没有匹配的功能。</div>

    <!-- 模块卡片 -->
    <section v-for="m in filtered" :key="m.id" class="feature-module">
      <div class="module-head">
        <div>
          <h2>{{ m.name }}</h2>
          <div class="sub">{{ m.tagline }}</div>
        </div>
        <span class="tag">{{ m.features.length }} 项</span>
      </div>

      <div class="feature-grid">
        <div v-for="f in m.features" :key="f.name" class="feature-card">
          <h4>{{ f.name }}</h4>
          <p>{{ f.desc }}</p>
          <div class="class-chips">
            <code v-for="c in f.classes" :key="c">{{ c }}</code>
          </div>
        </div>
      </div>
    </section>

    <footer>RTS Building 功能清单 · 由源码逐模块梳理 · 由 docs SPA 渲染</footer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { featuresCatalog, totalFeatureCount, totalClassCount } from './features'

const catalog = featuresCatalog
const total = totalFeatureCount()
const classTotal = totalClassCount()

const keyword = ref('')
const activeModule = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return catalog.modules
    .filter((m) => activeModule.value === '' || m.id === activeModule.value)
    .map((m) => ({
      ...m,
      features: m.features.filter((f) => {
        if (!kw) return true
        const hay = [f.name, f.desc, ...(f.classes || [])].join(' ').toLowerCase()
        return hay.includes(kw)
      })
    }))
    .filter((m) => m.features.length > 0)
})

const activeCount = computed(() => {
  return filtered.value.reduce((sum, m) => sum + m.features.length, 0)
})
</script>
