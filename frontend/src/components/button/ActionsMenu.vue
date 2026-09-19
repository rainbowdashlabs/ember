/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import IconButton from './IconButton.vue'
import Popover from '@/components/feedback/Popover.vue'

/**
 * The menu of what can be done to one row, or to the page a toolbar belongs to.
 *
 * <p>Two conventions for what goes in the slot: the destructive entry comes last and is coloured,
 * the way the row menu of the item table does it, because a full width row directly under a
 * harmless one reads as harmless otherwise. And the action the reader came for is not in here at
 * all - it stays a button of its own beside the menu.
 *
 * <p>The trigger stops its click, because the menu often sits in a row that opens something when
 * clicked.
 */
withDefaults(defineProps<{
  label: string
  icon?: string[]
  /** Tells two menus on one page apart. The panel carries it, the trigger carries it with `-trigger`. */
  testId?: string
}>(), {
  icon: () => ['fas', 'ellipsis-vertical'],
  testId: 'actions-menu',
})
</script>

<template>
  <Popover :label="label" :test-id="testId" class="inline-block" role="menu">
    <template #trigger="{toggle, triggerAttrs}">
      <IconButton
          :icon="icon"
          :label="label"
          :data-testid="`${testId}-trigger`"
          v-bind="triggerAttrs"
          class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
          @click.stop="toggle"
      />
    </template>
    <slot/>
  </Popover>
</template>
