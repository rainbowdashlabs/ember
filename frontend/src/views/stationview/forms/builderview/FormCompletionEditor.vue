/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import { isOfferableLink } from '@/util/completionLink'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

/**
 * What the form says once it is sent, and where it offers to go on to: "Thanks, see you on Saturday"
 * and a link to the event, say. Left empty, the general thanks stays. A link that cannot be offered is
 * said so at once, while it is typed, rather than by a save that fails.
 */
const message = defineModel<string>('message', { required: true })
const link = defineModel<string>('link', { required: true })
const linkLabel = defineModel<string>('linkLabel', { required: true })

const { t } = useI18n()

const linkOffered = computed(() => isOfferableLink(link.value))
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3" data-testid="form-completion-editor">
      <FieldLabel>{{ t('forms.completion.title') }}</FieldLabel>
      <TextAreaInput v-model="message" :rows="2" :placeholder="t('forms.completion.message')"/>
      <div class="grid gap-3 sm:grid-cols-2">
        <TextInput v-model="link" :placeholder="t('forms.completion.link')"/>
        <TextInput v-model="linkLabel" :placeholder="t('forms.completion.linkLabel')"/>
      </div>
      <Alert v-if="!linkOffered" variant="error" data-testid="form-completion-link-refused">
        {{ t('forms.completion.notALink') }}
      </Alert>
      <FieldHint>{{ t('forms.completion.hint') }}</FieldHint>
    </div>
  </NeutralContainer>
</template>
