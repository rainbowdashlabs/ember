/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {SmtpEncryption} from '@/api/generated/schema'
import {usualSmtpPort} from '@/util/mailProviders'

/**
 * How the connection to a mail server is secured.
 *
 * Unencrypted is offered for a relay on the same internal network that cannot do better, and says
 * plainly what it costs: the login and every mail cross the network readable.
 */
const encryption = defineModel<SmtpEncryption>({required: true})

const props = defineProps<{
  /**
   * The port the server is reached on, compared with the one usual for the chosen encryption. A
   * port that differs is pointed out and never refused: some providers answer elsewhere on purpose.
   */
  port?: number | null
}>()

const {t} = useI18n()

const CHOICES = [SmtpEncryption.STARTTLS, SmtpEncryption.IMPLICIT_TLS, SmtpEncryption.NONE]

const usualPort = computed(() => usualSmtpPort(encryption.value))
const unusualPort = computed(() => !!usualPort.value && !!props.port && props.port !== usualPort.value)
</script>

<template>
  <LabelledField :label="t('mailChain.encryption.label')" :help="t('mailChain.encryption.hint')">
    <SelectInput v-model="encryption" :aria-label="t('mailChain.encryption.label')" data-testid="smtp-encryption">
      <option v-for="choice in CHOICES" :key="choice" :value="choice">
        {{ t(`mailChain.encryption.${choice}`) }}
      </option>
    </SelectInput>
    <p v-if="usualPort" class="text-xs text-(--text-muted)" data-testid="smtp-usual-port">
      {{ t('mailChain.encryption.usualPort', {port: usualPort}) }}
    </p>
    <Alert v-if="unusualPort" variant="info" data-testid="smtp-unusual-port">
      {{ t('mailChain.encryption.unusualPort', {port: props.port, usual: usualPort, encryption: t(`mailChain.encryption.${encryption}`)}) }}
    </Alert>
    <Alert v-if="encryption === SmtpEncryption.NONE" variant="error">
      {{ t('mailChain.encryption.noneWarning') }}
    </Alert>
  </LabelledField>
</template>
