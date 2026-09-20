/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * What a presentation says about itself while it is being shown: its name, where in it the reader is,
 * and the way out.
 *
 * <p>It fades rather than disappears, because a bar that leaves the layout would move the slide under
 * it every time the reader stopped touching the screen.
 */
defineProps<{
  title: string
  currentPage: number
  totalPages: number
  visible: boolean
}>()

const emit = defineEmits<{close: []}>()

const {t} = useI18n()
</script>

<template>
  <div
      class="absolute top-0 left-0 right-0 flex items-center justify-between px-4 py-2 bg-black/60 text-white z-10 transition-opacity duration-300"
      :class="visible ? 'opacity-100' : 'opacity-0 pointer-events-none'"
  >
    <span class="text-sm truncate">{{ title }}</span>
    <div class="flex items-center gap-4">
      <span v-if="totalPages > 0" class="text-sm">{{ currentPage }} / {{ totalPages }}</span>
      <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" class="!text-white" @click.stop="emit('close')"/>
    </div>
  </div>
</template>
