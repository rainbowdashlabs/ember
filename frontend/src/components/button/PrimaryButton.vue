/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from './BaseButton.vue'

defineProps<{
  disabled?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <BaseButton
      :disabled="disabled"
      class="bg-primary text-white hover:bg-primary-accent"
      @click="$emit('click', $event)"
  >
    <slot/>
  </BaseButton>
</template>
