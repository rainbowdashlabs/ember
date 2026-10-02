/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {stationManage} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import {StationModule} from '@/api/generated/schema'

const {t} = useI18n()
const router = useRouter()
const {load: reloadSession} = useSession()
const {reload} = useSetupStatus()

const disabled = ref<Set<StationModule>>(new Set([
  StationModule.INVENTORY, StationModule.ATTENDANCE, StationModule.FORMS, StationModule.LOST_AND_FOUND,
  StationModule.WAITING_LIST, StationModule.QUIZ, StationModule.TEST_PROTOCOL, StationModule.BOARDS,
  StationModule.PROCEDURES,
]))
const loading = ref(true)

onMounted(async () => {
  await stationManage.getDisabledModules().then(res => {
    disabled.value = new Set(res.disabledModules)
  }).catch(() => {})
  loading.value = false
})

function isEnabled(key: StationModule): boolean {
  return !disabled.value.has(key)
}

function toggle(key: StationModule) {
  const next = new Set(disabled.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  disabled.value = next
}

const {running: saving, failure, run: save} = useAsyncAction(async () => {
  await stationManage.setDisabledModules([...disabled.value])
  await reloadSession()
  await reload()
  goToNextStep(router, 'modules')
})
</script>

<template>
  <SetupLayout step-id="modules" :saving="saving" @save="save">
    <FailureAlert :failure="failure"/>
    <p v-if="loading" class="text-sm text-(--text-muted)">{{ t('common.loading') }}</p>
    <div v-else class="space-y-3">
      <StationModuleToggle
          v-for="mod in STATION_MODULE_OPTIONS"
          :key="mod.value"
          :module="mod"
          :model-value="isEnabled(mod.value)"
          @update:model-value="toggle(mod.value)"
      />
    </div>
  </SetupLayout>
</template>
