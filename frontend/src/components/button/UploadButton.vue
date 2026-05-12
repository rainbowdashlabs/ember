/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import IconButton from './IconButton.vue'

defineProps<{
  disabled?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <IconButton
      :disabled="disabled"
      :icon="['fas', 'upload']"
      class="text-primary hover:bg-primary/15"
      label="Upload"
      @click="$emit('click', $event)"
  />
</template>
