/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

defineProps<{
  osIcon: [string, string]
  browserIcon: [string, string]
  browserName: string
  osName: string
  lastActive: string
  created: string
  location: string
  current?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex items-center justify-between">
    <div class="flex items-center gap-2">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="osIcon" class="h-5 w-5 text-(--text-muted)"/>
        <font-awesome-icon :icon="browserIcon" class="h-5 w-5 text-(--text-muted)"/>
      </div>
      <div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium">{{ browserName }}</span>
          <span class="text-xs text-(--text-muted)">{{ osName }}</span>
          <span v-if="current" class="text-xs font-semibold text-primary">
            {{ t('userSettings.currentSession') }}
          </span>
        </div>
        <p class="text-xs text-(--text-muted)">
          {{ t('userSettings.lastActive') }}: {{ lastActive }}
          <span class="ml-2">{{ t('userSettings.created') }}: {{ created }}</span>
          <span class="ml-2">
            <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ location }}
          </span>
        </p>
      </div>
    </div>
    <DeleteButton v-if="!current" disabled/>
  </NeutralContainer>
</template>
