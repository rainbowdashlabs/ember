/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {session as sessionApi, managedMembers as managedMembersApi} from '@/api'
import type {ActiveSession} from '@/api/types'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import {useSession} from '@/composables/useSession'
import SessionsSection from './settingsview/SessionsSection.vue'
import GdprSection from './settingsview/GdprSection.vue'

const {t} = useI18n()
const router = useRouter()
const {startTour} = useOnboardingTour()
const {isGuardian} = useSession()

interface ManagedMemberInfo { id: number; name: string }

const sessions = ref<ActiveSession[]>([])
const managedMembers = ref<ManagedMemberInfo[]>([])
const exportingGdpr = ref(false)
const exportingMemberId = ref<number | null>(null)
const loading = ref(true)
const error = ref('')
const showInvalidateAllModal = ref(false)
const showDeleteAccountModal = ref(false)
const deletingAccount = ref(false)

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

async function exportOwnData() {
  exportingGdpr.value = true; error.value = ''
  try { downloadBlob(await sessionApi.gdprExport(), 'gdpr-export.zip') }
  catch { error.value = t('common.error') }
  finally { exportingGdpr.value = false }
}

async function exportManagedMemberData(memberId: number) {
  exportingMemberId.value = memberId; error.value = ''
  try { downloadBlob(await sessionApi.gdprExportManagedMember(memberId), `gdpr-export-member-${memberId}.zip`) }
  catch { error.value = t('common.error') }
  finally { exportingMemberId.value = null }
}

async function confirmDeleteAccount() {
  deletingAccount.value = true
  try {
    await sessionApi.deleteAccount()
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch { error.value = t('common.error'); deletingAccount.value = false }
}

async function invalidateSession(id: number) {
  error.value = ''
  try { await sessionApi.invalidateSession(id); sessions.value = sessions.value.filter(s => s.id !== id) }
  catch { error.value = t('common.error') }
}

async function invalidateAll() {
  error.value = ''
  try {
    await sessionApi.invalidateAllSessions()
    showInvalidateAllModal.value = false
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch { error.value = t('common.error') }
}

onMounted(async () => {
  loading.value = true
  try {
    sessions.value = await sessionApi.getActiveSessions()
    if (isGuardian()) {
      try {
        const managed = await managedMembersApi.listManaged()
        managedMembers.value = managed.map(m => ({id: m.id, name: m.name}))
      } catch { /* ignore */ }
    }
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <SessionsSection
          :sessions="sessions"
          @invalidate="invalidateSession"
          @invalidate-all="showInvalidateAllModal = true"
        />

        <GdprSection
          :exporting-gdpr="exportingGdpr"
          :exporting-member-id="exportingMemberId"
          :managed-members="managedMembers"
          :deleting-account="deletingAccount"
          @export-own="exportOwnData"
          @export-member="exportManagedMemberData"
          @show-delete-modal="showDeleteAccountModal = true"
          @restart-tour="startTour()"
        />
      </template>

      <Modal v-model="showInvalidateAllModal">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('userSettings.invalidateAllTitle') }}</SubHeader>
          <ErrorContainer>
            <p class="text-sm">{{ t('userSettings.invalidateAllWarning') }}</p>
          </ErrorContainer>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showInvalidateAllModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="invalidateAll">{{ t('userSettings.invalidateAll') }}</ErrorButton>
          </div>
        </div>
      </Modal>

      <Modal v-model="showDeleteAccountModal">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('userSettings.deleteTitle') }}</SubHeader>
          <ErrorContainer>
            <p class="text-sm">{{ t('userSettings.deleteConfirmWarning') }}</p>
          </ErrorContainer>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteAccountModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="deletingAccount" @click="confirmDeleteAccount">
              {{ deletingAccount ? t('common.loading') : t('userSettings.deleteAccount') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
