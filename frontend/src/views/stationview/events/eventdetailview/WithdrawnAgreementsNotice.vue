/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {RegistrationResponse} from '@/api/generated/schema'

/**
 * The registrations whose signed agreement was withdrawn while they stood, named for whoever runs the
 * appointment. The agreement is asked for again; the flag stays until it is signed anew.
 */
const props = defineProps<{
  registrations: RegistrationResponse[]
}>()

const {t} = useI18n()

const flagged = computed(() => props.registrations.filter(registration => registration.agreementWithdrawnAt))
</script>

<template>
  <Alert v-if="flagged.length > 0" variant="error" data-testid="withdrawn-agreements">
    {{ t('events.documents.agreementWithdrawnBy', {names: flagged.map(registration => registration.memberName).join(', ')}) }}
  </Alert>
</template>
