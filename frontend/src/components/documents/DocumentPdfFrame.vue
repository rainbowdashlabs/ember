/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FileView from '@/components/documents/FileView.vue'

/**
 * A drawn document in a bordered frame; the caller sets the frame's height. The viewer fills that height
 * with the page and keeps its page and zoom controls inside it, where they stay in sight.
 */
defineProps<{
  pdf: Blob
  title: string
}>()
</script>

<template>
  <div class="flex flex-col rounded-lg border border-(--border) p-2">
    <FileView :source="pdf" :title="title" mime-type="application/pdf" fill class="min-h-0 flex-1"/>
  </div>
</template>
