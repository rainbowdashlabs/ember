/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'

const props = defineProps<{
  icon: string | string[]
  label: string
  count: number
  warnAt?: number
  critAt?: number
  routeName?: string
}>()

const variant = computed<'success' | 'info' | 'error' | 'neutral'>(() => {
  if (props.count === 0) return 'success'
  if (props.critAt !== undefined && props.count >= props.critAt) return 'error'
  if (props.warnAt !== undefined && props.count >= props.warnAt) return 'info'
  return 'info'
})

const container = computed(() => {
  if (variant.value === 'success') return SuccessContainer
  if (variant.value === 'error') return ErrorContainer
  if (variant.value === 'info') return InfoContainer
  return NeutralContainer
})

/** The page the card stands for, or nothing for a card that only counts something. */
const page = computed(() => props.routeName ? {name: props.routeName} : null)
</script>

<template>
  <RowLink :to="page">
    <component
        :is="container"
        :class="['flex flex-col gap-2 h-full', routeName ? 'cursor-pointer hover:border-primary transition-colors' : '']"
    >
      <div class="flex items-start gap-2">
        <font-awesome-icon :icon="icon" class="text-xl mt-0.5"/>
        <div class="flex-1 min-w-0">
          <p class="text-sm text-(--text-muted)">{{ label }}</p>
          <StatValue>{{ count }}</StatValue>
        </div>
      </div>
    </component>
  </RowLink>
</template>
