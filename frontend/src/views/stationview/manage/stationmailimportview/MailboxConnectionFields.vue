/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import {MailSecurity} from '@/api/mailImport'
import type {MailSecurityName} from '@/api/mailImport'

/**
 * How to reach a mailbox and how often.
 *
 * <p>The password is asked for once. The server never hands it back, so leaving the field empty on an
 * existing mailbox leaves whatever is stored alone.
 */
const props = defineProps<{
  /** Whether this is a mailbox being added, which is the only case a password is required. */
  isNew: boolean
  /** The shortest interval the operator allows. */
  minimumInterval: number
}>()

const name = defineModel<string>('name', {required: true})
const host = defineModel<string>('host', {required: true})
const port = defineModel<number>('port', {required: true})
const security = defineModel<MailSecurityName>('security', {required: true})
const username = defineModel<string>('username', {required: true})
const password = defineModel<string>('password', {required: true})
const folder = defineModel<string>('folder', {required: true})
const intervalMinutes = defineModel<number>('intervalMinutes', {required: true})
const importFrom = defineModel<string>('importFrom', {required: true})

const {t} = useI18n()

/** The port the chosen encryption is usually on, so nobody has to remember two numbers. */
watch(security, (chosen) => {
  if (chosen === MailSecurity.SSL) port.value = 993
  if (chosen === MailSecurity.STARTTLS) port.value = 143
})
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
    <LabelledField :label="t('mailImport.field.name')">
      <TextInput v-model="name" data-testid="mailbox-name" :placeholder="t('mailImport.field.namePlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.field.folder')">
      <TextInput v-model="folder" data-testid="mailbox-folder"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.field.host')">
      <TextInput v-model="host" data-testid="mailbox-host"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.field.security')">
      <SelectInput v-model="security" data-testid="mailbox-security">
        <option :value="MailSecurity.SSL">{{ t('mailImport.security.SSL') }}</option>
        <option :value="MailSecurity.STARTTLS">{{ t('mailImport.security.STARTTLS') }}</option>
        <option :value="MailSecurity.NONE">{{ t('mailImport.security.NONE') }}</option>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('mailImport.field.port')">
      <NumberInput v-model="port" data-testid="mailbox-port"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.field.username')">
      <TextInput v-model="username" data-testid="mailbox-username"/>
    </LabelledField>
    <LabelledField :hint="!isNew" :label="t('mailImport.field.password')">
      <TextInput v-model="password" data-testid="mailbox-password" type="password"
                 :placeholder="isNew ? '' : t('mailImport.field.passwordUnchanged')"/>
    </LabelledField>
    <LabelledField :help="t('mailImport.field.intervalHint', {minutes: props.minimumInterval})"
                   :label="t('mailImport.field.interval')">
      <NumberInput v-model="intervalMinutes" data-testid="mailbox-interval" :min="minimumInterval"/>
    </LabelledField>
    <LabelledField :help="t('mailImport.field.importFromHint')" :label="t('mailImport.field.importFrom')">
      <DateInput v-model="importFrom" data-testid="mailbox-import-from"/>
    </LabelledField>
  </div>
</template>
