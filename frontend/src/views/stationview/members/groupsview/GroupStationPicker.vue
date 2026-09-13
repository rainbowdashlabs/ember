/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {matchesWords} from '@/util/listSearch'
import {byMemberName} from '@/util/memberOrder'
import type {MemberOption} from '@/components/input/select/memberOption'

/**
 * Putting a station into one of the association's groups.
 *
 * <p>Beside the member menu rather than inside it: a station has no face, no name colour and no kind
 * of member, so three quarters of what that menu exists to show would be blank. What the two share is
 * the search, and a list of stations is short enough that a plain one reads well.
 */
const props = defineProps<{
  stations: MemberOption[]
}>()

const emit = defineEmits<{
  (e: 'add', stationId: number): void
}>()

const {t} = useI18n()

const search = ref('')

const matching = computed(() => props.stations
    .filter(station => matchesWords(station.name, search.value.trim()))
    .toSorted(byMemberName(station => station.name)))
</script>

<template>
  <div class="space-y-2">
    <TextInput v-model="search" data-testid="station-picker-search" :placeholder="t('clusterStationGroups.searchStations')"/>
    <div class="max-h-64 space-y-1 overflow-y-auto rounded-lg border border-(--border) p-1">
      <MutedText v-if="matching.length === 0" tag="div" size="sm" class="px-3 py-2">
        {{ t('clusterStationGroups.nothingMatches') }}
      </MutedText>
      <button
          v-for="station in matching"
          :key="station.value"
          type="button"
          data-testid="station-picker-option"
          class="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm
                 transition-colors hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
          @click="emit('add', Number(station.value))"
      >
        <span class="truncate font-medium">{{ station.name }}</span>
        <font-awesome-icon :icon="['fas', 'plus']" class="shrink-0 text-sm text-primary"/>
      </button>
    </div>
  </div>
</template>
