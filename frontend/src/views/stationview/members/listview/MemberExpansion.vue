/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type {ProfileField} from '@/api/profileFields'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

defineProps<{
  member: StationMember
  colSpan: number
  overviewFields: ProfileField[]
  managers: StationMember[]
  getFieldValueFor: (memberId: number, fieldId: number) => unknown
  getOverviewFieldsFor: (memberId: number) => ProfileField[]
  managerName: (mgr: StationMember) => string
}>()
</script>

<template>
  <tr>
    <td :colspan="colSpan" class="px-3 py-4 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
      <div class="space-y-3">
        <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="field in overviewFields" :key="field.id" class="text-sm">
            <span class="text-(--text-muted)">{{ field.name }}:</span>
            <span class="ml-1 font-medium"><FieldValueDisplay :value="getFieldValueFor(member.id, field.id)" :field-type="field.fieldType"/></span>
          </div>
        </div>
        <div v-if="managers.length > 0">
          <h4 class="text-xs font-semibold uppercase text-(--text-muted) mb-2">{{ t('membersList.managers') }}</h4>
          <div class="space-y-2">
            <div v-for="mgr in managers" :key="mgr.id"
                 class="rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 space-y-1">
              <div class="flex items-center gap-2">
                <MutedIcon :icon="['fas', 'user']"/>
                <span class="text-sm font-medium">{{ managerName(mgr) }}</span>
                <span v-if="mgr.email" class="text-xs text-(--text-muted)">{{ mgr.email }}</span>
              </div>
              <div class="grid gap-x-4 gap-y-1 sm:grid-cols-2 lg:grid-cols-3 pl-5">
                <div v-for="field in getOverviewFieldsFor(mgr.id)" :key="field.id" class="text-xs">
                  <span class="text-(--text-muted)">{{ field.name }}:</span>
                  <span class="ml-1"><FieldValueDisplay :value="getFieldValueFor(mgr.id, field.id)" :field-type="field.fieldType"/></span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </td>
  </tr>
</template>
