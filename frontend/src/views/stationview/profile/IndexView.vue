/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import type { ProfileField } from '@/api/types'
import { profileFields, auth, members } from '@/api'
import { useSession } from '@/composables/useSession'

function getUserScopes(roles: string[]): string[] {
  const scopes: string[] = []
  if (roles.includes('MEMBER')) {
    scopes.push('MEMBER')
  }
  if (roles.includes('TEAM') || roles.includes('MANAGER') || roles.includes('ADMIN')
      || roles.includes('ATTENDENCE_MANAGEMENT') || roles.includes('INVENTORY_MANAGEMENT')
      || roles.includes('EVENT_MANAGEMENT') || roles.includes('MEMBER_MANAGEMENT')) {
    scopes.push('TEAM')
  }
  if (roles.includes('MEMBER_MANAGER')) {
    scopes.push('MEMBER_MANAGER')
  }
  return scopes
}

const { t } = useI18n()
const { sessionInfo } = useSession()

const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

// Account details
const editFirstName = ref('')
const editLastName = ref('')
const editEmail = ref('')
const savingAccount = ref(false)

// Password change
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const savingPassword = ref(false)

const memberId = computed(() => sessionInfo.value?.member?.id ?? null)

const userScopes = computed(() => getUserScopes([...(sessionInfo.value?.roles ?? [])]))

const editableFields = computed(() => {
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return userScopes.value.includes(f.scope ?? 'MEMBER')
  })
})

function parseFieldConfig(configStr: string | undefined): { required?: boolean; readonly?: boolean; options?: string[]; defaultValue?: unknown } {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

function getValue(fieldId: number): string {
  return values.value.get(fieldId) ?? ''
}

function setValue(fieldId: number, val: string) {
  const newMap = new Map(values.value)
  newMap.set(fieldId, val)
  values.value = newMap
}


async function loadProfile() {
  if (!memberId.value) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [allFields, profileValues] = await Promise.all([
      profileFields.listFields(),
      profileFields.getValues(memberId.value),
    ])
    fields.value = allFields
    // Populate account fields
    const account = sessionInfo.value?.account
    if (account) {
      editFirstName.value = account.firstName ?? ''
      editLastName.value = account.lastName ?? ''
      editEmail.value = account.email ?? ''
    }
    const map = new Map<number, string>()
    for (const v of profileValues) {
      let val = v.value ?? ''
      try { val = JSON.parse(val) } catch { /* use as-is */ }
      map.set(v.fieldId, typeof val === 'string' ? val : String(val))
    }
    values.value = map
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!memberId.value) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const entries = editableFields.value
      .filter(f => !parseFieldConfig(f.config).readonly)
      .map(f => ({ fieldId: f.id, value: JSON.stringify(getValue(f.id)) }))
    await profileFields.setValues(memberId.value, { values: entries })
    success.value = t('profile.saved')
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

async function saveAccount() {
  savingAccount.value = true
  error.value = ''
  success.value = ''
  try {
    const accountId = sessionInfo.value?.account?.id
    if (!accountId) return
    await members.updateAccount(accountId, {
      email: editEmail.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
    success.value = t('profile.accountSaved')
  } catch {
    error.value = t('common.error')
  } finally {
    savingAccount.value = false
  }
}

async function changePassword() {
  if (newPassword.value !== confirmPassword.value) {
    error.value = t('profile.passwordMismatch')
    return
  }
  savingPassword.value = true
  error.value = ''
  success.value = ''
  try {
    await auth.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    success.value = t('profile.passwordChanged')
  } catch {
    error.value = t('profile.passwordError')
  } finally {
    savingPassword.value = false
  }
}

watch(memberId, (newId) => {
  if (newId) loadProfile()
})

onMounted(loadProfile)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && memberId">
        <!-- Account details -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profile.accountTitle') }}</SectionHeader>
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.firstName') }}</label>
              <TextInput v-model="editFirstName" />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.lastName') }}</label>
              <TextInput v-model="editLastName" />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.email') }}</label>
              <TextInput v-model="editEmail" />
            </div>
          </div>
          <PrimaryButton :disabled="savingAccount" @click="saveAccount">
            {{ savingAccount ? t('common.loading') : t('profile.saveAccount') }}
          </PrimaryButton>
        </NeutralContainer>

        <!-- Change password -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profile.passwordTitle') }}</SectionHeader>
          <div class="grid gap-4 sm:grid-cols-3">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.currentPassword') }}</label>
              <PasswordInput v-model="currentPassword" :placeholder="t('profile.currentPassword')" />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.newPassword') }}</label>
              <PasswordInput v-model="newPassword" :placeholder="t('profile.newPassword')" />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('profile.confirmPassword') }}</label>
              <PasswordInput v-model="confirmPassword" :placeholder="t('profile.confirmPassword')" />
            </div>
          </div>
          <PrimaryButton :disabled="savingPassword || !currentPassword || !newPassword || !confirmPassword" @click="changePassword">
            {{ savingPassword ? t('common.loading') : t('profile.changePassword') }}
          </PrimaryButton>
        </NeutralContainer>

        <!-- Profile fields -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profile.title') }}</SectionHeader>

          <div v-if="editableFields.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('profile.noFields') }}
          </div>

          <div v-for="field in editableFields" :key="field.id" class="space-y-1">
            <label class="block text-sm font-medium">
              {{ field.name }}
              <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
              <span v-if="parseFieldConfig(field.config).readonly" class="text-xs text-(--text-muted) ml-1">({{ t('profile.readonlyHint') }})</span>
            </label>

            <ProfileFieldInput
              :field-type="field.fieldType ?? 'text'"
              :model-value="getValue(field.id)"
              :options="(parseFieldConfig(field.config).options as string[]) ?? []"
              :disabled="!!parseFieldConfig(field.config).readonly"
              @update:model-value="setValue(field.id, $event)"
            />
          </div>

          <PrimaryButton :disabled="saving" @click="saveProfile">
            {{ saving ? t('common.loading') : t('profile.save') }}
          </PrimaryButton>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
