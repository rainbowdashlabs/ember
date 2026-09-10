/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

/** What the store is narrowed by, and the way into it. */
const search = defineModel<string>('search', {required: true})
const members = defineModel<string[]>('members', {required: true})

/**
 * Whether only the documents that name nobody are shown.
 *
 * <p>Without this the split the store has always had is invisible: a contract and a medical
 * certificate sit in one list, indistinguishable, and the paperwork that belongs to the station
 * rather than to a person cannot be found at all.
 */
const unbound = defineModel<boolean>('unbound', {required: true})

defineProps<{
  memberOptions: { value: string; label: string }[]
  canUpload?: boolean
}>()

const emit = defineEmits<{
  searchInput: []
  upload: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-end justify-between gap-3 flex-wrap">
      <div class="flex items-end gap-3 flex-wrap">
        <div class="space-y-1">
          <FieldLabel>{{ t('documents.search') }}</FieldLabel>
          <TextInput
              v-model="search"
              :placeholder="t('documents.searchPlaceholder')"
              class="min-w-64"
              @input="emit('searchInput')"
          />
        </div>
        <div class="space-y-1 min-w-56">
          <FieldLabel>{{ t('documents.filterMember') }}</FieldLabel>
          <MultiSelectDropdown
              v-model="members"
              :options="memberOptions"
              :placeholder="t('documents.allMembers')"
              searchable
          />
        </div>
      </div>
      <div class="flex items-center gap-3">
        <SelectionToggleButton :selected="unbound" data-testid="documents-unbound" @toggle="unbound = !unbound">
          {{ t('documents.onlyUnbound') }}
        </SelectionToggleButton>
        <PrimaryButton v-if="canUpload" :icon="['fas', 'upload']" @click="emit('upload')">
          {{ t('documents.upload') }}
        </PrimaryButton>
      </div>
    </div>
    <MutedText size="sm">{{ t('documents.searchHint') }}</MutedText>
  </NeutralContainer>
</template>
