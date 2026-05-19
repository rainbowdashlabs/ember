/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, MemberGroup, Role} from '@/api/types'
import {Roles, EventTypes, needsDayOfWeek} from '@/api/types'
import type {EventFieldDefault} from '@/api/events'
import {attendance, events, memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const eventId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => eventId.value !== null)

const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const roles = ref<Role[]>([])
const groups = ref<MemberGroup[]>([])
const allTemplateFields = ref<AttendanceTemplateField[]>([])
const eventRoleIds = ref<number[]>([])
const eventGroupIds = ref<number[]>([])
const eventFieldDefaults = ref<EventFieldDefault[]>([])
const eventCustomFields = ref<{ name: string; value: string }[]>([])

const loading = ref(true)
const saving = ref(false)
const error = ref('')

// Form state
const eventName = ref('')
const eventDescription = ref('')
const eventType = ref<string>(EventTypes.RECURRING)
const eventDayOfWeek = ref('1')
const eventStartTime = ref('')
const eventEndTime = ref('')
const eventTemplateId = ref('')
const eventCategoryId = ref('')
const eventRequiresRegistration = ref(false)
const eventHasDeadline = ref(false)
const eventRegistrationDeadline = ref('')
const eventRequiresConfirmation = ref(false)
const selectedRoleIds = ref<Set<number>>(new Set())
const selectedGroupIds = ref<Set<number>>(new Set())
const fieldDefaults = ref<Map<number, { source: string; value: string }>>(new Map())

function toLocalDateTime(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [cats, tpl, allRoles, allGroups] = await Promise.all([
      events.listCategories(),
      attendance.listTemplates(),
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
    ])
    categories.value = cats
    templates.value = tpl
    roles.value = allRoles
    groups.value = allGroups

    const fieldResults = await Promise.all(tpl.map(t => attendance.listTemplateFields(t.id)))
    allTemplateFields.value = fieldResults.flat()

    if (isEdit.value) {
      const [ev, restrictions, defaults, fields] = await Promise.all([
        events.getEvent(eventId.value!),
        events.getRestrictions(eventId.value!),
        events.getFieldDefaults(eventId.value!),
        events.getEventFields(eventId.value!),
      ])

      eventCustomFields.value = fields.map(f => ({name: f.name ?? '', value: f.value ?? ''}))

      eventName.value = ev.name ?? ''
      eventDescription.value = ev.description ?? ''
      eventType.value = ev.eventType ?? EventTypes.RECURRING
      eventDayOfWeek.value = ev.dayOfWeek != null ? String(ev.dayOfWeek) : '1'
      eventStartTime.value = ev.startTime ? toLocalDateTime(ev.startTime) : ''
      eventEndTime.value = ev.endTime ? toLocalDateTime(ev.endTime) : ''
      eventTemplateId.value = ev.templateId != null ? String(ev.templateId) : ''
      eventCategoryId.value = ev.categoryId != null ? String(ev.categoryId) : ''
      eventRequiresRegistration.value = ev.requiresRegistration ?? false
      eventHasDeadline.value = !!ev.registrationDeadline
      eventRegistrationDeadline.value = ev.registrationDeadline ? toLocalDateTime(ev.registrationDeadline) : ''
      eventRequiresConfirmation.value = ev.requiresConfirmation ?? false

      eventRoleIds.value = restrictions.roleIds ?? []
      eventGroupIds.value = restrictions.groupIds ?? []
      selectedRoleIds.value = new Set(eventRoleIds.value)
      selectedGroupIds.value = new Set(eventGroupIds.value)

      const fdMap = new Map<number, { source: string; value: string }>()
      for (const fd of defaults) {
        fdMap.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
      }
      fieldDefaults.value = fdMap
      eventFieldDefaults.value = defaults
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const RESTRICTION_ROLES = [Roles.MEMBER, Roles.MEMBER_MANAGER, Roles.TEAM] as readonly string[]

const roleFriendlyNames: Record<string, string> = {
  MEMBER: 'Mitglied', MEMBER_MANAGER: 'Mitgliedsmanager', TEAM: 'Team',
}

const restrictionRoles = computed(() =>
    roles.value.filter(r => RESTRICTION_ROLES.includes(r.role))
)

function toggleRole(roleId: number) {
  const s = new Set(selectedRoleIds.value)
  if (s.has(roleId)) s.delete(roleId); else s.add(roleId)
  selectedRoleIds.value = s
}

function toggleGroup(groupId: number) {
  const s = new Set(selectedGroupIds.value)
  if (s.has(groupId)) s.delete(groupId); else s.add(groupId)
  selectedGroupIds.value = s
}

const EVENT_SOURCES = [
  {value: 'EVENT_NAME', label: 'Terminname'},
  {value: 'EVENT_DESCRIPTION', label: 'Beschreibung'},
  {value: 'EVENT_START_TIME', label: 'Startzeit'},
  {value: 'EVENT_END_TIME', label: 'Endzeit'},
]

const currentTemplateFields = computed(() => {
  if (!eventTemplateId.value) return []
  return allTemplateFields.value.filter(f => f.templateId === Number(eventTemplateId.value))
})

function getFieldDefault(fieldId: number): { source: string; value: string } {
  return fieldDefaults.value.get(fieldId) ?? {source: '', value: ''}
}

function setFieldDefaultSource(fieldId: number, source: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  if (!source) {
    m.delete(fieldId)
  } else {
    m.set(fieldId, {source, value: source === 'VALUE' ? existing.value : ''})
  }
  fieldDefaults.value = m
}

function setFieldDefaultValue(fieldId: number, value: string) {
  const existing = getFieldDefault(fieldId)
  const m = new Map(fieldDefaults.value)
  m.set(fieldId, {source: existing.source, value})
  fieldDefaults.value = m
}

function addCustomField() {
  eventCustomFields.value.push({name: '', value: ''})
}

function removeCustomField(index: number) {
  eventCustomFields.value.splice(index, 1)
}

async function submit() {
  saving.value = true
  error.value = ''
  try {
    const fieldDefaultEntries = [...fieldDefaults.value.entries()]
        .filter(([, v]) => v.source)
        .map(([fieldId, v]) => ({fieldId, source: v.source, value: v.value || undefined}))

    const data = {
      name: eventName.value,
      description: eventDescription.value || undefined,
      eventType: eventType.value,
      dayOfWeek: needsDayOfWeek(eventType.value) ? Number(eventDayOfWeek.value) : null,
      startTime: eventStartTime.value ? new Date(eventStartTime.value).toISOString() : undefined,
      endTime: eventEndTime.value ? new Date(eventEndTime.value).toISOString() : undefined,
      templateId: eventTemplateId.value ? Number(eventTemplateId.value) : undefined,
      categoryId: eventCategoryId.value ? Number(eventCategoryId.value) : undefined,
      requiresRegistration: eventRequiresRegistration.value,
      registrationDeadline: eventHasDeadline.value && eventRegistrationDeadline.value
          ? new Date(eventRegistrationDeadline.value).toISOString() : undefined,
      requiresConfirmation: eventRequiresConfirmation.value,
      restrictedRoleIds: [...selectedRoleIds.value],
      restrictedGroupIds: [...selectedGroupIds.value],
    }

    let savedEventId: number
    if (isEdit.value) {
      await events.updateEvent(eventId.value!, data)
      savedEventId = eventId.value!
    } else {
      const created = await events.createEvent(data)
      savedEventId = created.id
    }

    if (fieldDefaultEntries.length > 0 || isEdit.value) {
      await events.setFieldDefaults(savedEventId, fieldDefaultEntries)
    }

    const customFields = eventCustomFields.value.filter(f => f.name.trim())
    await events.setEventFields(savedEventId, {fields: customFields})

    router.push({name: 'events'})
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({name: 'events'})
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <SectionHeader>{{ isEdit ? t('events.editEvent') : t('events.addEvent') }}</SectionHeader>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('events.general') }}</SubHeader>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('events.name') }}</label>
            <TextInput v-model="eventName" :placeholder="t('events.namePlaceholder')"/>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('events.description') }}</label>
            <TextInput v-model="eventDescription" :placeholder="t('events.descriptionPlaceholder')"/>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('events.type') }}</label>
            <SelectInput v-model="eventType">
              <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
              <option :value="EventTypes.MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
              <option :value="EventTypes.QUARTERLY">{{ t('events.typeQuarterly') }}</option>
              <option :value="EventTypes.YEARLY">{{ t('events.typeYearly') }}</option>
              <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
            </SelectInput>
          </div>

          <div v-if="needsDayOfWeek(eventType)" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('events.dayOfWeek') }}</label>
            <SelectInput v-model="eventDayOfWeek">
              <option value="1">Montag</option>
              <option value="2">Dienstag</option>
              <option value="3">Mittwoch</option>
              <option value="4">Donnerstag</option>
              <option value="5">Freitag</option>
              <option value="6">Samstag</option>
              <option value="7">Sonntag</option>
            </SelectInput>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('events.startTime') }}</label>
              <DateTimeInput v-model="eventStartTime"/>
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('events.endTime') }}</label>
              <DateTimeInput v-model="eventEndTime"/>
            </div>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('events.category') }}</label>
            <SelectInput v-model="eventCategoryId">
              <option value="">{{ t('events.noCategory') }}</option>
              <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
            </SelectInput>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('events.eventFields') }}</SubHeader>
            <SecondaryButton class="text-xs" @click="addCustomField">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
              {{ t('events.addField') }}
            </SecondaryButton>
          </div>
          <p class="text-xs text-(--text-muted)">{{ t('events.eventFieldsHint') }}</p>
          <div v-if="eventCustomFields.length === 0" class="text-sm text-(--text-muted) py-2">
            {{ t('events.noFields') }}
          </div>
          <div v-for="(field, index) in eventCustomFields" :key="index"
               class="flex items-start gap-2">
            <div class="flex-1 grid gap-2 sm:grid-cols-2">
              <TextInput v-model="field.name" :placeholder="t('events.fieldNamePlaceholder')"/>
              <TextInput v-model="field.value" :placeholder="t('events.fieldValuePlaceholder')"/>
            </div>
            <DeleteButton class="mt-1" @click="removeCustomField(index)"/>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('events.template') }}</SubHeader>

          <div class="space-y-1">
            <SelectInput v-model="eventTemplateId">
              <option value="">{{ t('events.noTemplate') }}</option>
              <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
            </SelectInput>
            <p class="text-xs text-(--text-muted)">{{ t('events.templateHint') }}</p>
          </div>

          <div v-if="currentTemplateFields.length > 0" class="space-y-3">
            <label class="block text-sm font-medium">{{ t('events.fieldDefaults') }}</label>
            <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
            <div class="space-y-2">
              <div v-for="field in currentTemplateFields" :key="field.id"
                   class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
                <div class="text-sm font-medium">{{ field.name }} <span
                    class="text-xs text-(--text-muted)">({{ field.fieldType }})</span></div>
                <div class="grid gap-2 sm:grid-cols-2">
                  <SelectInput
                      :model-value="getFieldDefault(field.id).source"
                      @update:model-value="setFieldDefaultSource(field.id, $event ?? '')"
                  >
                    <option value="">{{ t('events.noDefault') }}</option>
                    <option value="VALUE">{{ t('events.staticValue') }}</option>
                    <option v-for="src in EVENT_SOURCES" :key="src.value" :value="src.value">{{ src.label }}</option>
                  </SelectInput>
                  <TextInput
                      v-if="getFieldDefault(field.id).source === 'VALUE'"
                      :model-value="getFieldDefault(field.id).value"
                      :placeholder="t('events.defaultValuePlaceholder')"
                      @update:model-value="setFieldDefaultValue(field.id, $event ?? '')"
                  />
                </div>
              </div>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('events.registration') }}</SubHeader>

          <div class="flex items-center justify-between">
            <label class="text-sm font-medium">{{ t('events.requiresRegistration') }}</label>
            <ToggleInput v-model="eventRequiresRegistration"/>
          </div>

          <template v-if="eventRequiresRegistration">
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</label>
              <ToggleInput v-model="eventRequiresConfirmation"/>
            </div>
            <p class="text-xs text-(--text-muted)">{{ t('events.requiresConfirmationHint') }}</p>

            <div class="flex items-center justify-between">
              <label class="text-sm font-medium">{{ t('events.hasDeadline') }}</label>
              <ToggleInput v-model="eventHasDeadline"/>
            </div>

            <div v-if="eventHasDeadline" class="space-y-1">
              <label class="block text-sm font-medium">{{ t('events.registrationDeadline') }}</label>
              <DateTimeInput v-model="eventRegistrationDeadline"/>
            </div>
          </template>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('events.restrictions') }}</SubHeader>

          <div class="space-y-2">
            <label class="block text-sm font-medium">{{ t('events.restrictToRoles') }}</label>
            <p class="text-xs text-(--text-muted)">{{ t('events.restrictToRolesHint') }}</p>
            <div class="flex flex-wrap gap-2">
              <button
                  v-for="role in restrictionRoles"
                  :key="role.id"
                  :class="selectedRoleIds.has(role.id)
                  ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
                  : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                  class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
                  type="button"
                  @click="toggleRole(role.id)"
              >
                {{ roleFriendlyNames[role.role] ?? role.role }}
              </button>
            </div>
          </div>

          <div v-if="groups.length > 0" class="space-y-2">
            <label class="block text-sm font-medium">{{ t('events.restrictToGroups') }}</label>
            <div class="flex flex-wrap gap-2">
              <button
                  v-for="group in groups"
                  :key="group.id"
                  :class="selectedGroupIds.has(group.id)
                  ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
                  : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                  class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
                  type="button"
                  @click="toggleGroup(group.id)"
              >
                {{ group.name }}
              </button>
            </div>
          </div>
        </NeutralContainer>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="goBack">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !eventName || !eventStartTime || !eventEndTime" @click="submit">
            {{ saving ? t('common.loading') : t('common.save') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
