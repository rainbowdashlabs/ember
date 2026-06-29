/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import MemberTypeBadge from './MemberTypeBadge.vue'
import type {ProfileField, StationMember} from '@/api/types'

const {t} = useI18n()

defineProps<{
  member: StationMember
  visibleColumns: ProfileField[]
  memberGroups: string[]
  memberTags: string[]
  isFieldApplicable: (field: ProfileField) => boolean
  getFieldValue: (fieldId: number) => unknown
  expanded?: boolean
  exportMode?: boolean
  selected?: boolean
  canEdit?: boolean
}>()

const emit = defineEmits<{
  click: []
  toggleSelect: []
  navigateDetail: [event: Event]
  navigateEdit: [event: Event]
  resendSetup: [event: Event]
}>()
</script>

<template>
  <TRow
      :class="{
        'bg-bg-light-accent/30 dark:bg-bg-dark-accent/30': !exportMode && expanded,
        'bg-primary/5': exportMode && selected,
        'cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 transition-colors': true,
      }"
      @click="emit('click')"
  >
    <td v-if="exportMode" class="px-2 py-2.5" @click.stop>
      <CheckboxInput :model-value="selected ?? false" @update:model-value="emit('toggleSelect')"/>
    </td>
    <Td v-if="!exportMode" @click.stop>
      <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')"
                  class="text-primary hover:bg-primary/15"
                  @click="emit('navigateDetail', $event)"/>
      <EditButton v-if="canEdit" @click="emit('navigateEdit', $event)"/>
    </Td>
    <Td>
      <div class="flex items-center gap-2">
        <MemberName :identity="member.identity" size="sm" class="font-medium"/>
        <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
        <IconButton
            v-if="member.accountSetupPending && canEdit"
            :icon="['fas', 'paper-plane']"
            :title="t('membersList.accountPendingResend')"
            :label="t('membersList.accountPendingResend')"
            class="text-warning hover:bg-warning/15"
            @click.stop="emit('resendSetup', $event)"
        />
        <font-awesome-icon
            v-else-if="member.accountSetupPending"
            :icon="['fas', 'hourglass-half']"
            :title="t('membersList.accountPending')"
            class="text-warning w-3.5 h-3.5"
        />
      </div>
    </Td>
    <Td>
      <MemberTypeBadge :user-type="member.userType"/>
    </Td>
    <Td class="text-(--text-muted) text-xs">
      {{ member.email || '–' }}
    </Td>
    <Td class="text-(--text-muted) text-xs">
      {{ memberGroups.join(', ') || '–' }}
    </Td>
    <Td class="text-(--text-muted) text-xs">
      {{ memberTags.join(', ') || '–' }}
    </Td>
    <Td
        v-for="field in visibleColumns"
        :key="field.id"
        :class="isFieldApplicable(field) ? 'text-(--text-muted)' : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40'"
    >
      <template v-if="isFieldApplicable(field)">
        <FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/>
      </template>
    </Td>
  </TRow>
</template>
