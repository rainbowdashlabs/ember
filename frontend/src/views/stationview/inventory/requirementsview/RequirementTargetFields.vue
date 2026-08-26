/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { StationUserType, type MemberGroup } from '@/api/types'
import type { StationGroup } from '@/api/clusterStationGroups'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'
import { userTypeFriendlyNames } from './types'

/**
 * Who a requirement is about, which is up to three questions and no more than two at once.
 *
 * <p>A station asks about a member type or one of its groups. An association asks about a member type
 * and, on top of it, which of its stations the requirement counts at: one requirement written once,
 * pointed at the stations it makes sense for.
 */
const targetType = defineModel<'userType' | 'group'>('targetType', { default: 'userType' })
const userType = defineModel<string>('userType', { default: '' })
const groupId = defineModel<string>('groupId', { default: '' })
const stationGroupId = defineModel<string>('stationGroupId', { default: '' })

defineProps<{
  allGroups: MemberGroup[]
  /** The association's ways of filing its stations. Empty at a station, which has none to point at. */
  stationGroups?: StationGroup[]
}>()

const { t } = useI18n()

/**
 * Whether a requirement can be keyed to a group of members at all.
 *
 * <p>Those stay with the station, so an association has none to key one to and is asked for a member
 * type instead of being offered a choice with one empty half.
 */
const routes = useInventoryRoutes()
const byGroup = computed(() => !!routes.memberGroups)
</script>

<template>
  <div class="space-y-4">
    <div v-if="byGroup" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.targetType') }}</FieldLabel>
      <SelectInput v-model="targetType">
        <option value="userType">{{ t('inventory.requirements.byUserType') }}</option>
        <option value="group">{{ t('inventory.requirements.byGroup') }}</option>
      </SelectInput>
    </div>

    <div v-if="targetType === 'userType'" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.userType') }}</FieldLabel>
      <SelectInput v-model="userType">
        <option value="" disabled>{{ t('inventory.requirements.selectUserType') }}</option>
        <option v-for="(value, key) in StationUserType" :key="key" :value="value">
          {{ userTypeFriendlyNames[value] ?? value }}
        </option>
      </SelectInput>
    </div>

    <div v-if="byGroup && targetType === 'group'" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.group') }}</FieldLabel>
      <SelectInput v-model="groupId">
        <option value="" disabled>{{ t('inventory.requirements.selectGroup') }}</option>
        <option v-for="group in allGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
      </SelectInput>
    </div>

    <div v-if="stationGroups && stationGroups.length > 0" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.stationGroup') }}</FieldLabel>
      <SelectInput v-model="stationGroupId">
        <option value="">{{ t('inventory.requirements.everyStation') }}</option>
        <option v-for="group in stationGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
      </SelectInput>
    </div>
  </div>
</template>
