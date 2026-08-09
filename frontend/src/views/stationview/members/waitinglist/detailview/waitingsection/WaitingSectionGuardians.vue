/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { WaitingListEntryWithScore } from '@/api/waitingList'

const props = defineProps<{
  item: WaitingListEntryWithScore
  layout?: 'inline' | 'stack'
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <template v-if="props.item.guardians && props.item.guardians.length > 0">
      <span class="text-xs font-semibold uppercase text-(--text-muted)">{{ t('waitingList.guardians') }}</span>
      <template v-if="props.layout === 'stack'">
        <div v-for="g in props.item.guardians" :key="g.id" class="text-sm flex flex-col">
          <span class="font-medium">{{ `${g.firstname} ${g.lastname}`.trim() || '-' }}</span>
          <span class="text-(--text-muted)">{{ g.email }}{{ g.phone ? ` · ${g.phone}` : '' }}</span>
        </div>
      </template>
      <template v-else>
        <div v-for="g in props.item.guardians" :key="g.id" class="flex items-center gap-4 text-sm">
          <span class="font-medium">{{ `${g.firstname} ${g.lastname}`.trim() || '-' }}</span>
          <span class="text-(--text-muted)">{{ g.email || '-' }}</span>
          <span v-if="g.phone" class="text-(--text-muted)">{{ g.phone }}</span>
        </div>
      </template>
    </template>
    <span v-else class="text-sm text-(--text-muted)">{{ t('waitingList.noGuardians') }}</span>
  </div>
</template>
