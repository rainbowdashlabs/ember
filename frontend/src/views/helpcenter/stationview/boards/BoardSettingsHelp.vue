/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.boardSettings.title')" :subtitle="t('helpCenter.boardSettings.subtitle')">
    <HelpSection :title="t('helpCenter.boardSettings.whatIs')">
      <p>{{ t('helpCenter.boardSettings.whatIsText') }}</p>
      <p>{{ t('helpCenter.boardSettings.whatIsText2') }}</p>
    </HelpSection>

    <!-- Dummy UI: General settings -->
    <HelpSection :title="t('helpCenter.boardSettings.generalTitle')">
      <p>{{ t('helpCenter.boardSettings.generalText') }}</p>
      <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('common.general') }}</SubHeader>
        <div class="space-y-3">
          <div>
            <FieldLabel class="mb-1">{{ t('boards.boardName') }}</FieldLabel>
            <TextInput model-value="Aufgaben Jugendfeuerwehr" />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
            <TextAreaInput model-value="Alle Aufgaben rund um die Jugendfeuerwehr" :rows="3" />
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('boards.hideDoneAfterDays') }}</FieldLabel>
            <NumberInput :model-value="7" :min="1" :max="365" />
          </div>
          <div class="flex items-center justify-between">
            <FieldLabel>{{ t('boards.backlog') }}</FieldLabel>
            <ToggleInput :model-value="true" />
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.boardSettings.archiveTitle')">
      <p>{{ t('helpCenter.boardSettings.archiveText') }}</p>
    </HelpSection>

    <!-- Dummy UI: Lanes -->
    <HelpSection :title="t('helpCenter.boardSettings.lanesTitle')">
      <p>{{ t('helpCenter.boardSettings.lanesText') }}</p>
      <p>{{ t('helpCenter.boardSettings.lanesText2') }}</p>
      <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.lanes') }}</SubHeader>
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <ColorInput model-value="#3b82f6" />
            <TextInput model-value="Zu erledigen" class="flex-1" />
            <IconButton :icon="['fas', 'chevron-up']" label="Move up" disabled />
            <IconButton :icon="['fas', 'chevron-down']" label="Move down" />
            <IconButton :icon="['fas', 'xmark']" label="Remove" />
          </div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <ColorInput model-value="#f59e0b" />
            <TextInput model-value="In Arbeit" class="flex-1" />
            <IconButton :icon="['fas', 'chevron-up']" label="Move up" />
            <IconButton :icon="['fas', 'chevron-down']" label="Move down" />
            <IconButton :icon="['fas', 'xmark']" label="Remove" />
          </div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <ColorInput model-value="#22c55e" />
            <TextInput model-value="Fertig" class="flex-1" />
            <IconButton :icon="['fas', 'chevron-up']" label="Move up" />
            <IconButton :icon="['fas', 'chevron-down']" label="Move down" disabled />
            <IconButton :icon="['fas', 'xmark']" label="Remove" />
          </div>
        </div>
        <div class="flex gap-2 mt-3">
          <TextInput model-value="" :placeholder="t('boards.addLane')" class="flex-1" />
          <SecondaryButton>
            <font-awesome-icon :icon="['fas', 'plus']" />
          </SecondaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.boardSettings.laneMoveTitle')">
      <p>{{ t('helpCenter.boardSettings.laneMoveText') }}</p>
      <p>{{ t('helpCenter.boardSettings.laneMoveText2') }}</p>
    </HelpSection>

    <!-- Dummy UI: Custom fields -->
    <HelpSection :title="t('helpCenter.boardSettings.fieldsTitle')">
      <p>{{ t('helpCenter.boardSettings.fieldsText') }}</p>
      <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
        <div class="space-y-2">
          <div class="border border-(--border) rounded-theme p-2">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
              <TextInput model-value="Zeitaufwand (Stunden)" :placeholder="t('boards.fieldName')" class="flex-1" />
              <SelectInput model-value="NUMBER">
                <option value="STRING">{{ t('boards.fieldTypeString') }}</option>
                <option value="NUMBER">{{ t('boards.fieldTypeNumber') }}</option>
                <option value="BOOLEAN">{{ t('boards.fieldTypeBoolean') }}</option>
                <option value="ENUM">{{ t('boards.fieldTypeEnum') }}</option>
                <option value="DATE">{{ t('boards.fieldTypeDate') }}</option>
                <option value="LANE_ASSIGNEE">{{ t('boards.fieldTypeLaneAssignee') }}</option>
              </SelectInput>
              <IconButton :icon="['fas', 'chevron-up']" label="Move up" disabled />
              <IconButton :icon="['fas', 'chevron-down']" label="Move down" />
              <IconButton :icon="['fas', 'xmark']" label="Remove" />
            </div>
          </div>
          <div class="border border-(--border) rounded-theme p-2">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
              <TextInput model-value="Kategorie" :placeholder="t('boards.fieldName')" class="flex-1" />
              <SelectInput model-value="ENUM">
                <option value="STRING">{{ t('boards.fieldTypeString') }}</option>
                <option value="NUMBER">{{ t('boards.fieldTypeNumber') }}</option>
                <option value="BOOLEAN">{{ t('boards.fieldTypeBoolean') }}</option>
                <option value="ENUM">{{ t('boards.fieldTypeEnum') }}</option>
                <option value="DATE">{{ t('boards.fieldTypeDate') }}</option>
                <option value="LANE_ASSIGNEE">{{ t('boards.fieldTypeLaneAssignee') }}</option>
              </SelectInput>
              <IconButton :icon="['fas', 'chevron-up']" label="Move up" />
              <IconButton :icon="['fas', 'chevron-down']" label="Move down" disabled />
              <IconButton :icon="['fas', 'xmark']" label="Remove" />
            </div>
            <div class="pl-6 mt-1">
              <TextInput model-value="Ausrüstung, Ausbildung, Organisation" :placeholder="t('boards.fieldOptions')" class="text-sm" />
            </div>
          </div>
        </div>
        <div class="flex gap-2 mt-3">
          <TextInput model-value="" :placeholder="t('boards.addField')" class="flex-1" />
          <SelectInput model-value="STRING">
            <option value="STRING">{{ t('boards.fieldTypeString') }}</option>
            <option value="NUMBER">{{ t('boards.fieldTypeNumber') }}</option>
            <option value="BOOLEAN">{{ t('boards.fieldTypeBoolean') }}</option>
            <option value="ENUM">{{ t('boards.fieldTypeEnum') }}</option>
            <option value="DATE">{{ t('boards.fieldTypeDate') }}</option>
            <option value="LANE_ASSIGNEE">{{ t('boards.fieldTypeLaneAssignee') }}</option>
          </SelectInput>
          <SecondaryButton>
            <font-awesome-icon :icon="['fas', 'plus']" />
          </SecondaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.boardSettings.fieldTypesTitle')">
      <p>{{ t('helpCenter.boardSettings.fieldTypesText') }}</p>
      <ul class="list-disc ml-4 space-y-1">
        <li><strong>{{ t('boards.fieldTypeString') }}:</strong> {{ t('helpCenter.boardSettings.typeStringDesc') }}</li>
        <li><strong>{{ t('boards.fieldTypeNumber') }}:</strong> {{ t('helpCenter.boardSettings.typeNumberDesc') }}</li>
        <li><strong>{{ t('boards.fieldTypeBoolean') }}:</strong> {{ t('helpCenter.boardSettings.typeBooleanDesc') }}</li>
        <li><strong>{{ t('boards.fieldTypeEnum') }}:</strong> {{ t('helpCenter.boardSettings.typeEnumDesc') }}</li>
        <li><strong>{{ t('boards.fieldTypeDate') }}:</strong> {{ t('helpCenter.boardSettings.typeDateDesc') }}</li>
        <li><strong>{{ t('boards.fieldTypeLaneAssignee') }}:</strong> {{ t('helpCenter.boardSettings.typeLaneAssigneeDesc') }}</li>
      </ul>
    </HelpSection>

    <!-- Dummy UI: Federation -->
    <HelpSection :title="t('helpCenter.boardSettings.federationTitle')">
      <p>{{ t('helpCenter.boardSettings.federationText') }}</p>
      <p>{{ t('helpCenter.boardSettings.federationText2') }}</p>
      <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.federation') }}</SubHeader>
        <div class="space-y-2">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium text-sm min-w-24">{{ t('helpCenter.boardSettings.partnerExample') }}</span>
            <div class="flex items-center gap-1">
              <span class="text-xs text-(--text-muted) shrink-0">{{ t('boards.accessMode') }}:</span>
              <SelectInput model-value="FULL">
                <option value="READ_ONLY">{{ t('boards.shareModeReadOnly') }}</option>
                <option value="FULL">{{ t('boards.shareModeFull') }}</option>
              </SelectInput>
            </div>
            <div class="flex items-center gap-1">
              <span class="text-xs text-(--text-muted) shrink-0">{{ t('boards.minViewRole') }}:</span>
              <SelectInput model-value="USER">
                <option value="USER">{{ t('boards.requiredRoleUser') }}</option>
                <option value="TEAM">{{ t('boards.requiredRoleTeam') }}</option>
                <option value="MANAGER">{{ t('boards.requiredRoleManager') }}</option>
              </SelectInput>
            </div>
            <DeleteButton />
          </div>
        </div>
        <div class="flex gap-2 mt-3">
          <SelectInput model-value="" class="flex-1">
            <option value="">{{ t('boards.federationShare') }}...</option>
            <option value="2">{{ t('helpCenter.boardSettings.partnerExample2') }}</option>
          </SelectInput>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'plus']" />
          </SecondaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.boardSettings.shareModesTitle')">
      <p>{{ t('helpCenter.boardSettings.shareModesText') }}</p>
      <ul class="list-disc ml-4 space-y-1">
        <li><strong>{{ t('boards.shareModeReadOnly') }}:</strong> {{ t('helpCenter.boardSettings.readOnlyDesc') }}</li>
        <li><strong>{{ t('boards.shareModeFull') }}:</strong> {{ t('helpCenter.boardSettings.fullDesc') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.boardSettings.autoSaveTitle')">
      <p>{{ t('helpCenter.boardSettings.autoSaveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.boardSettings.tip') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.boardSettings.tip2') }}</HelpTip>
  </HelpArticle>
</template>
