/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import Alert from '@/components/feedback/Alert.vue'
import {SwitchBlockerKinds, type SwitchBlocker} from '@/api/inventory'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'

/**
 * Why a change of what an inventory holds was refused, and what has to go first.
 *
 * A count would send the reader hunting, so each thing in the way is named and, where it lives on
 * another screen, linked. A size needs no link: the list it belongs to is on this page, below.
 */
defineProps<{
  message: string
  blockers: SwitchBlocker[]
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

function labelOf(blocker: SwitchBlocker): string {
  const named = blocker.label?.trim()
  return named
      ? t(`inventory.edit.blocker.${blocker.kind}`, {name: named})
      : t(`inventory.edit.blockerUnnamed.${blocker.kind}`)
}

/** Where to go to deal with one, or nothing when it is already on this screen. */
function targetOf(blocker: SwitchBlocker): RouteLocationRaw | null {
  switch (blocker.kind) {
    case SwitchBlockerKinds.REQUIREMENT:
      return {name: routes.requirements}
    case SwitchBlockerKinds.PROCUREMENT:
      return {name: routes.procurement}
    case SwitchBlockerKinds.EXCHANGE:
      return {name: routes.movement, params: {id: String(blocker.id)}}
    default:
      return null
  }
}
</script>

<template>
  <Alert v-if="message" variant="error" data-testid="inventory-kind-refused">
    {{ message }}
    <ul v-if="blockers.length > 0" class="mt-2 list-disc space-y-1 pl-5">
      <li v-for="blocker in blockers" :key="`${blocker.kind}-${blocker.id}`">
        <NuxtLink v-if="targetOf(blocker)" :to="targetOf(blocker)!" class="underline">
          {{ labelOf(blocker) }}
        </NuxtLink>
        <span v-else>{{ labelOf(blocker) }}</span>
      </li>
    </ul>
  </Alert>
</template>
