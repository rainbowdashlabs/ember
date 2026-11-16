/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import IconButton from '@/components/button/IconButton.vue'

/**
 * A setting of a question shown as an icon on its line, lit while it is on and switched with a click.
 * The label names the setting, read out together with whether it is pressed.
 */
const active = defineModel<boolean>({required: true})

defineProps<{
  icon: string[]
  label: string
  disabled?: boolean
}>()
</script>

<template>
  <IconButton
      :icon="icon"
      :label="label"
      :aria-pressed="active"
      :disabled="disabled"
      :class="active ? 'text-primary' : 'text-(--text-muted) opacity-50'"
      @click="active = !active"
  />
</template>
