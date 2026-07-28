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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import MemberTypeBadge from './MemberTypeBadge.vue'
import type {ProfileField} from '@/api/profileFields'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  visibleColumns: ProfileField[]
  memberGroups: string[]
  memberTags: string[]
  isFieldApplicable: (field: ProfileField) => boolean
  getFieldValue: (fieldId: number) => unknown
  exportMode?: boolean
  selected?: boolean
  canEdit?: boolean
}>()

const emit = defineEmits<{
  click: []
  toggleSelect: []
  navigateDetail: [event: Event]
  navigateEdit: [event: Event]
}>()
</script>

<template>
  <NeutralContainer class="space-y-2" @click="emit('click')">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <div>
          <MemberName :identity="member.identity" size="sm" class="font-medium"/>
          <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
          <div v-if="member.userType" class="mt-0.5">
            <MemberTypeBadge :user-type="member.userType"/>
          </div>
        </div>
      </div>
      <div v-if="!exportMode" class="flex gap-1" @click.stop>
        <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')" class="text-primary hover:bg-primary/15" @click="emit('navigateDetail', $event)"/>
        <EditButton v-if="canEdit" @click="emit('navigateEdit', $event)"/>
      </div>
      <div v-else @click.stop>
        <CheckboxInput :model-value="selected ?? false" @update:model-value="emit('toggleSelect')"/>
      </div>
    </div>
    <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm mt-1">
      <div v-if="member.email" class="col-span-2 text-(--text-muted) text-xs">{{ member.email }}</div>
      <div v-if="memberGroups.length > 0" class="text-xs">
        <div class="text-(--text-muted)">{{ t('membersList.colGroups') }}</div>
        <div>{{ memberGroups.join(', ') }}</div>
      </div>
      <div v-if="memberTags.length > 0" class="text-xs">
        <div class="text-(--text-muted)">{{ t('membersList.colTags') }}</div>
        <div>{{ memberTags.join(', ') }}</div>
      </div>
      <template v-for="field in visibleColumns" :key="field.id">
        <div v-if="isFieldApplicable(field) && getFieldValue(field.id)" class="text-xs">
          <div class="text-(--text-muted)">{{ field.name }}</div>
          <div><FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/></div>
        </div>
      </template>
    </div>
  </NeutralContainer>
</template>
