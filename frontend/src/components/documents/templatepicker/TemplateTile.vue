/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import BareButton from '@/components/button/BareButton.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import TemplateTileBody from './TemplateTileBody.vue'

/**
 * One template as a tile. With somewhere to go, the tile is a link there, as in the template list;
 * without, it is a button that chooses the template, as in the picker. Actions besides the tile's own
 * press go in the `actions` slot, below it rather than inside it.
 */
defineProps<{
  template: DocumentTemplateSummary
  /** Where the picture of the template is served. */
  pictureUrl: string
  /** Where pressing the tile leads, or nothing where it chooses the template instead. */
  to?: RouteLocationRaw | null
  /** Whether the tile stands for a template already chosen. */
  chosen?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  choose: [template: DocumentTemplateSummary]
}>()
</script>

<template>
  <article
      :class="chosen ? 'border-primary ring-2 ring-primary/40' : 'border-bg-light-accent dark:border-bg-dark-accent'"
      class="flex flex-col overflow-hidden rounded-theme border transition-colors hover:border-primary"
      data-testid="template-tile"
  >
    <RowLink v-if="to" :to="to" class="flex-1">
      <TemplateTileBody :template="template" :picture-url="pictureUrl"/>
    </RowLink>
    <BareButton v-else class="flex-1 text-left" :disabled="disabled" data-testid="template-tile-choose"
                @click="emit('choose', template)">
      <TemplateTileBody :template="template" :picture-url="pictureUrl"/>
    </BareButton>
    <div v-if="$slots.actions" class="flex justify-end gap-1 border-t border-bg-light-accent px-2 py-1 dark:border-bg-dark-accent">
      <slot name="actions"/>
    </div>
  </article>
</template>
