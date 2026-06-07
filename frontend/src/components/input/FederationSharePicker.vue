/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {PartnerResponse} from '@/api/federation'

const props = defineProps<{
  shared: boolean
  scope: string
  partnerIds: number[]
  partners: PartnerResponse[]
  disabled?: boolean
  noPermissionHint?: string
}>()

const emit = defineEmits<{
  'update:shared': [value: boolean]
  'update:scope': [value: string]
  'update:partnerIds': [value: number[]]
}>()

const {t} = useI18n()

const activePartners = computed(() =>
    props.partners.filter(p => p.partner.status === 'ACTIVE')
)

function togglePartner(id: number, checked: boolean) {
  if (checked) {
    emit('update:partnerIds', [...props.partnerIds, id])
  } else {
    emit('update:partnerIds', props.partnerIds.filter(pid => pid !== id))
  }
}
</script>

<template>
  <div class="space-y-2">
    <label class="flex items-center gap-3">
      <ToggleInput :model-value="shared" :disabled="disabled" @update:model-value="emit('update:shared', $event)"/>
      <span class="text-sm">{{ t('federationShare.enable') }}</span>
    </label>

    <p v-if="disabled && noPermissionHint" class="text-xs text-primary italic">{{ noPermissionHint }}</p>

    <template v-if="shared && !disabled">
      <div class="space-y-1">
        <FieldLabel>{{ t('federationShare.scope') }}</FieldLabel>
        <SelectInput :model-value="scope" @update:model-value="emit('update:scope', $event ?? 'ALL_PARTNERS')">
          <option value="ALL_PARTNERS">{{ t('federationShare.scopeAll') }}</option>
          <option value="SPECIFIC_PARTNERS">{{ t('federationShare.scopeSpecific') }}</option>
        </SelectInput>
      </div>

      <div v-if="scope === 'SPECIFIC_PARTNERS' && activePartners.length > 0" class="space-y-1">
        <FieldLabel>{{ t('federationShare.partners') }}</FieldLabel>
        <div class="space-y-1">
          <label v-for="p in activePartners" :key="p.partner.id" class="flex items-center gap-2 text-sm">
            <input
                type="checkbox"
                :checked="partnerIds.includes(p.partner.id)"
                class="accent-primary"
                @change="togglePartner(p.partner.id, ($event.target as HTMLInputElement).checked)"
            />
            {{ p.partnerStationName }}
          </label>
        </div>
      </div>
    </template>

    <template v-else-if="shared && disabled">
      <p class="text-xs text-(--text-muted)">
        {{ scope === 'ALL_PARTNERS' ? t('federationShare.scopeAll') : t('federationShare.scopeSpecific') }}
      </p>
      <div v-if="scope === 'SPECIFIC_PARTNERS'" class="text-xs text-(--text-muted)">
        {{ activePartners.filter(p => partnerIds.includes(p.partner.id)).map(p => p.partnerStationName).join(', ') || '—' }}
      </div>
    </template>
  </div>
</template>
