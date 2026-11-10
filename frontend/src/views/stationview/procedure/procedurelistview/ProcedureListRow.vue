/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {ProcedureStatus, type Procedure} from '@/api/generated/schema'

/**
 * One procedure in a list, by name, state and description, leading to its page. What a list shows
 * beside that, such as the due date or a delete button, goes into the slot. Attributes such as a test id
 * land on the card rather than on the link around it.
 */
defineOptions({inheritAttrs: false})

defineProps<{
  procedure: Procedure
}>()

const {t} = useI18n()
</script>

<template>
  <RowLink :to="{name: 'procedure-detail', params: {id: procedure.id}}">
    <NeutralContainer v-bind="$attrs"
                      class="flex items-center gap-3 cursor-pointer hover:border-[var(--color-primary)] transition-colors group">
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2">
          <span class="font-medium">{{ procedure.name }}</span>
          <SuccessBadge v-if="procedure.status === ProcedureStatus.RESOLVED">{{ t('procedures.resolved') }}</SuccessBadge>
          <PrimaryBadge v-else>{{ t('procedures.open') }}</PrimaryBadge>
        </div>
        <div v-if="procedure.description" class="text-sm text-[var(--text-muted)] truncate">{{ procedure.description }}</div>
      </div>
      <slot/>
    </NeutralContainer>
  </RowLink>
</template>
