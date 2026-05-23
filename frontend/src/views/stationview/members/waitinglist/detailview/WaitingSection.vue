/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/types'
import { computed } from 'vue'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  fields: WaitingListField[]
  visibleFieldIds: Set<number>
  isMobile: boolean
  showFieldToggle: boolean
}>()

const emit = defineEmits<{
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
  toggleField: [fieldId: number]
  toggleFieldMenu: []
  addEntry: []
}>()

const { t } = useI18n()

const visibleFields = computed(() => props.fields.filter(f => props.visibleFieldIds.has(f.id)))

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function getEntryFieldValue(item: WaitingListEntryWithScore, fieldId: number): string {
  return item.values.find(v => v.fieldId === fieldId)?.value ?? ''
}

function statusBadgeComponent(status: string) {
  if (status === 'JOINED') return SuccessBadge
  if (status === 'WITHDRAWN') return ErrorBadge
  if (status === 'TESTING') return PrimaryBadge
  if (status === 'INVITED') return InfoBadge
  return SecondaryBadge
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SubHeader>{{ t('waitingList.sectionWaiting') }} ({{ entries.length }})</SubHeader>
      <div class="flex items-center gap-2 w-full sm:w-auto">
        <div class="relative flex-1 sm:flex-initial">
          <SecondaryButton :full-width="isMobile" @click="emit('toggleFieldMenu')">
            <font-awesome-icon :icon="['fas', 'table-columns']" class="mr-2" />
            {{ t('waitingList.columns') }}
          </SecondaryButton>
          <div v-if="showFieldToggle" class="absolute right-0 top-full mt-1 z-20 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-2 min-w-48">
            <div v-for="field in fields" :key="field.id" class="flex items-center gap-2 px-2 py-1 rounded hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="emit('toggleField', field.id)">
              <font-awesome-icon :icon="['fas', visibleFieldIds.has(field.id) ? 'square-check' : 'square']" class="text-primary" />
              <span class="text-sm">{{ field.name }}</span>
            </div>
            <div v-if="fields.length === 0" class="text-xs text-(--text-muted) px-2 py-1">{{ t('waitingList.noFields') }}</div>
          </div>
        </div>
        <PrimaryButton :full-width="isMobile" class="flex-1 sm:flex-initial" @click="emit('addEntry')">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('waitingList.addEntry') }}
        </PrimaryButton>
      </div>
    </div>

    <div v-if="entries.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('waitingList.noEntries') }}
    </div>

    <!-- Desktop table -->
    <div v-if="!isMobile && entries.length > 0" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="py-2 px-2 font-medium">#</th>
            <th class="py-2 px-2 font-medium">{{ t('waitingList.firstname') }}</th>
            <th class="py-2 px-2 font-medium">{{ t('waitingList.lastname') }}</th>
            <th class="py-2 px-2 font-medium">{{ t('waitingList.parentName') }}</th>
            <th class="py-2 px-2 font-medium">{{ t('waitingList.email') }}</th>
            <th v-for="vf in visibleFields" :key="vf.id" class="py-2 px-2 font-medium">{{ vf.name }}</th>
            <th class="py-2 px-2 font-medium">{{ t('waitingList.status') }}</th>
            <th class="py-2 px-2 font-medium text-right">{{ t('waitingList.score') }}</th>
            <th class="py-2 px-2 font-medium text-right">{{ t('waitingList.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in entries"
            :key="item.entry.id"
            class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30"
          >
            <td class="py-2 px-2 text-(--text-muted)">{{ index + 1 }}</td>
            <td class="py-2 px-2">
              <span class="text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
                {{ item.entry.firstname }}
              </span>
            </td>
            <td class="py-2 px-2">{{ item.entry.lastname }}</td>
            <td class="py-2 px-2">{{ item.entry.parentName }}</td>
            <td class="py-2 px-2">{{ item.entry.email }}</td>
            <td v-for="vf in visibleFields" :key="vf.id" class="py-2 px-2 text-(--text-muted)">{{ getEntryFieldValue(item, vf.id) || '–' }}</td>
            <td class="py-2 px-2">
              <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
            </td>
            <td class="py-2 px-2 text-right font-mono">{{ item.score }}</td>
            <td class="py-2 px-2">
              <div class="flex items-center justify-end gap-1">
                <IconButton
                  v-if="item.entry.status === 'WAITING'"
                  icon="paper-plane"
                  :label="t('waitingList.invite')"
                  @click="emit('invite', item.entry.id)"
                />
                <IconButton
                  v-if="item.entry.status === 'INVITED'"
                  icon="play"
                  :label="t('waitingList.startTesting')"
                  @click="emit('moveToTesting', item.entry.id)"
                />
                <EditButton @click="emit('navigateToEntry', item.entry.id)" />
                <DeleteButton @click="emit('deleteEntry', item)" />
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Mobile cards -->
    <div v-if="isMobile && entries.length > 0" class="space-y-3">
      <NeutralContainer
        v-for="(item, index) in entries"
        :key="item.entry.id"
        class="space-y-2"
      >
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xs text-(--text-muted) mr-2">#{{ index + 1 }}</span>
            <span class="font-semibold text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
              {{ entryFullName(item) }}
            </span>
          </div>
          <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
        </div>
        <div class="text-sm text-(--text-muted)">
          {{ item.entry.parentName }} &middot; {{ item.entry.email }}
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ item.score }}</span></span>
          <div class="flex items-center gap-1">
            <IconButton
              v-if="item.entry.status === 'WAITING'"
              icon="paper-plane"
              :label="t('waitingList.invite')"
              @click="emit('invite', item.entry.id)"
            />
            <IconButton
              v-if="item.entry.status === 'INVITED'"
              icon="play"
              :label="t('waitingList.startTesting')"
              @click="emit('moveToTesting', item.entry.id)"
            />
            <EditButton @click="emit('navigateToEntry', item.entry.id)" />
            <DeleteButton @click="emit('deleteEntry', item)" />
          </div>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
