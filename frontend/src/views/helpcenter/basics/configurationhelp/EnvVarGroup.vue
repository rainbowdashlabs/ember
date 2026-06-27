/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const props = defineProps<{
  title: string
  note?: string
  vars: ReadonlyArray<{ name?: string; default: string; configKey?: string; desc: string }>
  defaultOpen?: boolean
}>()

const {t} = useI18n()
const open = ref(props.defaultOpen ?? true)
</script>

<template>
  <div class="mt-4">
    <button
        type="button"
        class="flex w-full items-center gap-2 text-left font-semibold text-sm py-1.5 rounded-theme hover:bg-(--bg-accent) transition-colors"
        @click="open = !open"
    >
      <font-awesome-icon :icon="['fas', open ? 'chevron-down' : 'chevron-right']" class="h-3 w-3 text-(--text-muted)"/>
      <span class="flex-1">{{ title }}</span>
      <span class="text-xs font-normal text-(--text-muted)">{{ vars.length }}</span>
    </button>
    <div v-if="open" class="mt-2">
      <p v-if="note" class="text-sm mb-2">{{ note }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="(v, i) in vars" :key="v.name ?? v.configKey ?? i" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code v-if="v.name" class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <code v-else-if="v.configKey" class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.configKey }}</code>
            <span v-if="v.name && v.configKey" class="text-xs text-(--text-muted)">
              {{ t('helpCenter.basics.configuration.envConfigKey') }}:
              <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.configKey }}</code>
            </span>
            <span v-if="!v.name" class="text-xs text-(--text-muted) italic">
              {{ t('helpCenter.basics.configuration.envConfigOnly') }}
            </span>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.configuration.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>
    </div>
  </div>
</template>
