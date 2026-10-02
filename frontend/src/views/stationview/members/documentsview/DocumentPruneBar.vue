/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * What can be done with the documents chosen on the page: choose every one the filter matches rather
 * than the page in front of the reader, let go of the choice, or delete what is chosen.
 */
defineProps<{
  /** How many are chosen. */
  selected: number
  /** How many the filter matches in all, across every page. */
  total: number
  busy?: boolean
}>()

const emit = defineEmits<{
  selectAll: []
  clear: []
  prune: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between gap-3 flex-wrap" data-testid="documents-prune-bar">
    <MutedText size="sm" tag="p">{{ t('documents.selected', {count: selected}) }}</MutedText>
    <ButtonRow>
      <SecondaryButton :disabled="busy || selected === total" data-testid="documents-select-all" @click="emit('selectAll')">
        {{ t('documents.selectAll', {count: total}) }}
      </SecondaryButton>
      <SecondaryButton :disabled="busy || selected === 0" @click="emit('clear')">
        {{ t('documents.clearSelection') }}
      </SecondaryButton>
      <ErrorButton
          :icon="['fas', 'trash']"
          :disabled="busy || selected === 0"
          data-testid="documents-prune"
          @click="emit('prune')"
      >
        {{ t('documents.pruneSelected') }}
      </ErrorButton>
    </ButtonRow>
  </div>
</template>
