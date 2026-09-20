/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * The way back and forward through a presentation, for a reader who is not using the keys or a swipe.
 *
 * <p>It fades with the rest of the controls, and the click is stopped here so that pressing a button
 * does not also read as a press on the slide, which would turn two pages at once.
 */
defineProps<{
  currentPage: number
  totalPages: number
  visible: boolean
}>()

const emit = defineEmits<{previous: []; next: []}>()

const {t} = useI18n()
</script>

<template>
  <div
      class="absolute bottom-4 flex items-center gap-4 transition-opacity duration-300"
      :class="visible ? 'opacity-100' : 'opacity-0 pointer-events-none'"
  >
    <IconButton
        :icon="['fas', 'chevron-left']" :label="t('common.previous')"
        class="!text-white !bg-white/20 !p-3 rounded-full" :disabled="currentPage <= 1"
        @click.stop="emit('previous')"
    />
    <IconButton
        :icon="['fas', 'chevron-right']" :label="t('common.next')"
        class="!text-white !bg-white/20 !p-3 rounded-full" :disabled="currentPage >= totalPages"
        @click.stop="emit('next')"
    />
  </div>
</template>
