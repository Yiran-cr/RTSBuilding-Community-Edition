<script setup>
import { ref, computed } from 'vue'

// 文档清单：多张架构图可切换查看，后续逻辑链路报告可在此追加
const docs = [
  { id: 'architecture',   title: '模块架构',    src: '/architecture/rtsbuilding-architecture.html' },
  { id: 'main-module',    title: '主模组 main', src: '/architecture/rtsbuilding-main.html' },
  { id: 'planetrise',     title: '电网插件',    src: '/architecture/planetrise-module.html' },
  { id: 'power-grid',     title: '电网链路',    src: '/architecture/power-grid-flow.html' }
]
const active = ref(docs[0].id)
const activeDoc = computed(() => docs.find((d) => d.id === active.value))
</script>

<template>
  <div class="shell">
    <header class="header">
      <div class="brand">
        <span class="dot" />
        <h1>RTS Building 文档站</h1>
      </div>
      <nav class="nav">
        <button
          v-for="d in docs"
          :key="d.id"
          class="nav-item"
          :class="{ active: d.id === active }"
          @click="active = d.id"
        >
          {{ d.title }}
        </button>
      </nav>
    </header>
    <main class="stage">
      <iframe
        :src="activeDoc.src"
        :title="activeDoc.title"
        class="frame"
      />
    </main>
  </div>
</template>
