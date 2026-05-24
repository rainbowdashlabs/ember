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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {session as sessionApi, userSettings, managedMembers as managedMembersApi} from '@/api'
import type {ActiveSession, UserSettings} from '@/api/types'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import {useSession} from '@/composables/useSession'
import ThemeSection from './settingsview/ThemeSection.vue'
import SessionsSection from './settingsview/SessionsSection.vue'
import NotificationsSection from './settingsview/NotificationsSection.vue'
import GdprSection from './settingsview/GdprSection.vue'

const {t} = useI18n()
const router = useRouter()
const {startTour} = useOnboardingTour()
const {isGuardian} = useSession()

interface ManagedMemberInfo {
  id: number
  name: string
}

const settings = ref<UserSettings | null>(null)
const sessions = ref<ActiveSession[]>([])
const managedMembers = ref<ManagedMemberInfo[]>([])
const exportingGdpr = ref(false)
const exportingMemberId = ref<number | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)
const showInvalidateAllModal = ref(false)
const showDeleteAccountModal = ref(false)
const deletingAccount = ref(false)

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function exportOwnData() {
  exportingGdpr.value = true
  error.value = ''
  try {
    const blob = await sessionApi.gdprExport()
    downloadBlob(blob, 'gdpr-export.json')
  } catch {
    error.value = t('common.error')
  } finally {
    exportingGdpr.value = false
  }
}

async function exportManagedMemberData(memberId: number) {
  exportingMemberId.value = memberId
  error.value = ''
  try {
    const blob = await sessionApi.gdprExportManagedMember(memberId)
    downloadBlob(blob, `gdpr-export-member-${memberId}.json`)
  } catch {
    error.value = t('common.error')
  } finally {
    exportingMemberId.value = null
  }
}

async function confirmDeleteAccount() {
  deletingAccount.value = true
  try {
    await sessionApi.deleteAccount()
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch {
    error.value = t('common.error')
    deletingAccount.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const [s, sess] = await Promise.all([
      userSettings.getSettings(),
      sessionApi.getActiveSessions(),
    ])
    settings.value = s
    sessions.value = sess
    if (isGuardian()) {
      try {
        const managed = await managedMembersApi.listManaged()
        managedMembers.value = managed.map(m => ({id: m.id, name: m.name}))
      } catch { /* ignore */ }
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!settings.value) return
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    settings.value = await userSettings.updateSettings({
      emailEnabled: settings.value.emailEnabled,
      notifications: settings.value.notifications,
    })
    saved.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function toggleEmailEnabled() {
  if (!settings.value) return
  settings.value.emailEnabled = !settings.value.emailEnabled
  save()
}

function toggleApp(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false}
  settings.value.notifications[type] = {app: !current.app, email: current.email}
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false}
  settings.value.notifications[type] = {app: current.app, email: !current.email}
  save()
}

async function invalidateSession(id: number) {
  error.value = ''
  try {
    await sessionApi.invalidateSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
  } catch {
    error.value = t('common.error')
  }
}

async function invalidateAll() {
  error.value = ''
  try {
    await sessionApi.invalidateAllSessions()
    showInvalidateAllModal.value = false
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('userSettings.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading && settings">
        <ThemeSection/>

        <SessionsSection
            :sessions="sessions"
            @invalidate="invalidateSession"
            @invalidate-all="showInvalidateAllModal = true"
        />

        <NotificationsSection
            :settings="settings"
            @toggle-email-enabled="toggleEmailEnabled"
            @toggle-app="toggleApp"
            @toggle-email="toggleEmail"
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

      <!-- Invalidate All Modal -->
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
      <!-- Delete Account Modal -->
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
