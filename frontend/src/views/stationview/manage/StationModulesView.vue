/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationManage} from '@/api'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {hasPermission, loaded, load: reloadSession} = useSession()
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_MODULES)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const {t} = useI18n()

const loading = ref(true)
const disabledModules = ref<Set<string>>(new Set())
const clusterDenied = ref<Set<string>>(new Set())
const clusterName = ref<string | null>(null)

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

function isModuleEnabled(key: string): boolean {
  return !disabledModules.value.has(key) && !clusterDenied.value.has(key)
}

/** A module the cluster switched off is shown as locked rather than simply off, and says who locked it. */
function isLockedByCluster(key: string): boolean {
  return clusterDenied.value.has(key)
}

const {running: modulesSaving, error, run: toggleModule} = useAsyncAction(async (key: string) => {
  const next = new Set(disabledModules.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  const res = await stationManage.setDisabledModules([...next])
  apply(res)
  reloadSession()
})

function apply(res: stationManage.ModulesResponse) {
  disabledModules.value = new Set(res.disabledModules)
  clusterDenied.value = new Set(res.clusterDeniedModules ?? [])
  clusterName.value = res.clusterName ?? null
}

onMounted(async () => {
  try {
    apply(await stationManage.getDisabledModules())
  } catch {
    loading.value = false
    return
  }
  loading.value = false
})
</script>

<template>
  <ViewContent
      :title="t('pages.station-modules.title')"
      :subtitle="t('pages.station-modules.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
        <div class="space-y-3">
          <div v-for="mod in allModules" :key="mod.key" data-testid="module-toggle" :data-module="mod.key"
               class="flex items-center gap-3">
            <ToggleInput :model-value="isModuleEnabled(mod.key)"
                         :disabled="modulesSaving || isLockedByCluster(mod.key)"
                         @update:model-value="toggleModule(mod.key)"/>
            <span class="text-sm font-medium">{{ t(`stationManage.${mod.label}`) }}</span>
            <span v-if="isLockedByCluster(mod.key)" class="text-xs text-(--text-muted)">
              <font-awesome-icon :icon="['fas', 'lock']" class="mr-1 h-3 w-3"/>
              {{ t('stationManage.moduleClusterLocked', {cluster: clusterName ?? ''}) }}
            </span>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
