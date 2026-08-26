/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {reportCaughtError} from '@/util/devErrorReporter'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EventFieldList from './eventshared/EventFieldList.vue'
import EventReminderEditor from './eventshared/EventReminderEditor.vue'
import EventDefaultsSection from './templateeditview/EventDefaultsSection.vue'
import type {AttendanceTemplate, AttendanceTemplateField} from '@/api/attendance'
import type {EventCategory, EventFieldEntry, EventTemplateDetail} from '@/api/events'
import type {MemberGroup, UserTag} from '@/api/types'
import {attendance, events, memberGroups as memberGroupsApi, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const templateId = computed(() => Number(route.params.id))

const categories = ref<EventCategory[]>([])
const attendanceTemplates = ref<AttendanceTemplate[]>([])
const attendanceFields = ref<AttendanceTemplateField[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const loading = ref(true)
const error = ref('')

const name = ref('')
const title = ref('')
const description = ref('')
const categoryId = ref('')
const eventType = ref('')
const requiresRegistration = ref(false)
const requiresConfirmation = ref(false)
const registrationLimit = ref<number | undefined>(undefined)
const attendanceTemplateId = ref('')
const fields = ref<EventFieldEntry[]>([])
const reminderDays = ref<number[]>([])

onMounted(() => { if (loaded.value) loadData() })
watch(loaded, (v) => { if (v && loading.value) loadData() })

function seedForm(detail: EventTemplateDetail) {
  const tpl = detail.template
  name.value = tpl.name
  title.value = tpl.title ?? ''
  description.value = tpl.description ?? ''
  categoryId.value = tpl.categoryId ? String(tpl.categoryId) : ''
  eventType.value = tpl.eventType ?? ''
  requiresRegistration.value = tpl.requiresRegistration ?? false
  requiresConfirmation.value = tpl.requiresConfirmation ?? false
  registrationLimit.value = tpl.registrationLimit ?? undefined
  attendanceTemplateId.value = tpl.attendanceTemplateId ? String(tpl.attendanceTemplateId) : ''
  reminderDays.value = detail.reminderDays ?? []
  fields.value = detail.fields.map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: typeof f.config === 'string' ? (f.config ? JSON.parse(f.config) : {}) : (f.config ?? {}),
    value: '',
    overview: f.overview,
    attendanceFieldId: f.attendanceFieldId ?? null,
    isPublic: f.isPublic,
  }))
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [detail, cats, attTpls, memberGroups, userTags] = await Promise.all([
      events.getTemplate(templateId.value),
      events.listCategories(),
      attendance.listTemplates(),
      memberGroupsApi.listGroups(),
      userTagsApi.listTags(),
    ])
    categories.value = cats
    attendanceTemplates.value = attTpls
    groups.value = memberGroups
    tags.value = userTags

    const fieldResults = await Promise.all(attTpls.map(t => attendance.listTemplateFields(t.id)))
    attendanceFields.value = fieldResults.flat()

    seedForm(detail)
  } catch (e) {
    reportCaughtError(e, 'TemplateEditView.loadData')
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    await events.updateTemplate(templateId.value, {
      name: name.value,
      title: title.value || null,
      description: description.value || null,
      categoryId: categoryId.value ? Number(categoryId.value) : null,
      eventType: eventType.value || null,
      requiresRegistration: requiresRegistration.value || null,
      requiresConfirmation: requiresConfirmation.value || null,
      registrationLimit: registrationLimit.value ?? null,
      attendanceTemplateId: attendanceTemplateId.value ? Number(attendanceTemplateId.value) : null,
    })
    await events.setTemplateReminders(templateId.value, reminderDays.value)
    await events.setTemplateFields(templateId.value, {
      fields: fields.value.map((f, i) => ({
        name: f.name,
        fieldType: f.fieldType ?? 'STRING',
        config: typeof f.config === 'string' ? JSON.parse(f.config || '{}') : (f.config ?? {}),
        position: i,
        overview: f.overview,
        isPublic: f.isPublic,
        attendanceFieldId: f.attendanceFieldId,
      })),
    })
  } catch (e) {
    reportCaughtError(e, 'TemplateEditView.save')
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-template-edit.title')"
      :subtitle="t('pages.event-template-edit.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({name: 'event-templates'})"/>
        <SectionHeader>{{ t('eventTemplates.edit') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('eventTemplates.name') }}</SubHeader>
          <TextInput v-model="name" :placeholder="t('eventTemplates.namePlaceholder')"/>
        </NeutralContainer>

        <EventDefaultsSection
            v-model:title="title"
            v-model:description="description"
            v-model:category-id="categoryId"
            v-model:event-type="eventType"
            v-model:attendance-template-id="attendanceTemplateId"
            v-model:requires-registration="requiresRegistration"
            v-model:requires-confirmation="requiresConfirmation"
            v-model:registration-limit="registrationLimit"
            :categories="categories"
            :attendance-templates="attendanceTemplates"
        />

        <NeutralContainer>
          <EventReminderEditor v-model="reminderDays" />
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <EventFieldList
              v-model:fields="fields"
              :attendance-fields="attendanceFields"
              :groups="groups"
              :tags="tags"
          />
        </NeutralContainer>

        <SaveButton :disabled="!name.trim()" :action="save"/>
      </template>
    </div>
  </ViewContent>
</template>
