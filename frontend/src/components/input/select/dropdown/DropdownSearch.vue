/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ListboxFilter} from 'reka-ui'

/**
 * The search at the head of a dropdown's list. The focus stays here while the arrow keys move the
 * highlight through the list below, and typing puts the highlight back on the first match.
 */
const search = defineModel<string>({required: true})

defineProps<{
  placeholder?: string
}>()
</script>

<template>
  <div class="border-b border-(--border) p-2">
    <ListboxFilter
        v-model="search"
        :placeholder="placeholder"
        class="w-full rounded-theme border border-(--border) bg-transparent px-2 py-1.5 text-sm text-(--text) outline-none focus:border-primary"
    />
  </div>
</template>
