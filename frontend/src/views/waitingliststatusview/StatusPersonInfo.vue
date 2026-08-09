/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import StatusFieldRow from './StatusFieldRow.vue'
import type { WaitingListPublicStatus } from '@/api/waitingList'

const props = defineProps<{ status: WaitingListPublicStatus }>()

const { t } = useI18n()

function guardianLabel(g: { firstname: string; lastname: string; email: string }): string {
  const name = `${g.firstname ?? ''} ${g.lastname ?? ''}`.trim()
  return name || g.email
}
</script>

<template>
  <StatusFieldRow :label="t('waitingList.firstname')" :value="props.status.firstname" />
  <StatusFieldRow :label="t('waitingList.lastname')" :value="props.status.lastname || '-'" />
  <StatusFieldRow v-if="props.status.email" wide :label="t('waitingList.email')" :value="props.status.email" />
  <StatusFieldRow
    v-if="props.status.guardians && props.status.guardians.length > 0"
    wide
    :label="t('waitingList.guardians')"
  >
    <span v-for="(g, i) in props.status.guardians" :key="i" class="ml-1 font-medium">
      {{ guardianLabel(g) }}{{ g.phone ? ` (${g.phone})` : '' }}{{ i < props.status.guardians.length - 1 ? ', ' : '' }}
    </span>
  </StatusFieldRow>
  <StatusFieldRow
    v-else-if="props.status.parentName"
    :label="t('waitingList.parentName')"
    :value="props.status.parentName"
  />
</template>
