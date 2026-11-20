/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'

/**
 * Says that an open movement carries a piece, and leads to that movement.
 *
 * <p>A promised piece sits on the shelf looking free, and one on its way looks like nothing at all.
 * The badge stands where a lent-out piece says to whom, so the reader sees why the piece is not
 * theirs to hand out and where to find out more.
 */
const props = defineProps<{
  /** The movement carrying the piece. Nothing is drawn where none does. */
  movementId?: number | null
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

const target = computed(() =>
    props.movementId != null ? {name: routes.movement, params: {id: String(props.movementId)}} : null)
</script>

<template>
  <router-link v-if="target" :to="target" class="inline-block hover:underline" data-testid="movement-piece-badge" @click.stop>
    <InfoBadge>
      <font-awesome-icon :icon="['fas', 'right-left']" class="mr-0.5 h-2.5 w-2.5"/>
      {{ t('inventory.detail.onMovement', {id: props.movementId}) }}
    </InfoBadge>
  </router-link>
</template>
