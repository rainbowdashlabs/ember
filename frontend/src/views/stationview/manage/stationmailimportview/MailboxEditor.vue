/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MailboxConnectionFields from './MailboxConnectionFields.vue'
import {MailSecurity} from '@/api/mailImport'
import type {Mailbox, MailboxRequest, MailSecurityName} from '@/api/mailImport'

/**
 * Adding a mailbox or changing one.
 *
 * <p>The password goes in and the server never hands it back, so changing a mailbox leaves whatever is
 * stored alone unless a new one is typed.
 */
const props = defineProps<{
  /** The mailbox being changed, or null when one is being added. */
  mailbox: Mailbox | null
  /** The shortest interval the operator allows, which the field will not go below. */
  minimumInterval: number
}>()

const emit = defineEmits<{
  save: [request: MailboxRequest]
  cancel: []
}>()

const {t} = useI18n()

const name = ref('')
const host = ref('')
const port = ref(993)
const security = ref<MailSecurityName>(MailSecurity.SSL)
const username = ref('')
const password = ref('')
const folder = ref('INBOX')
const enabled = ref(false)
const intervalMinutes = ref(props.minimumInterval)
const importFrom = ref(today())

const isNew = computed(() => props.mailbox === null)
const canSave = computed(() =>
    name.value.trim() !== ''
    && host.value.trim() !== ''
    && username.value.trim() !== ''
    && (!isNew.value || password.value !== ''))

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

watch(() => props.mailbox, (mailbox) => {
  name.value = mailbox?.name ?? ''
  host.value = mailbox?.host ?? ''
  port.value = mailbox?.port ?? 993
  security.value = mailbox?.security ?? MailSecurity.SSL
  username.value = mailbox?.username ?? ''
  folder.value = mailbox?.folder ?? 'INBOX'
  enabled.value = mailbox?.enabled ?? false
  intervalMinutes.value = mailbox?.intervalMinutes ?? props.minimumInterval
  importFrom.value = mailbox ? mailbox.importFrom.slice(0, 10) : today()
  password.value = ''
}, {immediate: true})

function save() {
  emit('save', {
    name: name.value.trim(),
    host: host.value.trim(),
    port: port.value,
    security: security.value,
    username: username.value.trim(),
    password: password.value === '' ? null : password.value,
    folder: folder.value.trim() || 'INBOX',
    enabled: enabled.value,
    intervalMinutes: Math.max(intervalMinutes.value, props.minimumInterval),
    importFrom: new Date(importFrom.value + 'T00:00:00Z').toISOString(),
  })
}
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <MailboxConnectionFields
          v-model:folder="folder"
          v-model:host="host"
          v-model:import-from="importFrom"
          v-model:interval-minutes="intervalMinutes"
          v-model:name="name"
          v-model:password="password"
          v-model:port="port"
          v-model:security="security"
          v-model:username="username"
          :is-new="isNew"
          :minimum-interval="minimumInterval"
      />

      <div class="flex items-center gap-2">
        <ToggleInput v-model="enabled" data-testid="mailbox-enabled"/>
        <span class="text-sm">{{ t('mailImport.field.enabled') }}</span>
      </div>

      <div class="flex flex-wrap justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!canSave" data-testid="mailbox-save" @click="save">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
