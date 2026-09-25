/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {Failure} from '@/util/failure'

/**
 * Renders exactly one of the four states an async region can be in: loading, failed, loaded but
 * empty, or loaded with content. Replaces the hand-written
 * `<Spinner v-if="loading"/><Alert v-else-if="error"/><EmptyState v-else-if="!items.length"/>`
 * ladder that views used to repeat.
 *
 * Pairs with {@link useAsyncLoader}: bind `loading` and `failure` straight from its return value
 * and `empty` to the emptiness of whatever the loader filled.
 *
 * Every branch is overridable through the `loading`, `error` and `empty` slots for views that need
 * a bespoke placeholder while keeping the branch logic here.
 *
 * <p>Pass `failure` rather than `error` wherever the loader has one. The failed branch used to draw
 * a plain alert holding one sentence, which took the guidance and the offer to report it back off
 * again at the last step: every screen that had been told what went wrong showed only that
 * something had. `error` stays for callers holding nothing better than a string.
 */
withDefaults(defineProps<{
  loading: boolean
  /** The load failure, described, which is what the loader composables hand back. */
  failure?: Failure | null
  /** Load failure message, for a caller that has only a string. Empty means "no error". */
  error?: string
  /** Whether the successful load produced nothing to show. */
  empty?: boolean
  /** Localized text for the default empty branch. Ignored when the `empty` slot is used. */
  emptyMessage?: string
  emptyCompact?: boolean
  spinnerSize?: 'sm' | 'md' | 'lg'
}>(), {
  failure: null,
  error: '',
  empty: false,
  emptyMessage: '',
  emptyCompact: false,
  spinnerSize: 'lg',
})
</script>

<template>
  <div v-if="loading" class="flex justify-center py-8">
    <slot name="loading">
      <Spinner :size="spinnerSize"/>
    </slot>
  </div>
  <slot v-else-if="failure || error" name="error">
    <FailureAlert :failure="failure" :message="error"/>
  </slot>
  <slot v-else-if="empty" name="empty">
    <EmptyState :compact="emptyCompact" :message="emptyMessage"/>
  </slot>
  <slot v-else/>
</template>
