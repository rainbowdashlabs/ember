/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ContainerTree from '@/views/stationview/inventory/storageview/ContainerTree.vue'
import type {PitchStorage} from './pitchTypes'

/** The storage, drawn by the application's own container tree. */
defineProps<{
  storage: PitchStorage
}>()
</script>

<template>
  <ContainerTree
      :roots="storage.roots" :children-by-parent="storage.childrenByParent"
      :kind-by-id="storage.kindById"/>
</template>
