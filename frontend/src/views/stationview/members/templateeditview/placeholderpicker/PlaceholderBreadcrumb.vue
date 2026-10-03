/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseButton from '@/components/button/BaseButton.vue'

/**
 * The steps the picker has taken, from the category on. Choosing one goes back to it.
 */
defineProps<{
  steps: string[]
}>()

const emit = defineEmits<{
  back: [depth: number]
}>()

const {t} = useI18n()
</script>

<template>
  <nav class="flex flex-wrap items-center gap-1 text-sm" :aria-label="t('documentTemplates.placeholderPicker.path')"
       data-testid="placeholder-picker-path">
    <template v-for="(step, depth) in steps" :key="depth">
      <span v-if="depth > 0" class="text-(--text-muted)" aria-hidden="true">›</span>
      <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
                  :class="depth === steps.length - 1 ? '!text-primary !font-medium' : '!text-(--text-muted)'"
                  @click="emit('back', depth)">
        {{ step }}
      </BaseButton>
    </template>
  </nav>
</template>
