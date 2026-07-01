/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ManagedFormTile from './ManagedFormTile.vue'
import type { Form } from '@/api/types'

const props = defineProps<{
  forms: Form[]
  canCreatePolls: boolean
  titleKey?: string
  statusLabel: (status: string) => string
}>()

type SortKey = 'activity' | 'created' | 'title'
const sortKey = ref<SortKey>('activity')
const sortDir = ref<'desc' | 'asc'>('desc')

const sortedForms = computed(() => {
  const list = [...props.forms]
  const dir = sortDir.value === 'asc' ? 1 : -1
  const cmpDate = (a?: string | null, b?: string | null) => {
    const av = a ? new Date(a).getTime() : 0
    const bv = b ? new Date(b).getTime() : 0
    return (av - bv) * dir
  }
  if (sortKey.value === 'title') {
    return list.sort((a, b) => a.title.localeCompare(b.title, 'de', {sensitivity: 'base'}) * dir)
  }
  if (sortKey.value === 'created') {
    return list.sort((a, b) => cmpDate(a.createdAt, b.createdAt))
  }
  return list.sort((a, b) => cmpDate(a.lastActivityAt ?? a.updatedAt, b.lastActivityAt ?? b.updatedAt))
})

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'open', form: Form): void
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'delete', form: Form): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <SubHeader>{{ t(titleKey ?? 'forms.title') }}</SubHeader>
      <PrimaryButton v-if="canCreatePolls" :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('forms.create') }}
      </PrimaryButton>
    </div>

    <div v-if="forms.length > 0" class="flex flex-wrap items-end gap-3">
      <div>
        <FieldLabel>{{ t('forms.sortBy') }}</FieldLabel>
        <SelectInput v-model="sortKey">
          <option value="activity">{{ t('forms.sortActivity') }}</option>
          <option value="created">{{ t('forms.sortCreated') }}</option>
          <option value="title">{{ t('forms.sortTitle') }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel>{{ t('forms.sortOrder') }}</FieldLabel>
        <SelectInput v-model="sortDir">
          <option value="desc">{{ t('forms.sortDesc') }}</option>
          <option value="asc">{{ t('forms.sortAsc') }}</option>
        </SelectInput>
      </div>
    </div>

    <EmptyState compact v-if="forms.length === 0">{{ t('forms.noForms') }}</EmptyState>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <ManagedFormTile
        v-for="form in sortedForms"
        :key="form.id"
        :form="form"
        :can-create-polls="canCreatePolls"
        :status-label="statusLabel"
        @open="emit('open', $event)"
        @publish="emit('publish', $event)"
        @close="emit('close', $event)"
        @edit="emit('edit', $event)"
        @analytics="emit('analytics', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </div>
</template>
