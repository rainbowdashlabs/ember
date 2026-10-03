/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditorToolbar from '@/components/input/markdowneditor/EditorToolbar.vue'

const {t} = useI18n()

const demoContent = ref(`# Willkommen zum Texteditor

Das ist ein **fetter Text**, das ist *kursiv* und das ist ~~durchgestrichen~~.

Du kannst auch <u>unterstrichenen</u> und ==markierten== Text verwenden.

<div data-align="center">

Dieser Absatz steht zentriert.

</div>

## Listen

Hier eine Aufzählung:

- Erster Punkt
- Zweiter Punkt
- Dritter Punkt

Und eine nummerierte Liste:

1. Schritt eins
2. Schritt zwei
3. Schritt drei

## Zitate und Code

> Das ist ein Zitat. Hier kann man wichtige Informationen hervorheben.

Inline-Code sieht so aus: \`const x = 42\`

\`\`\`
function hallo() {
  return "Hallo Welt!"
}
\`\`\`

## Tabellen

| Spalte A | Spalte B | Spalte C |
|----------|----------|----------|
| Zeile 1  | Wert 1   | Daten 1  |
| Zeile 2  | Wert 2   | Daten 2  |
| Zeile 3  | Wert 3   | Daten 3  |

## Links

Hier ist ein [Link zur Startseite](/).

---

Das war eine horizontale Trennlinie. Damit kannst du Abschnitte visuell trennen.

## Überschriftenebenen

### Das ist eine H3-Überschrift

Normaler Absatztext darunter.`)
</script>

<template>
  <HelpArticle :title="t('helpCenter.kb.editor.title')" :subtitle="t('helpCenter.kb.editor.subtitle')">
    <HelpSection :title="t('helpCenter.kb.editor.whatIs')">
      <p>{{ t('helpCenter.kb.editor.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.blocksTitle')">
      <p>{{ t('helpCenter.kb.editor.blocksText') }}</p>
      <p>{{ t('helpCenter.kb.editor.blocksKeepsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('stationPages.editor.chooseNewsTeaser')">
      <p>{{ t('helpCenter.pages.contentTypeNewsTeaser') }}</p>
    </HelpSection>

    <HelpSection :title="t('stationPages.editor.chooseFeaturedEvent')">
      <p>{{ t('helpCenter.pages.contentTypeFeaturedEvent') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.toolbarTitle')">
      <p>{{ t('helpCenter.kb.editor.toolbarText') }}</p>
      <NeutralContainer class="mt-3">
        <EditorToolbar :editor="undefined"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.formattingTitle')">
      <p>{{ t('helpCenter.kb.editor.formattingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.headingsTitle')">
      <p>{{ t('helpCenter.kb.editor.headingsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.alignTitle')">
      <p>{{ t('helpCenter.kb.editor.alignText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.listsTitle')">
      <p>{{ t('helpCenter.kb.editor.listsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.linksTitle')">
      <p>{{ t('helpCenter.kb.editor.linksText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.tableTitle')">
      <p>{{ t('helpCenter.kb.editor.tableText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.bubbleTitle')">
      <p>{{ t('helpCenter.kb.editor.bubbleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.rawTitle')">
      <p>{{ t('helpCenter.kb.editor.rawText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.demoTitle')">
      <p class="mb-3 text-sm text-[var(--text-muted)]">{{ t('helpCenter.sample.knowledge.tryEditor') }}</p>
      <MarkdownEditor v-model="demoContent" placeholder="Schreibe etwas..." />
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kb.editor.tip') }}</HelpTip>
  </HelpArticle>
</template>
