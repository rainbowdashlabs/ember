/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import FederatedBoardSearchResults from '@/views/stationview/federation/federatedboardview/FederatedBoardSearchResults.vue'
import type {BoardTicket} from '@/api/boards'
import type {TicketPriorityName} from '@/api/boards'

defineProps<{
  boardName: string
  shortKey: string
  isReadOnly: boolean
  isFull: boolean
  canManageBoards: boolean
  searchResults: BoardTicket[] | null
  laneName: (laneId: number) => string
  priorityIcon: (priority: TicketPriorityName) => string[]
  priorityColor: (priority: TicketPriorityName) => string
}>()

const emit = defineEmits<{
  'search-input': []
  'pick-result': [ticket: BoardTicket]
  'open-override': []
  'open-create': []
}>()

const searchQuery = defineModel<string>('searchQuery', {required: true})

const {t} = useI18n()
const router = useRouter()
</script>

<template>
  <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
    <div class="flex items-center gap-3">
      <IconButton :icon="['fas', 'arrow-left']" :label="t('common.back')"
                  @click="router.push('/station/federation/boards')"/>
      <SectionHeader>{{ boardName }}</SectionHeader>
      <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ shortKey }}</span>
      <InfoBadge v-if="isReadOnly" class="inline-flex items-center gap-1">
        <font-awesome-icon :icon="['fas', 'eye']" class="text-[0.65rem]"/>
        {{ t('boards.readOnlyBadge') }}
      </InfoBadge>
      <SuccessBadge v-else class="inline-flex items-center gap-1">
        <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]"/>
        {{ t('boards.fullAccessBadge') }}
      </SuccessBadge>
    </div>
    <div class="flex items-center gap-2">
      <div class="relative">
        <SearchInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-96"
                     @input="emit('search-input')"/>
        <FederatedBoardSearchResults
            v-if="searchResults"
            :results="searchResults"
            :short-key="shortKey"
            :lane-name="laneName"
            :priority-icon="priorityIcon"
            :priority-color="priorityColor"
            @pick="emit('pick-result', $event)"
        />
      </div>
      <SecondaryButton v-if="canManageBoards" @click="emit('open-override')">
        <font-awesome-icon :icon="['fas', 'shield']" class="mr-1"/>
        {{ t('boards.accessOverride') }}
      </SecondaryButton>
      <PrimaryButton v-if="isFull" @click="emit('open-create')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('boards.createTicket') }}
      </PrimaryButton>
    </div>
  </div>
</template>
