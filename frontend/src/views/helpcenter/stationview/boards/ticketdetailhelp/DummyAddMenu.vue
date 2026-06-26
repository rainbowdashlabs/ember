/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()

const items = [
  {icon: 'list-check', key: 'boards.checklist'},
  {icon: 'link', key: 'boards.addLink'},
  {icon: 'globe', key: 'boards.addWeblink'},
  {icon: 'paperclip', key: 'boards.addAttachment'},
  {icon: 'book', key: 'boards.addKbLink'},
]
</script>

<template>
  <NeutralContainer class="w-48">
    <div class="space-y-0">
      <div v-for="i in items" :key="i.key" class="px-3 py-2 text-sm flex items-center gap-2">
        <font-awesome-icon :icon="['fas', i.icon]" class="text-(--text-muted) w-4"/>
        {{ t(i.key) }}
      </div>
    </div>
  </NeutralContainer>
</template>
