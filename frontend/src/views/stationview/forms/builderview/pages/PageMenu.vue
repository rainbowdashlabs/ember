/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import Popover from '@/components/feedback/Popover.vue'

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
</script>

<template>
  <Popover :label="t('forms.pages.menu')" panel-class="w-64 py-1">
    <template #trigger="{ toggle, triggerAttrs }">
      <IconButton :icon="['fas', 'ellipsis']" :label="t('forms.pages.menu')" data-testid="page-menu-trigger"
                  class="text-(--text-muted) hover:text-(--text)" v-bind="triggerAttrs" @click.stop="toggle"/>
    </template>
    <DropdownMenuItem :icon="['fas', 'chevron-up']" :disabled="first" @click="emit('move', -1)">
      {{ t('forms.pages.moveUp') }}
    </DropdownMenuItem>
    <DropdownMenuItem :icon="['fas', 'chevron-down']" :disabled="last" @click="emit('move', 1)">
      {{ t('forms.pages.moveDown') }}
    </DropdownMenuItem>
    <DropdownMenuItem :icon="['fas', 'trash']" destructive @click="emit('remove')">
      {{ t('forms.pages.remove') }}
    </DropdownMenuItem>
  </Popover>
</template>
