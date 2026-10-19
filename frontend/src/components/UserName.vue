<script setup lang="ts">
import { useUserPageStore } from '@/stores/userPage'

/** 可以点的人名：点了打开这个人的个人资料页。放在可点击的卡片里也不会触发卡片本身的跳转 */
const props = defineProps<{ id: number; name: string }>()

const userPage = useUserPageStore()
</script>

<template>
  <button type="button" class="user-name" :title="`查看 ${props.name} 的资料`" @click.stop="userPage.open(props.id)">
    {{ props.name }}
  </button>
</template>

<style scoped>
.user-name {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: inherit;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--jd-line);
  text-underline-offset: 3px;
}

.user-name:hover {
  color: var(--jd-accent);
  text-decoration-color: currentColor;
}
</style>
