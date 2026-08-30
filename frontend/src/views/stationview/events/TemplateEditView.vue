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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TemplateEditBody from './templateeditview/TemplateEditBody.vue'
import {emptyRestriction, toRestriction, type RestrictionSelection} from '@/components/input/restriction'
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

/**
 * The fields of the attendance sheet this template names, and of no other.
 *
 * <p>What a question of the template may be tied to. Every sheet of the station used to be offered
 * at once, and two sheets that both carry a field called "Ausbilder Anfänger" were indistinguishable
 * in the list, so a template could end up tied to a sheet it does not use. The answer then goes to a
 * sheet nobody opens.
 */
const sheetFields = computed(() => attendanceFields.value
    .filter(field => String(field.templateId) === attendanceTemplateId.value))
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
const restriction = ref<RestrictionSelection>(emptyRestriction())
const viewRestriction = ref<RestrictionSelection>(emptyRestriction())

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
  restriction.value = toRestriction(detail.restriction?.register)
  viewRestriction.value = toRestriction(detail.restriction?.view)
  fields.value = detail.fields.map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: typeof f.config === 'string' ? (f.config ? JSON.parse(f.config) : {}) : (f.config ?? {}),
    value: f.defaultValue ?? '',
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
    await events.setTemplateRestrictions(templateId.value, {
      register: restriction.value,
      view: viewRestriction.value,
    })
    await events.setTemplateFields(templateId.value, {
      fields: fields.value.map((f, i) => ({
        name: f.name,
        fieldType: f.fieldType ?? 'STRING',
        config: typeof f.config === 'string' ? JSON.parse(f.config || '{}') : (f.config ?? {}),
        position: i,
        overview: f.overview,
        isPublic: f.isPublic,
        attendanceFieldId: f.attendanceFieldId,
        defaultValue: f.value?.trim() ? f.value : null,
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

      <TemplateEditBody
          v-if="!loading"
          v-model:name="name"
          v-model:title="title"
          v-model:description="description"
          v-model:category-id="categoryId"
          v-model:event-type="eventType"
          v-model:attendance-template-id="attendanceTemplateId"
          v-model:requires-registration="requiresRegistration"
          v-model:requires-confirmation="requiresConfirmation"
          v-model:registration-limit="registrationLimit"
          v-model:restriction="restriction"
          v-model:view-restriction="viewRestriction"
          v-model:reminder-days="reminderDays"
          v-model:fields="fields"
          :categories="categories"
          :attendance-templates="attendanceTemplates"
          :sheet-fields="sheetFields"
          :groups="groups"
          :tags="tags"
          :save="save"
      />
    </div>
  </ViewContent>
</template>
