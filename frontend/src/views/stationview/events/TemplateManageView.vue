/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Modal from '@/components/feedback/Modal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventTemplate} from '@/api/events'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {configOf} from '@/components/profilefields/fieldLayout'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const templates = ref<EventTemplate[]>([])

const createOpen = ref(false)
const createName = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  templates.value = await events.listTemplates()
}, {autoLoad: loaded.value})

watch(loaded, (v) => { if (v) reload() })

async function createTemplate() {
  if (!createName.value.trim()) return
  try {
    const tpl = await events.createTemplate({name: createName.value.trim()})
    createOpen.value = false
    createName.value = ''
    router.push({name: 'event-template-edit', params: {id: tpl.id}})
  } catch { error.value = t('common.error') }
}

/**
 * Copies a template, questions and all, and opens the copy.
 *
 * <p>Most templates a station writes are a small change to one it already has, and writing the second
 * one from nothing means typing every question again. What is copied is everything the template says
 * about an event; the copy is opened straight away because the first thing anybody does with it is
 * change the part that differs.
 */
async function duplicateTemplate(id: number) {
  try {
    const {template, fields, restrictionUserTypes, reminderDays} = await events.getTemplate(id)
    const {id: _id, stationId: _stationId, name, ...settings} = template
    const copyName = t('eventTemplates.copyOf', {name})
    const copy = await events.createTemplate({name: copyName})
    await events.updateTemplate(copy.id, {...settings, name: copyName})
    if (fields.length > 0) {
      await events.setTemplateFields(copy.id, {
        fields: fields.map((field, position) => ({
          name: field.name,
          fieldType: field.fieldType,
          config: configOf(field.config),
          position,
          overview: field.overview,
          isPublic: field.isPublic,
          attendanceFieldId: field.attendanceFieldId ?? null,
        })),
      })
    }
    if (restrictionUserTypes.length > 0) {
      await events.setTemplateRestrictions(copy.id, {userTypes: restrictionUserTypes})
    }
    if (reminderDays.length > 0) {
      await events.setTemplateReminders(copy.id, reminderDays)
    }
    router.push({name: 'event-template-edit', params: {id: copy.id}})
  } catch { error.value = t('common.error') }
}

async function deleteTemplate(id: number) {
  try {
    await events.deleteTemplate(id)
    await reload()
  } catch { error.value = t('common.error') }
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-templates.title')"
      :subtitle="t('pages.event-templates.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-end">
        <PrimaryButton :icon="['fas', 'plus']" @click="createOpen = true">{{ t('eventTemplates.create') }}</PrimaryButton>
      </div>

      <AsyncSection
          :empty="templates.length === 0"
          :empty-message="t('eventTemplates.empty')"
          :error="error"
          :loading="loading"
      >
        <div class="space-y-2">
          <NeutralContainer
              v-for="tpl in templates"
              :key="tpl.id"
              :data-name="tpl.name"
              class="flex items-center justify-between"
              data-testid="template-row"
          >
            <div>
              <span class="font-medium">{{ tpl.name }}</span>
              <MutedText v-if="tpl.title" size="sm" class="ml-2">{{ tpl.title }}</MutedText>
            </div>
            <div class="flex items-center gap-2">
              <EditButton @click="router.push({name: 'event-template-edit', params: {id: tpl.id}})"/>
              <MutedIconButton
                  :icon="['fas', 'clone']"
                  :label="t('eventTemplates.duplicate')"
                  data-testid="duplicate-template"
                  @click="duplicateTemplate(tpl.id)"
              />
              <DeleteButton @click="deleteTemplate(tpl.id)"/>
            </div>
          </NeutralContainer>
        </div>
      </AsyncSection>

      <Modal v-model="createOpen">
        <div class="space-y-4">
          <SubHeader>{{ t('eventTemplates.create') }}</SubHeader>
          <FieldLabel>{{ t('eventTemplates.name') }}</FieldLabel>
          <TextInput v-model="createName" :placeholder="t('eventTemplates.namePlaceholder')"/>
          <div class="flex justify-end">
            <PrimaryButton :disabled="!createName.trim()" @click="createTemplate">{{ t('eventTemplates.create') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
