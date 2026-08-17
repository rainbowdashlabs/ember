/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {session as sessionApi, managedMembers as managedMembersApi} from '@/api'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {saveBlob} from '@/util/downloadAuthed'
import GdprSection from '@/views/stationview/profile/settingsview/GdprSection.vue'
import StorageConsentSection from '@/views/stationview/profile/settingsview/StorageConsentSection.vue'

const {t} = useI18n()
const router = useRouter()
const {startTour} = useOnboardingTour()
const {isGuardian} = useSession()

interface ManagedMemberInfo { id: number; name: string }

const managedMembers = ref<ManagedMemberInfo[]>([])
const exportingMemberId = ref<number | null>(null)
const memberError = ref('')
const showDeleteAccountModal = ref(false)

const {running: exportingGdpr, error: exportError, run: exportOwnData} = useAsyncAction(async () => {
  saveBlob(await sessionApi.gdprExport(), 'gdpr-export.zip')
})

async function exportManagedMemberData(memberId: number) {
  exportingMemberId.value = memberId; memberError.value = ''
  try { saveBlob(await sessionApi.gdprExportManagedMember(memberId), `gdpr-export-member-${memberId}.zip`) }
  catch { memberError.value = t('common.error') }
  finally { exportingMemberId.value = null }
}

const {running: deletingAccount, error: deleteError, run: confirmDeleteAccount} = useAsyncAction(async () => {
  await sessionApi.deleteAccount()
  localStorage.removeItem('session_token')
  localStorage.removeItem('session_expires_at')
  router.push({name: 'login'})
})

const error = computed(() => exportError.value || memberError.value || deleteError.value)

onMounted(async () => {
  if (isGuardian()) {
    try {
      const managed = await managedMembersApi.listManaged()
      managedMembers.value = managed.map(m => ({id: m.id, name: m.name}))
    } catch { /* ignore */ }
  }
})
</script>

<template>
  <ViewContent :title="t('pages.account-gdpr.title')" :subtitle="t('pages.account-gdpr.subtitle')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <StorageConsentSection/>

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
