/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * Turning the pages of a document: back, which page is shown, and on. Neither button leaves the
 * document.
 */
const page = defineModel<number>('page', {required: true})

defineProps<{
  pageCount: number
  /** Which page is shown, in the words of the screen. */
  label: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-2">
    <IconButton :icon="['fas', 'chevron-left']" :label="t('common.previous')" :disabled="page <= 1" @click="page--"/>
    <span class="text-sm">{{ label }}</span>
    <IconButton :icon="['fas', 'chevron-right']" :label="t('common.next')" :disabled="page >= pageCount" @click="page++"/>
  </div>
</template>
