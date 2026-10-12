/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import IconButton from './IconButton.vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

defineProps<{
  disabled?: boolean
  /** The editing page the button opens, which makes it a link that also opens in a new tab. */
  to?: RouteLocationRaw | null
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <IconButton
      :disabled="disabled"
      :to="to"
      :icon="['fas', 'pen']"
      class="text-info-accent hover:bg-info/15 dark:text-info"
      :label="t('common.edit')"
      @click="$emit('click', $event)"
  />
</template>
