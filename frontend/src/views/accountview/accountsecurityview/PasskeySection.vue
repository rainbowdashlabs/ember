/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import PasskeyList from '@/views/accountview/accountsecurityview/PasskeyList.vue'
import PasskeyCreateModal from '@/views/accountview/accountsecurityview/PasskeyCreateModal.vue'
import PasskeyRenameModal from '@/views/accountview/accountsecurityview/PasskeyRenameModal.vue'
import PasskeyRemoveModal from '@/views/accountview/accountsecurityview/PasskeyRemoveModal.vue'
import PasskeySwitches from '@/views/accountview/accountsecurityview/PasskeySwitches.vue'
import {getPasskeysStatus, removePasskey, renamePasskey, setAskWithPassword, setPasswordLogin} from '@/api/passkeys'
import type {PasskeyEntry, PasskeysStatus} from '@/api/passkeys'
import {isWebAuthnSupported, signalAcceptedCredentials} from '@/util/webauthn'
import {apiErrorStatus} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'
import {useConfirmDelete} from '@/composables/useConfirmDelete'

/**
 * The "Anmeldung" section: the passkey list, creation with its trial, and the two switches D3
 * names. The existing two-factor section keeps its name and its contents; a passkey is a way
 * in, not a second factor, and the screen says so by giving each its own place.
 */
const {t} = useI18n()

const status = ref<PasskeysStatus | null>(null)
const failure = ref<Failure | null>(null)
const notice = ref('')
const showCreate = ref(false)
const renameTarget = ref<PasskeyEntry | null>(null)

const supported = isWebAuthnSupported()

async function reload() {
  try {
    status.value = await getPasskeysStatus()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

onMounted(reload)

const removal = useConfirmDelete<PasskeyEntry>({
  onDelete: async (entry) => {
    const before = status.value
    const outcome = await removePasskey(entry.id)
    notice.value = outcome.passwordLoginReenabled ? t('passkeys.section.passwordReenabled') : ''
    // Tell the device's own store which credentials still exist, so the removed passkey
    // disappears from its picker instead of haunting it.
    if (before?.rpId && before.userHandle) {
      const remaining = before.passkeys
          .filter(p => p.id !== entry.id && p.credentialId)
          .map(p => p.credentialId as string)
      await signalAcceptedCredentials(before.rpId, before.userHandle, remaining)
    }
    await reload()
  },
  failure,
})

/**
 * Removes the passkey, and says which refusal it was.
 *
 * <p>The one worth naming is the last passkey on an account with no password: that leaves nobody a
 * way back in, so the server turns it down and the reader has to set a password first.
 */
async function confirmRemoval() {
  try {
    await removal.confirm()
  } catch (e) {
    const described = describeFailure(e, t)
    failure.value = apiErrorStatus(e) === 409
        ? {...described, message: t('passkeys.section.removeRefusedNoPassword'), reportable: false}
        : described
  }
}

async function saveRename(id: number, label: string) {
  try {
    await renamePasskey(id, label)
    renameTarget.value = null
    await reload()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function togglePasswordLogin(enabled: boolean) {
  failure.value = null
  try {
    await setPasswordLogin(enabled)
    notice.value = enabled ? '' : t('passkeys.section.passwordOffDone')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    await reload()
  }
}

async function toggleAskWithPassword(enabled: boolean) {
  failure.value = null
  try {
    await setAskWithPassword(enabled)
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    await reload()
  }
}
</script>

<template>
  <NeutralContainer v-if="status && status.mode !== 'OFF'" class="space-y-4">
    <SectionHeader>{{ t('passkeys.section.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('passkeys.explainer') }}</MutedText>

    <FailureAlert :failure="failure"/>
    <Alert v-if="notice" variant="info">{{ notice }}</Alert>

    <PasskeyList v-if="status.passkeys.length" :passkeys="status.passkeys"
                 @rename="entry => renameTarget = entry" @remove="removal.requestDelete"/>
    <MutedText v-else tag="p" size="sm">{{ t('passkeys.section.empty') }}</MutedText>

    <div class="flex flex-wrap items-center gap-3">
      <PrimaryButton v-if="supported" :icon="['fas', 'plus']" @click="showCreate = true">
        {{ t('passkeys.section.create') }}
      </PrimaryButton>
      <MutedText v-else tag="p" size="sm">{{ t('passkeys.errors.notSupported') }}</MutedText>
      <router-link class="text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                   to="/account/unlock-device">
        {{ t('passkeys.approve.title') }}
      </router-link>
    </div>

    <PasskeySwitches v-if="status.passkeys.length" :status="status"
                     @toggle-password-login="togglePasswordLogin"
                     @toggle-ask-with-password="toggleAskWithPassword"/>

    <PasskeyCreateModal v-model="showCreate" @created="reload"/>
    <PasskeyRenameModal v-model="renameTarget" @save="saveRename"/>
    <PasskeyRemoveModal v-model="removal.show.value"
                        :label="removal.target.value?.label ?? ''"
                        @confirm="confirmRemoval"/>
  </NeutralContainer>
</template>
