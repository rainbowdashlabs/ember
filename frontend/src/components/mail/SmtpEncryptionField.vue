/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {SmtpEncryption, type SmtpEncryptionName} from '@/api/mailProviders'

/**
 * How the connection to a mail server is secured.
 *
 * Unencrypted is offered for a relay on the same internal network that cannot do better, and says
 * plainly what it costs: the login and every mail cross the network readable.
 */
const encryption = defineModel<SmtpEncryptionName>({required: true})

const {t} = useI18n()

const CHOICES = [SmtpEncryption.STARTTLS, SmtpEncryption.IMPLICIT_TLS, SmtpEncryption.NONE]
</script>

<template>
  <LabelledField :label="t('mailChain.encryption.label')" :help="t('mailChain.encryption.hint')">
    <SelectInput v-model="encryption" :aria-label="t('mailChain.encryption.label')" data-testid="smtp-encryption">
      <option v-for="choice in CHOICES" :key="choice" :value="choice">
        {{ t(`mailChain.encryption.${choice}`) }}
      </option>
    </SelectInput>
    <Alert v-if="encryption === SmtpEncryption.NONE" variant="error">
      {{ t('mailChain.encryption.noneWarning') }}
    </Alert>
  </LabelledField>
</template>
