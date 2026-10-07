/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {Placeholder} from '@/api/generated/schema'
import PlaceholderRow from './PlaceholderRow.vue'

/**
 * What a search of the picker found, each placeholder with the path it stands under, so two answers of
 * the same name, the member's and a guardian's, are told apart.
 */
defineProps<{
  matches: Placeholder[]
}>()

const emit = defineEmits<{
  pick: [placeholder: Placeholder]
}>()

const {t} = useI18n()
</script>

<template>
  <p v-if="matches.length === 0" class="text-xs text-(--text-muted)">{{ t('documentTemplates.placeholderPicker.noMatches') }}</p>
  <ul v-else class="max-h-56 space-y-0.5 overflow-y-auto" data-testid="placeholder-picker-matches">
    <li v-for="placeholder in matches" :key="placeholder.key">
      <PlaceholderRow :name="placeholder.path.at(-1) ?? placeholder.label" :branch="false"
                      :detail="placeholder.path.slice(0, -1).join(' › ')"
                      :data-testid="`placeholder-${placeholder.key}`" @choose="emit('pick', placeholder)"/>
    </li>
  </ul>
</template>
