/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {InventoryItem, MemberRequirements} from '@/api/inventory'
import MissingRequirementCard from './MissingRequirementCard.vue'

/**
 * What the member is required to hold and has not been given.
 *
 * Shown only while something is missing: a member who has everything needs no list of what they
 * already carry, that is the tab below.
 */
const props = defineProps<{
  requirements: MemberRequirements
  canHandOut: boolean
}>()

const emit = defineEmits<{
  handOut: [itemId: number]
  handOutNew: [inventoryId: number, sizeId: number | null]
}>()

const {t} = useI18n()

const missing = computed(() =>
  props.requirements.required.filter(req => req.assignedQuantity < req.requiredQuantity))

function available(inventoryId: number): InventoryItem[] {
  return props.requirements.unassigned[inventoryId] ?? []
}
</script>

<template>
  <div v-if="missing.length > 0" class="space-y-3" data-testid="missing-requirements">
    <SubHeader>{{ t('memberDetail.missingRequirements') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('memberDetail.missingRequirementsHint') }}</MutedText>

    <template v-if="canHandOut">
      <MissingRequirementCard
          v-for="req in missing"
          :key="req.inventoryId"
          :requirement="req"
          :available="available(req.inventoryId)"
          @hand-out="emit('handOut', $event)"
          @hand-out-new="emit('handOutNew', req.inventoryId, $event)"
      />
    </template>
    <ul v-else class="list-disc space-y-1 pl-5 text-sm">
      <li v-for="req in missing" :key="req.inventoryId">
        {{ req.inventoryName }} ({{ req.assignedQuantity }} / {{ req.requiredQuantity }})
      </li>
    </ul>
  </div>
</template>
