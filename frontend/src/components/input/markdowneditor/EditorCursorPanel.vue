/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

/**
 * A panel of the text editor that opens at the cursor, such as the one for a link: its icon, its
 * title and a close button above whatever it asks for.
 *
 * <p>It hangs off an empty point where the cursor stands and is rendered at the end of the page, so
 * neither the edge of the text field nor a dialog around the editor cuts it off. The close button,
 * escape and a press outside the panel all cancel it.
 */
defineProps<{
  /** Where the cursor stands, measured from the top left corner of the editor. */
  position: { top: number; left: number }
  icon: string[]
  title: string
}>()

const emit = defineEmits<{
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <FloatingPanel
    :open="true"
    :label="title"
    role="dialog"
    align="start"
    panel-class="w-80 p-3 space-y-2"
    class="absolute"
    :style="{ top: `${position.top}px`, left: `${position.left}px` }"
    @dismissed="emit('cancel')"
  >
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="icon" class="text-[var(--color-primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">{{ title }}</span>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.close')" hover="text" @click="emit('cancel')"/>
    </div>
    <slot />
  </FloatingPanel>
</template>
