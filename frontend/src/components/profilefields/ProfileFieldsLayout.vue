/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import type {FieldOrigin} from '@/util/profileFields'
import {isSection, spanClass} from './fieldLayout'

/**
 * The fields of a member, laid out the way the station arranged them: headings between them, and
 * the short ones beside each other rather than each on a row of its own.
 *
 * <p>The one place that decides this, so a field looks the same wherever it is filled in.
 */
/**
 * A field as this layout needs it: the station's own, or one its cluster added.
 *
 * The station is optional because a cluster's question belongs to no station: it is asked at every station
 * under the cluster, and the row it lives in names the cluster instead.
 */
export type LaidOutField = Omit<ProfileField, 'stationId'> & {
  stationId?: string
  origin?: FieldOrigin
  /** Set on a cluster field the cluster keeps to itself, which nobody at the station may write. */
  readonlyAtStation?: boolean
}

const props = defineProps<{
  fields: LaidOutField[]
  getValue: (field: LaidOutField) => string
  /** Whether the reader may write to fields the station marked read-only. */
  canEditReadonly?: boolean
}>()

const emit = defineEmits<{
  update: [field: LaidOutField, value: string]
}>()

const {t} = useI18n()

/**
 * Whether the input is shown but not offered.
 *
 * Two different reasons land here. The station can mark its own field read-only for everybody but its
 * managers, and a cluster can keep one of its questions to itself, which nobody at the station may
 * answer whatever they hold.
 */
function locked(field: LaidOutField): boolean {
  if (field.readonlyAtStation) return true
  return !props.canEditReadonly && !!parseFieldConfig(field.config).readonly
}
</script>

<template>
  <div class="grid grid-cols-6 gap-x-4 gap-y-3 items-start">
    <template v-for="field in props.fields" :key="`${field.origin ?? 'STATION'}-${field.id}`">
      <div v-if="isSection(field)" :class="spanClass(field)" class="pt-2 first:pt-0">
        <SubHeader class="text-sm">{{ field.name }}</SubHeader>
      </div>
      <div v-else :class="spanClass(field)" class="space-y-1">
        <FieldLabel>
          {{ field.name }}
          <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
          <SecondaryBadge v-if="field.origin === 'CLUSTER'" class="ml-1">
            {{ t('memberEdit.fieldFromCluster') }}
          </SecondaryBadge>
          <MutedText v-if="locked(field)" class="ml-1">({{ t('profile.readonlyHint') }})</MutedText>
        </FieldLabel>
        <ProfileFieldInput
            :field-type="field.fieldType ?? 'TEXT'"
            :model-value="props.getValue(field)"
            :options="(parseFieldConfig(field.config).options as string[]) ?? []"
            :disabled="locked(field)"
            @update:model-value="emit('update', field, $event)"
        />
      </div>
    </template>
  </div>
</template>
