/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()

const {t} = useI18n()
</script>

<template>
  <IconButton
      :icon="['fas', 'chevron-left']"
      :label="collapsed ? t('sidebar.expand') : t('sidebar.collapse')"
      class="text-(--text-muted) hover:text-(--text) hover:bg-(--bg-accent) shrink-0"
      @click="$emit('click', $event)"
  >
    <font-awesome-icon
        :icon="['fas', 'chevron-left']"
        :class="collapsed ? 'rotate-180' : ''"
        class="h-3.5 w-3.5 transition-transform duration-300"
    />
  </IconButton>
</template>
