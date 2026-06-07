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
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/types'
import { ref, computed } from 'vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  fields: WaitingListField[]
  visibleFieldIds: Set<number>
  isMobile: boolean
  showFieldToggle: boolean
  readonly?: boolean
  canAdd?: boolean
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
const expandedId = ref<number | null>(null)

function toggleExpand(entryId: number) {
  expandedId.value = expandedId.value === entryId ? null : entryId
}

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
          <SecondaryButton :icon="['fas', 'table-columns']" :full-width="isMobile" @click="emit('toggleFieldMenu')">
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
        <PrimaryButton v-if="canAdd" :icon="['fas', 'plus']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="emit('addEntry')">
          {{ t('waitingList.addEntry') }}
        </PrimaryButton>
      </div>
    </div>

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noEntries') }}</EmptyState>

    <!-- Desktop table -->
    <div v-if="!isMobile && entries.length > 0" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th class="\!px-2">#</th>
            <Th class="\!px-2">{{ t('waitingList.firstname') }}</th>
            <Th class="\!px-2">{{ t('waitingList.lastname') }}</th>
            <Th class="px-2!" v-for="vf in visibleFields" :key="vf.id">{{ vf.name }}</Th>
            <Th class="px-2!">{{ t('waitingList.status') }}</Th>
            <Th class="px-2! text-right">{{ t('waitingList.score') }}</Th>
            <Th v-if="!readonly" class="px-2! text-right">{{ t('waitingList.actions') }}</Th>
          </THead>
        </thead>
        <tbody>
          <template v-for="(item, index) in entries" :key="item.entry.id">
            <TRow class="hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="toggleExpand(item.entry.id)">
              <td class="py-2 px-2 text-(--text-muted)">{{ index + 1 }}</td>
              <td class="py-2 px-2">
                <span class="text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
                  {{ item.entry.firstname }}
                </span>
              </td>
              <td class="py-2 px-2">{{ item.entry.lastname }}</td>
              <td v-for="vf in visibleFields" :key="vf.id" class="py-2 px-2 text-(--text-muted)">{{ getEntryFieldValue(item, vf.id) || '–' }}</td>
              <td class="py-2 px-2">
                <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
              </td>
              <td class="py-2 px-2 text-right font-mono">{{ item.score }}</td>
              <td v-if="!readonly" class="py-2 px-2">
                <div class="flex items-center justify-end gap-1">
                  <IconButton
                    v-if="item.entry.status === 'WAITING'"
                    icon="paper-plane"
                    :label="t('waitingList.invite')"
                    @click.stop="emit('invite', item.entry.id)"
                  />
                  <IconButton
                    v-if="item.entry.status === 'INVITED'"
                    icon="play"
                    :label="t('waitingList.startTesting')"
                    @click.stop="emit('moveToTesting', item.entry.id)"
                  />
                  <EditButton @click.stop="emit('navigateToEntry', item.entry.id)" />
                  <DeleteButton @click.stop="emit('deleteEntry', item)" />
                </div>
              </td>
            </TRow>
            <tr v-if="expandedId === item.entry.id">
              <td :colspan="5 + visibleFields.length + (readonly ? 0 : 1)" class="px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
                <div class="space-y-2">
                  <template v-if="item.guardians && item.guardians.length > 0">
                    <span class="text-xs font-semibold uppercase text-(--text-muted)">{{ t('waitingList.guardians') }}</span>
                    <div v-for="g in item.guardians" :key="g.id" class="flex items-center gap-4 text-sm">
                      <span class="font-medium">{{ g.name || '-' }}</span>
                      <span class="text-(--text-muted)">{{ g.email || '-' }}</span>
                      <span v-if="g.phone" class="text-(--text-muted)">{{ g.phone }}</span>
                    </div>
                  </template>
                  <span v-else class="text-sm text-(--text-muted)">{{ t('waitingList.noGuardians') }}</span>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Mobile cards -->
    <div v-if="isMobile && entries.length > 0" class="space-y-3">
      <NeutralContainer
        v-for="(item, index) in entries"
        :key="item.entry.id"
        class="space-y-2 cursor-pointer"
        @click="toggleExpand(item.entry.id)"
      >
        <div class="flex items-center justify-between">
          <div>
            <span class="text-xs text-(--text-muted) mr-2">#{{ index + 1 }}</span>
            <span class="font-semibold text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
              {{ entryFullName(item) }}
            </span>
          </div>
          <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
        </div>
        <div v-if="expandedId === item.entry.id" class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 space-y-1">
          <template v-if="item.guardians && item.guardians.length > 0">
            <span class="text-xs font-semibold uppercase text-(--text-muted)">{{ t('waitingList.guardians') }}</span>
            <div v-for="g in item.guardians" :key="g.id" class="text-sm flex flex-col">
              <span class="font-medium">{{ g.name || '-' }}</span>
              <span class="text-(--text-muted)">{{ g.email }}{{ g.phone ? ` · ${g.phone}` : '' }}</span>
            </div>
          </template>
          <span v-else class="text-sm text-(--text-muted)">{{ t('waitingList.noGuardians') }}</span>
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ item.score }}</span></span>
          <div v-if="!readonly" class="flex items-center gap-1">
            <IconButton
              v-if="item.entry.status === 'WAITING'"
              icon="paper-plane"
              :label="t('waitingList.invite')"
              @click.stop="emit('invite', item.entry.id)"
            />
            <IconButton
              v-if="item.entry.status === 'INVITED'"
              icon="play"
              :label="t('waitingList.startTesting')"
              @click.stop="emit('moveToTesting', item.entry.id)"
            />
            <EditButton @click.stop="emit('navigateToEntry', item.entry.id)" />
            <DeleteButton @click.stop="emit('deleteEntry', item)" />
          </div>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
