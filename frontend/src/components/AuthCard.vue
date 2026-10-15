<script setup lang="ts">
/** 登录、注册、找回密码共用的页面框：左边品牌和三句介绍，右边表单 */
defineProps<{ title: string; subtitle?: string }>()

const POINTS = [
  ['技能结构化', '谁会什么、哪个队缺什么，都记下来'],
  ['匹配推荐', '五个维度打分，每条推荐都说明理由'],
  ['赛程日历', '比赛节点、队伍里程碑和个人日程，放在一张图上'],
]
</script>

<template>
  <div class="auth-page">
    <section class="intro" aria-label="竞搭">
      <div class="brand">
        <div class="name">竞搭</div>
        <div class="sub">COMPETITION<br />TEAM MATCHING</div>
      </div>
      <p class="kicker">竞赛组队匹配平台 <span class="zh">/ 能在综测里加分的比赛</span></p>
      <ol class="points">
        <li v-for="([head, text], i) in POINTS" :key="head">
          <span class="idx num">0{{ i + 1 }}</span>
          <strong>{{ head }}</strong>
          <span class="text">{{ text }}</span>
        </li>
      </ol>
    </section>

    <section class="panel">
      <p class="kicker">竞搭 <span class="zh">/ 账号</span></p>
      <h1 class="display title">{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <hr class="rule" />
      <slot />
      <div class="footer"><slot name="footer" /></div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 440px);
  gap: 64px;
  align-items: center;
  max-width: 1080px;
  margin: 0 auto;
  padding: 48px 32px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.name {
  font-size: 64px;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1;
}

.sub {
  padding-left: 16px;
  border-left: 2px solid var(--jd-ink);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.18em;
}

.points {
  list-style: none;
  margin: 24px 0 0;
  padding: 0;
  max-width: 420px;
}

.points li {
  display: grid;
  grid-template-columns: 32px 96px 1fr;
  align-items: baseline;
  padding: 12px 0;
  border-top: 1px solid var(--jd-line-light);
}

.points li:last-child {
  border-bottom: 1px solid var(--jd-line-light);
}

.idx {
  font-size: 11px;
  color: var(--jd-muted);
}

.points strong {
  font-size: 14px;
}

.text {
  font-size: 13px;
  color: var(--jd-muted);
}

.panel {
  padding: 32px;
  background: var(--jd-overlay);
  border: 1px solid var(--jd-line-light);
}

.title {
  margin-top: 10px;
  font-size: 32px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--jd-muted);
  font-size: 14px;
}

.footer {
  margin-top: 16px;
  font-size: 13px;
  color: var(--jd-muted);
}

@media (max-width: 820px) {
  .auth-page {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 24px 16px;
  }

  .name {
    font-size: 44px;
  }

  .points {
    display: none;
  }
}
</style>
