<template>
  <div class="wrap">
    <!-- 顶部 Tab 切换 -->
    <div class="view-tabs">
      <button class="filter-chip" :class="{ active: view === 'reports' }" @click="switchView('reports')">链路检查报告</button>
      <button class="filter-chip" :class="{ active: view === 'worklog' }" @click="switchView('worklog')">工作日志</button>
    </div>

    <!-- ================= 报告列表页 ================= -->
    <template v-if="view === 'reports'">
      <div v-if="!selected">
        <div class="list-header">
          <h1>RTS Building <span class="dot">·</span> 链路检查报告</h1>
          <p>全部走查报告 · 点击进入查看详情</p>
        </div>

        <div class="search-row">
          <input v-model="keyword" placeholder="搜索报告标题 / 类 / 路径 / 问题..." />
          <div class="filter-group">
            <button class="filter-chip" :class="{ active: statusFilter === '' }" @click="statusFilter = ''">全部</button>
            <button class="filter-chip" :class="{ active: statusFilter === 'fixed' }" @click="statusFilter = 'fixed'">含已修复</button>
            <button class="filter-chip" :class="{ active: statusFilter === 'kept' }" @click="statusFilter = 'kept'">含保留项</button>
          </div>
        </div>

        <div v-if="filtered.length === 0" class="note">没有匹配的报告。</div>
        <div v-for="r in filtered" :key="r.id" class="report-card" @click="selected = r">
          <h2>{{ r.title }}</h2>
          <div class="sub">{{ r.subtitle }}</div>
          <div class="meta">
            <span class="tag">模块：<b>{{ r.tags.module }}</b></span>
            <span class="tag">状态：<b>{{ r.tags.status }}</b></span>
            <span v-if="r.tags.counts" class="tag">{{ r.tags.counts }}</span>
            <span v-if="r.tags.result" class="tag"><b>{{ r.tags.result }}</b></span>
          </div>
          <div class="issue-summary">
            问题 {{ issueCount(r) }} 项 ·
            已修复 {{ fixedCount(r) }} · 保留 {{ keptCount(r) }}
          </div>
        </div>
      </div>

      <!-- 报告详情页 -->
      <div v-else>
        <a class="back-link" href="#" @click.prevent="selected = null">← 返回报告列表</a>
        <DetailView :report="selected" />
      </div>
    </template>

    <!-- ================= 工作日志页 ================= -->
    <template v-else>
      <WorkLog />
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { loadReports } from './reports'
import DetailView from './DetailView.vue'
import WorkLog from './WorkLog.vue'

const reports = loadReports()
const view = ref('reports')
const selected = ref(null)
const keyword = ref('')
const statusFilter = ref('')

function switchView(v) {
  view.value = v
  selected.value = null
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return reports.filter((r) => {
    if (statusFilter.value === 'fixed' && fixedCount(r) === 0) return false
    if (statusFilter.value === 'kept' && keptCount(r) === 0) return false
    if (!kw) return true
    const hay = [
      r.title, r.subtitle, r.tags.module,
      ...r.classes.map((c) => c.name + ' ' + c.path + ' ' + c.desc),
      ...r.issues.map((i) => i.title + ' ' + i.why)
    ].join(' ').toLowerCase()
    return hay.includes(kw)
  })
})

function issueCount(r) { return r.issues.length }
function fixedCount(r) { return r.issues.filter((i) => i.status === 'fixed').length }
function keptCount(r) { return r.issues.filter((i) => i.status === 'kept').length }
</script>

<style scoped>
.view-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}
</style>
