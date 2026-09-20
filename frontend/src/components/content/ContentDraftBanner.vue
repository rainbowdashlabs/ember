/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {formatDateTime} from '@/util/format'

/**
 * Says that there is unsaved writing from last time, and asks what to do with it.
 *
 * <p>Asking rather than restoring is the whole point. What the server holds is what everybody else
 * sees, and a draft from last Tuesday quietly replacing a page a colleague has since corrected
 * would lose more than it saves. The reader knows which of the two they meant; nobody else does.
 */
defineProps<{
  savedAt: number
}>()

const emit = defineEmits<{restore: []; discard: []}>()

const {t} = useI18n()
</script>

<template>
  <Alert variant="info" data-testid="content-draft-banner">
    <div class="space-y-2">
      <p>{{ t('contentDraft.found', {when: formatDateTime(new Date(savedAt).toISOString())}) }}</p>
      <ButtonRow pair align="start">
        <PrimaryButton data-testid="content-draft-restore" @click="emit('restore')">
          {{ t('contentDraft.restore') }}
        </PrimaryButton>
        <SecondaryButton data-testid="content-draft-discard" @click="emit('discard')">
          {{ t('contentDraft.discard') }}
        </SecondaryButton>
      </ButtonRow>
    </div>
  </Alert>
</template>
