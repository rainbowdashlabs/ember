/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  name: string
  lostNote?: string
  inventoryId: string
  size: string
  assignee?: string
  faded?: boolean
}>()
</script>

<template>
  <TRow :class="{ 'opacity-60': faded }">
    <Td class="font-medium">
      {{ name }}
      <span v-if="lostNote" class="ml-2 text-xs text-error font-normal">{{ lostNote }}</span>
    </Td>
    <Td muted>{{ inventoryId }}</Td>
    <Td><SizeBadge>{{ size }}</SizeBadge></Td>
    <Td>
      <span v-if="assignee" class="text-primary font-medium">{{ assignee }}</span>
      <span v-else class="text-(--text-muted)">&ndash;</span>
    </Td>
    <Td align="right">
      <div class="flex items-center justify-end gap-0.5">
        <EditButton/>
        <DeleteButton/>
      </div>
    </Td>
  </TRow>
</template>
