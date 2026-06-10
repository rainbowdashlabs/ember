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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import BulletList from '@/components/typography/BulletList.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.pages.title')" :subtitle="t('helpCenter.pages.subtitle')">
    <HelpSection :title="t('helpCenter.pages.whatIs')">
      <p>{{ t('helpCenter.pages.whatIsText') }}</p>
      <p>{{ t('helpCenter.pages.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.listTitle')">
      <p>{{ t('helpCenter.pages.listText') }}</p>
      <p>{{ t('helpCenter.pages.listText2') }}</p>
    </HelpSection>

    <!-- Dummy: Pages list -->
    <HelpSection :title="t('helpCenter.pages.exampleTitle')">
      <NeutralContainer class="space-y-2">
        <div class="flex items-center justify-between flex-wrap gap-2 mb-3">
          <span class="font-semibold">{{ t('stationPages.title') }}</span>
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
            {{ t('stationPages.createPage') }}
          </PrimaryButton>
        </div>

        <!-- Page row 1: published, landing page -->
        <NeutralContainer class="flex items-center gap-3">
          <div class="text-[var(--text-muted)] shrink-0">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
          </div>
          <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ t('helpCenter.pages.dummyPage1') }}</span>
            <span class="text-xs text-[var(--text-muted)]">/{{ t('helpCenter.pages.dummySlug1') }}</span>
            <SuccessBadge>{{ t('stationPages.published') }}</SuccessBadge>
            <font-awesome-icon :icon="['fas', 'star']" class="h-4 w-4 text-info-accent"/>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <EditButton/>
            <IconButton :icon="['fas', 'clone']" :label="t('stationPages.duplicate')"/>
            <IconButton :icon="['fas', 'eye-slash']" :label="t('stationPages.unpublish')"/>
            <DeleteButton/>
          </div>
        </NeutralContainer>

        <!-- Page row 2: draft -->
        <NeutralContainer class="flex items-center gap-3" style="margin-left: 1.5rem">
          <div class="text-[var(--text-muted)] shrink-0">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
          </div>
          <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ t('helpCenter.pages.dummyPage2') }}</span>
            <span class="text-xs text-[var(--text-muted)]">/{{ t('helpCenter.pages.dummySlug2') }}</span>
            <SecondaryBadge>{{ t('stationPages.draft') }}</SecondaryBadge>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <EditButton/>
            <IconButton :icon="['fas', 'clone']" :label="t('stationPages.duplicate')"/>
            <IconButton :icon="['fas', 'eye']" :label="t('stationPages.publish')"/>
            <DeleteButton/>
          </div>
        </NeutralContainer>

        <!-- Page row 3: published, child -->
        <NeutralContainer class="flex items-center gap-3" style="margin-left: 1.5rem">
          <div class="text-[var(--text-muted)] shrink-0">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="h-4 w-4"/>
          </div>
          <div class="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ t('helpCenter.pages.dummyPage3') }}</span>
            <span class="text-xs text-[var(--text-muted)]">/{{ t('helpCenter.pages.dummySlug3') }}</span>
            <SuccessBadge>{{ t('stationPages.published') }}</SuccessBadge>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <EditButton/>
            <IconButton :icon="['fas', 'clone']" :label="t('stationPages.duplicate')"/>
            <IconButton :icon="['fas', 'eye-slash']" :label="t('stationPages.unpublish')"/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.statusTitle')">
      <p>{{ t('helpCenter.pages.statusText') }}</p>
      <BulletList>
        <li><SuccessBadge>{{ t('stationPages.published') }}</SuccessBadge> {{ t('helpCenter.pages.statusPublished') }}</li>
        <li><SecondaryBadge>{{ t('stationPages.draft') }}</SecondaryBadge> {{ t('helpCenter.pages.statusDraft') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.landingTitle')">
      <p>{{ t('helpCenter.pages.landingText') }}</p>
      <p>{{ t('helpCenter.pages.landingText2') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PAGE_EDIT]" :label="t('helpCenter.permissionLabel.pageEdit')">
      <HelpSection :title="t('helpCenter.pages.createTitle')">
        <p>{{ t('helpCenter.pages.createText') }}</p>
        <p>{{ t('helpCenter.pages.createText2') }}</p>
      </HelpSection>

      <!-- Dummy: Create modal -->
      <HelpSection :title="t('helpCenter.pages.createModalTitle')">
        <NeutralContainer class="space-y-3">
          <span class="font-semibold">{{ t('stationPages.createPage') }}</span>
          <div>
            <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.titleLabel') }}</label>
            <TextInput :model-value="t('helpCenter.pages.dummyNewTitle')" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
          </div>
          <div>
            <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.parent') }}</label>
            <SelectInput :model-value="''">
              <option value="">{{ t('stationPages.editor.noParent') }}</option>
            </SelectInput>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton>{{ t('common.create') }}</PrimaryButton>
          </div>
        </NeutralContainer>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.editorTitle')">
        <p>{{ t('helpCenter.pages.editorText') }}</p>
        <p>{{ t('helpCenter.pages.editorText2') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.metadataTitle')">
        <p>{{ t('helpCenter.pages.metadataText') }}</p>
        <BulletList>
          <li><strong>{{ t('stationPages.editor.titleLabel') }}</strong>: {{ t('helpCenter.pages.metaFieldTitle') }}</li>
          <li><strong>{{ t('stationPages.editor.slugLabel') }}</strong>: {{ t('helpCenter.pages.metaFieldSlug') }}</li>
          <li><strong>{{ t('stationPages.editor.parent') }}</strong>: {{ t('helpCenter.pages.metaFieldParent') }}</li>
          <li><strong>{{ t('stationPages.editor.metaDescription') }}</strong>: {{ t('helpCenter.pages.metaFieldDescription') }}</li>
        </BulletList>
      </HelpSection>

      <!-- Dummy: Editor metadata -->
      <HelpSection :title="t('helpCenter.pages.editorDummyTitle')">
        <NeutralContainer class="space-y-3">
          <span class="font-semibold">{{ t('stationPages.editor.metadata') }}</span>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.titleLabel') }}</label>
              <TextInput :model-value="t('helpCenter.pages.dummyPage1')" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
            </div>
            <div>
              <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.slugLabel') }}</label>
              <TextInput :model-value="t('helpCenter.pages.dummySlug1')" :placeholder="t('stationPages.editor.slugPlaceholder')"/>
            </div>
            <div>
              <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.parent') }}</label>
              <SelectInput :model-value="''">
                <option value="">{{ t('stationPages.editor.noParent') }}</option>
              </SelectInput>
            </div>
            <div>
              <label class="text-sm text-[var(--text-muted)] mb-1 block">{{ t('stationPages.editor.metaDescription') }}</label>
              <TextInput :model-value="t('helpCenter.pages.dummyMetaDesc')" :placeholder="t('stationPages.editor.metaDescriptionPlaceholder')"/>
            </div>
          </div>
        </NeutralContainer>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.layoutTitle')">
        <p>{{ t('helpCenter.pages.layoutText') }}</p>
        <p>{{ t('helpCenter.pages.layoutText2') }}</p>
      </HelpSection>

      <!-- Dummy: Row with cells -->
      <HelpSection :title="t('helpCenter.pages.layoutDummyTitle')">
        <NeutralContainer class="space-y-2">
          <NeutralContainer class="flex gap-2">
            <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
              {{ t('stationPages.contentType.markdown') }}
            </div>
            <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
              {{ t('stationPages.contentType.image') }}
            </div>
          </NeutralContainer>
          <div class="flex items-center justify-center gap-2 py-1">
            <div class="flex-1 h-px bg-[var(--border)]"/>
            <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addRowHere')"/>
            <div class="flex-1 h-px bg-[var(--border)]"/>
          </div>
          <NeutralContainer class="border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
            {{ t('stationPages.contentType.video') }}
          </NeutralContainer>
        </NeutralContainer>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.contentTypesTitle')">
        <p>{{ t('helpCenter.pages.contentTypesText') }}</p>
        <BulletList>
          <li><strong>{{ t('stationPages.contentType.markdown') }}</strong>: {{ t('helpCenter.pages.contentTypeMarkdown') }}</li>
          <li><strong>{{ t('stationPages.contentType.image') }}</strong>: {{ t('helpCenter.pages.contentTypeImage') }}</li>
          <li><strong>{{ t('stationPages.contentType.video') }}</strong>: {{ t('helpCenter.pages.contentTypeVideo') }}</li>
        </BulletList>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.previewTitle')">
        <p>{{ t('helpCenter.pages.previewText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.pages.saveTitle')">
        <p>{{ t('helpCenter.pages.saveText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpPermissionGuard :permissions="[StationPermission.PAGE_MANAGER]" :label="t('helpCenter.permissionLabel.pageManager')">
      <HelpSection :title="t('helpCenter.pages.managerTitle')">
        <p>{{ t('helpCenter.pages.managerText') }}</p>
        <BulletList>
          <li>{{ t('helpCenter.pages.managerPublish') }}</li>
          <li>{{ t('helpCenter.pages.managerLanding') }}</li>
          <li>{{ t('helpCenter.pages.managerDelete') }}</li>
        </BulletList>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.pages.tip') }}</HelpTip>
  </HelpArticle>
</template>
