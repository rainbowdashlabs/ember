/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'

/** Back and forward through the pages of the account list, with where the reader is. */
defineProps<{
  /** The page shown, counted from zero. */
  page: number
  pages: number
  total: number
  busy: boolean
}>()

const emit = defineEmits<{
  go: [page: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <MutedText size="sm" data-testid="account-pager">
      {{ t('adminAccounts.pager', {page: page + 1, pages, total}) }}
    </MutedText>
    <ButtonRow pair align="end">
      <SecondaryButton :disabled="busy || page === 0" :icon="['fas', 'chevron-left']" @click="emit('go', page - 1)">
        {{ t('adminAccounts.previous') }}
      </SecondaryButton>
      <SecondaryButton :disabled="busy || page + 1 >= pages" :icon="['fas', 'chevron-right']" @click="emit('go', page + 1)">
        {{ t('adminAccounts.next') }}
      </SecondaryButton>
    </ButtonRow>
  </div>
</template>
