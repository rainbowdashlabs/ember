/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FileView from '@/components/documents/FileView.vue'

/** A drawn document in a bordered frame; the caller sets the frame's height. */
defineProps<{
  pdf: Blob
  title: string
}>()
</script>

<template>
  <div class="rounded-lg border border-(--border) overflow-hidden">
    <FileView :source="pdf" :title="title" mime-type="application/pdf"/>
  </div>
</template>
