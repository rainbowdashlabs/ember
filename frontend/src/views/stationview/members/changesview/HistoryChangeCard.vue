/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {ProfileFieldChange} from '@/api/types'
import ChangeValueDiff from './ChangeValueDiff.vue'
import AcknowledgementList from './AcknowledgementList.vue'

const {t} = useI18n()

defineProps<{
  change: ProfileFieldChange
  formatDate: (dateStr?: string) => string
}>()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span v-if="change.memberName" class="font-semibold text-sm text-primary">
          <MemberName :identity="change.memberIdentity ?? null"/>
        </span>
        <span class="font-medium text-sm">{{ change.fieldName }}</span>
        <span class="text-xs text-(--text-muted)">{{ formatDate(change.changedAt) }}</span>
        <span class="text-xs text-(--text-muted)">
          {{ t('memberDetail.changedBy') }}: {{ change.changedByName }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <SuccessBadge v-if="change.acknowledgements.length > 0">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('memberDetail.acknowledged') }}
        </SuccessBadge>
        <ErrorBadge v-else-if="change.requiresAcknowledgement">
          {{ t('memberDetail.notAcknowledged') }}
        </ErrorBadge>
      </div>
    </div>

    <ChangeValueDiff :old-value="change.oldValue" :new-value="change.newValue"/>

    <AcknowledgementList :acknowledgements="change.acknowledgements" :format-date="formatDate"/>
  </NeutralContainer>
</template>
