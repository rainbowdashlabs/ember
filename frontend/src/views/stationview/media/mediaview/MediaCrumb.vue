/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from '@/components/button/BaseButton.vue'
import {useFolderDrop} from '@/composables/useMediaDrop'

/**
 * One step of the path above the files: opened by a press, and a place to drop files, which is how a
 * file moves up into a folder above or out to the top level.
 */
const props = defineProps<{
  /** The folder of the step, null for the top level. */
  folderId: number | null
  /** Whether the step is the folder shown. */
  current: boolean
}>()

const emit = defineEmits<{
  navigate: [folderId: number | null]
}>()

const drop = useFolderDrop(() => props.folderId)
</script>

<template>
  <BaseButton compact class="!font-normal hover:bg-(--bg-accent)"
              :class="[props.current ? '!text-primary !font-medium' : '!text-(--text-muted)', drop.over.value ? '!ring-2 !ring-primary' : '']"
              data-testid="media-crumb"
              v-on="drop.handlers"
              @click="emit('navigate', props.folderId)">
    <slot/>
  </BaseButton>
</template>
