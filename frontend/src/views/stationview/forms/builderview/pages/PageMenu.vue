/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import { useEditorActionsMenu } from '@/components/content/blockeditor/useEditorActionsMenu'

/**
 * What can be done to a page as a whole: moving it past its neighbours, and removing it. Removing a
 * page keeps its questions, which join the page above.
 */
defineProps<{
  first: boolean
  last: boolean
}>()

const emit = defineEmits<{
  move: [direction: -1 | 1]
  remove: []
}>()

const { t } = useI18n()

const { open, rootRef, toggle, close } = useEditorActionsMenu()

function take(action: () => void) {
  action()
  close()
}
</script>

<template>
  <div ref="rootRef" class="relative">
    <IconButton :icon="['fas', 'ellipsis']" :label="t('forms.pages.menu')" data-testid="page-menu-trigger"
                class="text-(--text-muted) hover:text-(--text)" @click="toggle"/>
    <div v-if="open" class="absolute right-0 top-full z-20 mt-1 w-64 rounded-theme border border-(--border) bg-(--bg) py-1 shadow-lg">
      <DropdownMenuItem :icon="['fas', 'chevron-up']" :disabled="first" @click="take(() => emit('move', -1))">
        {{ t('forms.pages.moveUp') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'chevron-down']" :disabled="last" @click="take(() => emit('move', 1))">
        {{ t('forms.pages.moveDown') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'trash']" destructive @click="take(() => emit('remove'))">
        {{ t('forms.pages.remove') }}
      </DropdownMenuItem>
    </div>
  </div>
</template>
