/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ManagerDetail} from '@/api/types'

const props = defineProps<{
  manager: ManagerDetail | null
  editingManager: boolean
  isEdit: boolean
}>()

const managerEmail = defineModel<string>('managerEmail', {required: true})

const emit = defineEmits<{
  'start-transfer': []
  'cancel-transfer': []
}>()

const {t} = useI18n()

const hasManager = computed(() => props.manager !== null)
const showCurrent = computed(() => props.isEdit && hasManager.value && !props.editingManager)
const showInput = computed(() => !props.isEdit || !hasManager.value || props.editingManager)

const displayName = computed(() => {
  const m = props.manager!
  const composed = `${m.firstName ?? ''} ${m.lastName ?? ''}`.trim()
  return composed || m.email
})
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('adminStations.managerEmail') }}</FieldLabel>

    <component v-if="showCurrent" :is="manager!.accountReady ? SuccessContainer : InfoContainer"
               class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ displayName }}</div>
        <div class="text-sm text-(--text-muted)">{{ manager!.email }}</div>
        <div class="text-xs mt-1">
          <span v-if="manager!.accountReady" class="text-success">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('adminStations.accountReady') }}
          </span>
          <span v-else class="text-info">
            <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1"/>{{ t('adminStations.accountPending') }}
          </span>
        </div>
      </div>
      <EditButton @click="emit('start-transfer')"/>
    </component>

    <template v-if="showInput">
      <TextInput v-model="managerEmail" :placeholder="t('adminStations.managerEmailPlaceholder')"/>
      <p class="text-xs text-(--text-muted)">{{ t('adminStations.managerEmailHint') }}</p>
      <SecondaryButton v-if="editingManager" @click="emit('cancel-transfer')">
        {{ t('adminStations.cancelTransfer') }}
      </SecondaryButton>
    </template>
  </div>
</template>
