/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {useMovementRowView} from './movementRowView'
import type {Movement} from '@/api/movements'

/**
 * What kind of movement it is and whose gear it moves. The two together are what a row is: a swap
 * of the station's own gear and a swap of the association's walk different chains.
 */
const props = defineProps<{
  movement: Movement
}>()

const {t} = useI18n()
const {owner} = useMovementRowView(toRef(props, 'movement'))
</script>

<template>
  <span class="inline-flex flex-wrap items-center gap-1">
    <PrimaryBadge data-testid="movement-purpose">{{ t(`movements.purpose.${props.movement.purpose}`) }}</PrimaryBadge>
    <SecondaryBadge data-testid="movement-owner">{{ owner }}</SecondaryBadge>
  </span>
</template>
