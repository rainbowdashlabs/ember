/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {ChecklistEntryDto, StationMember} from '@/api/types'

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  adding: boolean
  members: StationMember[]
  aliveMemberIds: Set<number>
  removedEntries: ChecklistEntryDto[]
}>()

const emit = defineEmits<{
  (e: 'submit', memberIds: number[]): void
}>()

const {t} = useI18n()

const search = ref('')
const selected = ref<Set<number>>(new Set())

const removedMemberIds = computed(() => new Set(props.removedEntries.map(e => e.memberId)))

function displayName(m: StationMember): string {
  return m.name ?? m.email ?? `#${m.id}`
}

const candidates = computed(() => {
  const query = search.value.trim().toLowerCase()
  return props.members
      .filter(m => !props.aliveMemberIds.has(m.id))
      .filter(m => !query || displayName(m).toLowerCase().includes(query))
      .slice()
      .sort((a, b) => displayName(a).localeCompare(displayName(b), 'de', {sensitivity: 'base'}))
})

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function reset() {
  search.value = ''
  selected.value = new Set()
}

function cancel() {
  visible.value = false
  reset()
}

function submit() {
  if (selected.value.size === 0) return
  emit('submit', Array.from(selected.value))
}

watch(visible, (value, previous) => {
  if (previous && !value) reset()
})
</script>

<template>
  <Modal v-model="visible" size="lg">
    <div class="space-y-3">
      <SubHeader>{{ t('checklist.addMembersTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('checklist.addMembersIntro') }}</p>
      <TextInput v-model="search" :placeholder="t('checklist.searchPlaceholder')"/>
      <div class="max-h-80 overflow-y-auto border border-bg-light-accent dark:border-bg-dark-accent rounded-theme">
        <ul>
          <li
              v-for="member in candidates"
              :key="member.id"
              class="flex items-center justify-between gap-3 p-2 border-b border-bg-light-accent dark:border-bg-dark-accent last:border-b-0 hover:bg-(--bg-light-accent) dark:hover:bg-(--bg-dark-accent) cursor-pointer"
              @click="toggle(member.id)"
          >
            <div class="flex items-center gap-2 min-w-0">
              <CheckboxInput :model-value="selected.has(member.id)" @update:model-value="toggle(member.id)"/>
              <span class="truncate">{{ displayName(member) }}</span>
            </div>
            <SecondaryBadge v-if="removedMemberIds.has(member.id)">{{ t('checklist.previouslyRemoved') }}</SecondaryBadge>
          </li>
        </ul>
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="cancel">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="adding || selected.size === 0" @click="submit">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
