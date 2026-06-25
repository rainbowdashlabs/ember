/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {useI18n} from 'vue-i18n'

defineProps<{
  title: string
  note?: string
  vars: ReadonlyArray<{ name: string; default: string; desc: string }>
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <p class="font-semibold text-sm mt-4 mb-2">{{ title }}</p>
    <p v-if="note" class="text-sm mb-2">{{ note }}</p>
    <div class="space-y-2">
      <NeutralContainer v-for="v in vars" :key="v.name" class="p-3">
        <div class="flex flex-wrap items-baseline gap-2">
          <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
          <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
        </div>
        <p class="text-sm mt-1">{{ v.desc }}</p>
      </NeutralContainer>
    </div>
  </div>
</template>
