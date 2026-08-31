/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import * as federation from '@/api/federation'
import * as lending from '@/api/lending'
import type {ShareGrantName, ShareScopeName, ShareTarget} from '@/api/lending'

/**
 * The one place a station says what it offers a partner, used from the inventory and from a single
 * item alike. Nothing is offered until a row here says so, and the narrower row wins: an inventory
 * can go out with one piece kept back.
 */
const open = defineModel<boolean>({default: false})

const props = defineProps<{
  target: ShareTarget
  targetId: number
  targetName: string
}>()

const emit = defineEmits<{ saved: [] }>()

const {t} = useI18n()

const shared = ref(false)
const grant = ref<ShareGrantName>('GRANT')
const scope = ref<ShareScopeName>('ALL_PARTNERS')
const partnerIds = ref<string[]>([])
const partnerOptions = ref<{ value: string; label: string }[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const grantHint = computed(() => t(`lendingShare.grantHint.${props.target}`))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const partners = await federation.listPartners()
    partnerOptions.value = partners.map(p => ({value: String(p.partner.id), label: p.partnerStationName}))
    const setting = await lending.getShare(props.target, props.targetId)
    shared.value = setting.shared
    grant.value = setting.grant ?? 'GRANT'
    scope.value = setting.scope ?? 'ALL_PARTNERS'
    partnerIds.value = setting.partnerIds.map(String)
  } catch {
    error.value = t('lendingShare.loadError')
  } finally {
    loading.value = false
  }
}

watch(open, (isOpen) => {
  if (isOpen) load()
})

async function save() {
  saving.value = true
  error.value = ''
  try {
    const payload = {grant: grant.value, scope: scope.value, partnerIds: partnerIds.value.map(Number)}
    await lending.setShare(props.target, props.targetId, payload)
    shared.value = true
    emit('saved')
    open.value = false
  } catch {
    error.value = t('lendingShare.saveError')
  } finally {
    saving.value = false
  }
}

async function clear() {
  saving.value = true
  error.value = ''
  try {
    await lending.removeShare(props.target, props.targetId)
    shared.value = false
    emit('saved')
    open.value = false
  } catch {
    error.value = t('lendingShare.saveError')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4" data-testid="lending-share-modal">
      <SubHeader>{{ t('lendingShare.title') }}</SubHeader>
      <MutedText>{{ targetName }}</MutedText>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <MutedText v-if="loading">{{ t('lendingShare.loading') }}</MutedText>

      <template v-else>
        <div>
          <FieldLabel class="mb-1">{{ t('lendingShare.grant') }}</FieldLabel>
          <SelectInput v-model="grant" class="w-full" data-testid="lending-share-grant">
            <option value="GRANT">{{ t('lendingShare.grantValues.GRANT') }}</option>
            <option value="WITHHOLD">{{ t('lendingShare.grantValues.WITHHOLD') }}</option>
          </SelectInput>
          <FieldHint>{{ grantHint }}</FieldHint>
        </div>

        <div>
          <FieldLabel class="mb-1">{{ t('lendingShare.scope') }}</FieldLabel>
          <SelectInput v-model="scope" class="w-full" data-testid="lending-share-scope">
            <option value="ALL_PARTNERS">{{ t('lendingShare.scopeValues.ALL_PARTNERS') }}</option>
            <option value="SPECIFIC">{{ t('lendingShare.scopeValues.SPECIFIC') }}</option>
          </SelectInput>
        </div>

        <div v-if="scope === 'SPECIFIC'">
          <FieldLabel class="mb-1">{{ t('lendingShare.partners') }}</FieldLabel>
          <MultiSelectInput
              v-model="partnerIds"
              :options="partnerOptions"
              :placeholder="t('lendingShare.partnersPlaceholder')"
          />
          <FieldHint>{{ t('lendingShare.partnersHint') }}</FieldHint>
        </div>

        <div class="flex flex-wrap justify-end gap-2 pt-2">
          <SecondaryButton data-cancel @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton v-if="shared" :disabled="saving" @click="clear">{{ t('lendingShare.clear') }}</ErrorButton>
          <PrimaryButton :disabled="saving" data-testid="lending-share-save" @click="save">
            {{ t('lendingShare.save') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>
