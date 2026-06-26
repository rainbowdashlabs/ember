/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import { FormStatus } from '@/api/types'
import type { Form } from '@/api/types'

defineProps<{
  form: Form
  canCreatePolls: boolean
  statusLabel: (status: string) => string
  publishLabel: string
  closeLabel: string
  editLabel: string
  analyticsLabel: string
  deleteLabel: string
}>()

const emit = defineEmits<{
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'delete', form: Form): void
}>()
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ form.title }}</span>
          <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']" class="ml-1"/>
          <SuccessBadge v-if="form.status === FormStatus.OPEN">{{ statusLabel(form.status) }}</SuccessBadge>
          <ErrorBadge v-else-if="form.status === FormStatus.CLOSED">{{ statusLabel(form.status) }}</ErrorBadge>
          <InfoBadge v-else>{{ statusLabel(form.status) }}</InfoBadge>
        </div>
        <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <SecondaryButton v-if="canCreatePolls && form.status === FormStatus.DRAFT" @click="emit('publish', form)">
          {{ publishLabel }}
        </SecondaryButton>
        <SecondaryButton v-if="canCreatePolls && form.status === FormStatus.OPEN" @click="emit('close', form)">
          {{ closeLabel }}
        </SecondaryButton>
        <SecondaryButton v-if="canCreatePolls && form.status !== FormStatus.CLOSED" @click="emit('edit', form)">
          {{ editLabel }}
        </SecondaryButton>
        <SecondaryButton v-if="form.status !== FormStatus.DRAFT" @click="emit('analytics', form)">
          {{ analyticsLabel }}
        </SecondaryButton>
        <ErrorButton v-if="canCreatePolls" @click="emit('delete', form)">
          {{ deleteLabel }}
        </ErrorButton>
      </div>
    </div>
  </NeutralContainer>
</template>
