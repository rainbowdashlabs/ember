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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationManage} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {load: reloadSession} = useSession()
const {reload} = useSetupStatus()

const allModules = [
  {key: 'INVENTORY', label: 'moduleInventory'},
  {key: 'NEWS', label: 'moduleNews'},
  {key: 'EVENTS', label: 'moduleEvents'},
  {key: 'ATTENDANCE', label: 'moduleAttendance'},
  {key: 'FORMS', label: 'moduleForms'},
  {key: 'LOST_AND_FOUND', label: 'moduleLostAndFound'},
  {key: 'WAITING_LIST', label: 'moduleWaitingList'},
  {key: 'QUIZ', label: 'moduleQuiz'},
  {key: 'TEST_PROTOCOL', label: 'moduleTestProtocol'},
  {key: 'KNOWLEDGE_BASE', label: 'moduleKnowledgeBase'},
  {key: 'BOARDS', label: 'moduleBoards'},
  {key: 'PROCEDURES', label: 'moduleProcedures'},
]
const disabled = ref<Set<string>>(new Set(['INVENTORY', 'ATTENDANCE', 'FORMS', 'LOST_AND_FOUND', 'WAITING_LIST', 'QUIZ', 'TEST_PROTOCOL', 'BOARDS', 'PROCEDURES']))
const loading = ref(true)
const saving = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    const res = await stationManage.getDisabledModules()
    disabled.value = new Set(res.disabledModules)
  } catch { /* ignore */ }
  loading.value = false
})

function isEnabled(key: string): boolean {
  return !disabled.value.has(key)
}

function toggle(key: string) {
  const next = new Set(disabled.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  disabled.value = next
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    await stationManage.setDisabledModules([...disabled.value])
    await reloadSession()
    await reload()
    const next = nextStep('modules')
    if (next) router.push({name: stepRouteName(next)})
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <SetupLayout step-id="modules" :saving="saving" @save="save">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <p v-if="loading" class="text-sm text-(--text-muted)">{{ t('common.loading') }}</p>
    <div v-else class="space-y-3">
      <div v-for="mod in allModules" :key="mod.key" class="flex items-center gap-3">
        <ToggleInput :model-value="isEnabled(mod.key)" @update:model-value="toggle(mod.key)"/>
        <span class="text-sm font-medium">{{ t(`stationManage.${mod.label}`) }}</span>
      </div>
    </div>
  </SetupLayout>
</template>
