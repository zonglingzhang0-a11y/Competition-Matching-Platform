<script setup lang="ts">
/**
 * 页头：上面一行"竞搭 / 路径"，下面大号英文标题加中文，右边放操作按钮，底下一条粗线。
 * path 不传就用中文标题。
 */
defineProps<{ en: string; zh: string; path?: string }>()
</script>

<template>
  <header class="page-header">
    <div class="kicker">竞搭 <span class="zh">/ {{ path ?? zh }}</span></div>
    <div class="row">
      <h1 class="display title">{{ en }}<small>{{ zh }}</small></h1>
      <div class="actions"><slot /></div>
    </div>
    <hr class="rule" />
  </header>
</template>

<style scoped>
.page-header {
  margin-bottom: 16px;
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.title {
  font-size: 30px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule {
  margin: 12px 0 0;
}

@media (max-width: 640px) {
  .title {
    font-size: 22px;
  }
}
</style>
