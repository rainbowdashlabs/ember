/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, watch} from 'vue'
import {usePageHeader} from '@/composables/usePageHeader'

const props = defineProps<{
  title: string
  subtitle?: string
}>()

const {title: headerTitle, subtitle: headerSubtitle} = usePageHeader()

watch(
    () => props.title,
    v => {
      headerTitle.value = v
    },
    {immediate: true},
)
watch(
    () => props.subtitle ?? '',
    v => {
      headerSubtitle.value = v
    },
    {immediate: true},
)

onBeforeUnmount(() => {
  headerTitle.value = ''
  headerSubtitle.value = ''
})
</script>

<template>
  <div class="p-4 sm:p-6">
    <slot/>
  </div>
</template>
