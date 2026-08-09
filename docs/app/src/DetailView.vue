<template>
  <div>
    <header class="detail">
      <h1>RTS Building <span class="dot">·</span> {{ report.title }}</h1>
      <div class="sub">{{ report.subtitle }}</div>
      <div class="tags">
        <span v-if="report.tags.module" class="tag">模块：<b>{{ report.tags.module }}</b></span>
        <span v-if="report.tags.status" class="tag">状态：<b>{{ report.tags.status }}</b></span>
        <span v-if="report.tags.counts" class="tag">{{ report.tags.counts }}</span>
        <span v-if="report.tags.result" class="tag"><b>{{ report.tags.result }}</b></span>
      </div>
    </header>

    <!-- 1 核心类职责 -->
    <section>
      <h2><span class="num">1</span>核心类职责</h2>
      <div class="card">
        <table>
          <thead><tr><th>类</th><th>位置</th><th>职责</th></tr></thead>
          <tbody>
            <tr v-for="c in report.classes" :key="c.name">
              <td><code>{{ c.name }}</code></td>
              <td class="path">{{ c.path }}</td>
              <td v-html="c.desc"></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 2 链路 -->
    <section>
      <h2><span class="num">2</span>{{ report.title }}</h2>

      <template v-for="(s, i) in report.sections" :key="i">
        <h3>{{ s.title }}</h3>

        <!-- 表格 -->
        <div v-if="s.type === 'table'" class="card">
          <table>
            <thead><tr><th v-for="h in s.headers" :key="h">{{ h }}</th></tr></thead>
            <tbody>
              <tr v-for="(row, ri) in s.rows" :key="ri">
                <td v-for="(cell, ci) in row" :key="ci" v-html="cell"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 时序图 -->
        <div v-else-if="s.type === 'flow'" class="card">
          <div class="flow">
            <div v-for="(n, ni) in s.nodes" :key="ni" class="step">
              <div class="box" :class="n.side === 'srv' ? 'srv' : 'cli'">
                <b>{{ n.side === 'srv' ? '服务端' : '客户端' }}</b> — <span v-html="n.text"></span>
              </div>
              <div v-if="ni < s.nodes.length - 1" class="arrow">▼</div>
            </div>
          </div>
        </div>

        <!-- 说明 -->
        <div v-else-if="s.type === 'note'" class="card">
          <div class="note" v-html="s.note"></div>
        </div>

        <!-- 并排卡片 -->
        <div v-else-if="s.type === 'cards'" class="card">
          <div class="grid">
            <div v-for="(c, ci) in s.cards" :key="ci" class="card" style="margin-bottom:0">
              <h4>{{ c.title }}</h4>
              <p class="note" style="font-size:12.5px" v-html="c.text"></p>
            </div>
          </div>
        </div>
      </template>
    </section>

    <!-- 3 问题与修复 -->
    <section>
      <h2><span class="num">3</span>走查发现的问题与修复</h2>
      <div v-for="(issue, i) in report.issues" :key="i"
           class="card issue" :class="issue.status === 'fixed' ? 'fixed' : ''">
        <h4>
          {{ issue.title }}
          <span class="status badge" :class="issue.status === 'fixed' ? 'b-fix' : 'b-block'">
            {{ issue.status === 'fixed' ? '已修复' : '保留（合理）' }}
          </span>
        </h4>
        <p class="why" v-html="issue.why"></p>
      </div>
    </section>

    <!-- 4 边界与设计 -->
    <section>
      <h2><span class="num">4</span>边界与设计说明</h2>
      <div class="card">
        <ul style="margin-left:18px; font-size:13.5px">
          <li v-for="(b, i) in report.boundaries" :key="i" style="margin-bottom:8px" v-html="b"></li>
        </ul>
      </div>
    </section>

    <footer>RTS Building {{ report.title }} 链路检查报告 · 由 docs SPA 渲染</footer>
  </div>
</template>

<script setup>
defineProps({
  report: { type: Object, required: true }
})
</script>
