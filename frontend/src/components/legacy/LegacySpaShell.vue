/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {createApp, onMounted, onUnmounted, ref, h, defineComponent} from 'vue'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'
import router from '~/router'
import i18n from '~/i18n'
import App from '~/LegacyApp.vue'

const container = ref<HTMLDivElement | null>(null)
let app: ReturnType<typeof createApp> | null = null

onMounted(async () => {
  if (!container.value) return
  app = createApp(App)
      .component('font-awesome-icon', FontAwesomeIcon)
      .use(router)
      .use(i18n)
  await router.isReady()
  app.mount(container.value)
})

onUnmounted(() => {
  app?.unmount()
  app = null
})
</script>

<template>
  <div ref="container" />
</template>
