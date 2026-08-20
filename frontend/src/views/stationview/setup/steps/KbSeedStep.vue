/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import {knowledgeBase} from '@/api'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const pageName = ref('')
const pageDescription = ref('')
const pageContent = ref('')
const createdFileId = ref<number | null>(null)
const nameError = ref('')

const {running: saving, error: saveError, run: runSave} = useAsyncAction(async () => {
    if (createdFileId.value == null) {
        const file = await knowledgeBase.createMarkdownFile({
            folderId: null,
            name: pageName.value.trim(),
            description: pageDescription.value,
            content: pageContent.value,
        })
        createdFileId.value = file.id
    } else if (pageContent.value) {
        await knowledgeBase.updateMarkdownContent(createdFileId.value, pageContent.value)
    }
    await reload()
    goToNextStep(router, 'kb-seed')
})

const displayError = computed(() => nameError.value || saveError.value)

function save() {
    if (!pageName.value.trim()) {
        nameError.value = t('setup.steps.kb-seed.missing')
        return
    }
    nameError.value = ''
    return runSave()
}
</script>

<template>
  <SetupLayout
      step-id="kb-seed"
      skippable
      :save-label="t('setup.steps.kb-seed.createPage')"
      :saving="saving"
      @save="save"
  >
    <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>
    <InfoContainer class="space-y-1">
      <p class="font-medium text-sm">{{ t('setup.steps.kb-seed.aboutTitle') }}</p>
      <p class="text-sm">{{ t('setup.steps.kb-seed.aboutBody') }}</p>
    </InfoContainer>
    <div class="space-y-1">
      <FieldLabel>{{ t('setup.steps.kb-seed.pageName') }}</FieldLabel>
      <TextInput v-model="pageName" :placeholder="t('setup.steps.kb-seed.pagePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('setup.steps.kb-seed.pageDescription') }}</FieldLabel>
      <TextAreaInput v-model="pageDescription"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('setup.steps.kb-seed.pageContent') }}</FieldLabel>
      <MarkdownEditor v-model="pageContent"/>
    </div>
  </SetupLayout>
</template>
