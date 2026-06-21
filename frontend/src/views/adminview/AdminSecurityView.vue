/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TableHeaderCell from '@/components/typography/TableHeaderCell.vue'
import {twoFactorAdmin} from '@/api'
import type {AuditEntry, TwoFactorPolicy} from '@/api/twoFactorAdmin'

const {t} = useI18n()

// Same ordered list the backend service exposes; we hard-code the labels here.
const USER_TYPES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

const loading = ref(true)
const error = ref('')
const policies = ref<TwoFactorPolicy[]>([])
const saving = ref<string | null>(null)

const policyByUserType = computed(() => {
  const map = new Map<string, TwoFactorPolicy>()
  for (const p of policies.value) {
    if (p.userType) map.set(p.userType, p)
  }
  return map
})

// -- Audit log --
const auditLoading = ref(false)
const audit = ref<AuditEntry[]>([])
const auditOffset = ref(0)
const auditPageSize = 50
const auditHasMore = ref(false)
const auditAccountFilter = ref<number | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    policies.value = await twoFactorAdmin.listInstancePolicies()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  loading.value = false
  loadAudit(true)
}

async function loadAudit(reset = false) {
  if (reset) {
    audit.value = []
    auditOffset.value = 0
  }
  auditLoading.value = true
  try {
    const entries = await twoFactorAdmin.listAuditLog({
      accountId: auditAccountFilter.value ?? undefined,
      limit: auditPageSize,
      offset: auditOffset.value,
    })
    audit.value = audit.value.concat(entries)
    auditHasMore.value = entries.length === auditPageSize
    auditOffset.value += entries.length
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  auditLoading.value = false
}

async function togglePolicy(userType: string) {
  const existing = policyByUserType.value.get(userType)
  saving.value = userType
  try {
    if (existing && existing.required) {
      await twoFactorAdmin.deleteInstancePolicy(existing.id)
    } else {
      await twoFactorAdmin.upsertInstancePolicy(userType, true)
    }
    policies.value = await twoFactorAdmin.listInstancePolicies()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  saving.value = null
}

function userTypeLabel(name: string): string {
  const key = `twoFactor.admin.userTypes.${name}`
  const translated = t(key)
  return translated === key ? name : translated
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('de-DE')
}

// -- Reset --
const resetAccountId = ref<number | null>(null)
const resetLoading = ref(false)
const resetConfirmOpen = ref(false)
const resetSuccess = ref('')

function openResetModal() {
  if (!resetAccountId.value) return
  resetSuccess.value = ''
  resetConfirmOpen.value = true
}

async function confirmReset() {
  if (!resetAccountId.value) return
  resetLoading.value = true
  try {
    await twoFactorAdmin.resetAccount2FAByInstanceAdmin(resetAccountId.value)
    resetSuccess.value = t('twoFactor.admin.resetSuccess', {id: resetAccountId.value})
    resetConfirmOpen.value = false
    resetAccountId.value = null
    loadAudit(true)
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  resetLoading.value = false
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('twoFactor.admin.instanceTitle') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('twoFactor.admin.instanceHint') }}</MutedText>
      </div>

      <Spinner v-if="loading" size="md"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('twoFactor.admin.policiesTitle') }}</SubHeader>
          <ul class="space-y-2">
            <li v-for="ut in USER_TYPES" :key="ut"
                class="flex items-center justify-between rounded border border-(--border) px-3 py-2">
              <div>
                <div class="text-sm font-medium">{{ userTypeLabel(ut) }}</div>
                <MutedText tag="div" size="sm">
                  {{ policyByUserType.get(ut)?.required ? t('twoFactor.admin.required') : t('twoFactor.admin.optional') }}
                </MutedText>
              </div>
              <ToggleInput
                  :model-value="!!policyByUserType.get(ut)?.required"
                  :disabled="saving === ut"
                  @update:model-value="togglePolicy(ut)"
              />
            </li>
          </ul>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('twoFactor.admin.resetTitle') }}</SubHeader>
          <MutedText tag="p" size="sm">{{ t('twoFactor.admin.resetHint') }}</MutedText>
          <Alert v-if="resetSuccess" variant="success">{{ resetSuccess }}</Alert>
          <div class="flex items-end gap-2">
            <div class="flex-1">
              <MutedText tag="label" size="sm">{{ t('twoFactor.admin.resetAccountIdLabel') }}</MutedText>
              <NumberInput v-model="resetAccountId" :placeholder="t('twoFactor.admin.resetAccountIdLabel')"/>
            </div>
            <ErrorButton :disabled="!resetAccountId" @click="openResetModal">
              {{ t('twoFactor.admin.reset') }}
            </ErrorButton>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('twoFactor.admin.audit.title') }}</SubHeader>
          <div class="flex items-end gap-2">
            <div class="flex-1">
              <MutedText tag="label" size="sm">{{ t('twoFactor.admin.audit.filterAccount') }}</MutedText>
              <NumberInput v-model="auditAccountFilter" :placeholder="t('twoFactor.admin.audit.filterAccount')"/>
            </div>
            <SecondaryButton :disabled="auditLoading" @click="loadAudit(true)">
              {{ t('common.refresh') }}
            </SecondaryButton>
          </div>

          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-(--text-muted)">
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.when') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.account') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.actor') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.event') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.factor') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.audit.col.country') }}</TableHeaderCell>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in audit" :key="e.id" class="border-t border-(--border)">
                  <td class="py-2 pr-3 text-(--text-muted) whitespace-nowrap">{{ formatDate(e.createdAt) }}</td>
                  <td class="py-2 pr-3 font-mono">{{ e.accountId }}</td>
                  <td class="py-2 pr-3 font-mono">{{ e.actorId ?? '—' }}</td>
                  <td class="py-2 pr-3">{{ e.event }}</td>
                  <td class="py-2 pr-3 text-(--text-muted)">{{ e.factorKind ?? '—' }}</td>
                  <td class="py-2 pr-3 text-(--text-muted)">{{ e.country ?? '—' }}</td>
                </tr>
                <tr v-if="!auditLoading && audit.length === 0">
                  <td colspan="6" class="py-4 text-(--text-muted) text-center">{{ t('twoFactor.admin.audit.empty') }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="auditHasMore" class="text-center">
            <SecondaryButton :disabled="auditLoading" @click="loadAudit(false)">
              {{ t('twoFactor.admin.audit.loadMore') }}
            </SecondaryButton>
          </div>
        </NeutralContainer>
      </template>

      <Modal v-model="resetConfirmOpen" size="sm">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('twoFactor.admin.resetConfirmTitle') }}</SubHeader>
          <p class="text-sm">
            {{ t('twoFactor.admin.resetConfirmTextById', {id: resetAccountId}) }}
          </p>
          <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
          <div class="flex justify-end gap-2">
            <SecondaryButton :disabled="resetLoading" @click="resetConfirmOpen = false">
              {{ t('common.cancel') }}
            </SecondaryButton>
            <ErrorButton :disabled="resetLoading" @click="confirmReset">
              {{ resetLoading ? t('common.loading') : t('twoFactor.admin.reset') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
