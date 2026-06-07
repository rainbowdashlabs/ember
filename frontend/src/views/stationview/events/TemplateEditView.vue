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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EventFieldList from './eventshared/EventFieldList.vue'
import EventReminderEditor from './eventshared/EventReminderEditor.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, EventFieldEntry} from '@/api/types'
import {EventTypes} from '@/api/types'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const templateId = computed(() => Number(route.params.id))

const categories = ref<EventCategory[]>([])
const attendanceTemplates = ref<AttendanceTemplate[]>([])
const attendanceFields = ref<AttendanceTemplateField[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

// Form state
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [detail, cats, attTpls] = await Promise.all([
      events.getTemplate(templateId.value),
      events.listCategories(),
      attendance.listTemplates(),
    ])
    categories.value = cats
    attendanceTemplates.value = attTpls

    // Load attendance fields for all templates
    const fieldResults = await Promise.all(attTpls.map(t => attendance.listTemplateFields(t.id)))
    attendanceFields.value = fieldResults.flat()

    // Populate form
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
  } catch (e) {
    reportCaughtError(e, 'TemplateEditView.loadData')
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
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
    success.value = t('eventTemplates.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    reportCaughtError(e, 'TemplateEditView.save')
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({name: 'event-templates'})"/>
        <SectionHeader>{{ t('eventTemplates.edit') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <!-- Basic info -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('eventTemplates.name') }}</SubHeader>
          <TextInput v-model="name" :placeholder="t('eventTemplates.namePlaceholder')"/>
        </NeutralContainer>

        <!-- Event defaults -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('eventTemplates.eventDefaults') }}</SubHeader>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-1">
              <FieldLabel>{{ t('eventTemplates.eventTitle') }}</FieldLabel>
              <TextInput v-model="title" :placeholder="t('eventTemplates.eventTitlePlaceholder')"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('eventTemplates.category') }}</FieldLabel>
              <SelectInput v-model="categoryId" class="w-full">
                <option value="">{{ t('eventTemplates.noCategory') }}</option>
                <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('eventTemplates.eventType') }}</FieldLabel>
              <SelectInput v-model="eventType" class="w-full">
                <option value="">{{ t('eventTemplates.noDefault') }}</option>
                <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
                <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
                <option :value="EventTypes.MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
                <option :value="EventTypes.QUARTERLY">{{ t('events.typeQuarterly') }}</option>
                <option :value="EventTypes.YEARLY">{{ t('events.typeYearly') }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('eventTemplates.attendanceTemplate') }}</FieldLabel>
              <SelectInput v-model="attendanceTemplateId" class="w-full">
                <option value="">{{ t('eventTemplates.noDefault') }}</option>
                <option v-for="tpl in attendanceTemplates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
              </SelectInput>
            </div>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventDescription') }}</FieldLabel>
            <TextAreaInput v-model="description" :rows="3"/>
          </div>

          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
            <ToggleInput v-model="requiresRegistration"/>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
            <ToggleInput v-model="requiresConfirmation"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
            <NumberInput v-model="registrationLimit" :placeholder="t('events.registrationLimitHint')"/>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <EventReminderEditor v-model="reminderDays" />
        </NeutralContainer>

        <!-- Fields -->
        <NeutralContainer class="space-y-4">
          <EventFieldList v-model:fields="fields" :attendance-fields="attendanceFields"/>
        </NeutralContainer>

        <!-- Save -->
        <PrimaryButton :disabled="saving || !name.trim()" @click="save">
          {{ saving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </template>
    </div>
  </ViewContent>
</template>
