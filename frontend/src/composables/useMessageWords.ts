/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import type {MessagePersonResponse, SystemKind} from '@/api/generated/schema'
import {conversationTitle, personName, type MessagePersonName} from '@/util/conversationTitle'

/** What a system line is made of, whether it comes as a whole message or as a list preview. */
export interface SystemLineParts {
    systemKind?: SystemKind | null
    author?: MessagePersonResponse | null
    subject?: MessagePersonResponse | null
    text: string
}

/**
 * The words messages are shown with: names, the names of deleted accounts, what a system line says
 * and what a conversation is called.
 */
export function useMessageWords() {
    const {t} = useI18n()

    /** A person's register name, or the word for a deleted account. */
    function nameOf(person: MessagePersonName | null | undefined): string {
        return person ? personName(person) : t('messages.deletedAccount')
    }

    /** What a system line says, in a sentence. */
    function systemLine(line: SystemLineParts): string {
        const named = {author: nameOf(line.author), subject: nameOf(line.subject), name: line.text}
        if (line.systemKind === 'RENAMED' && !line.text) return t('messages.system.RENAMED_EMPTY', named)
        return t(`messages.system.${line.systemKind ?? 'LEFT'}`, named)
    }

    /** What a conversation is called, with a word for one nobody else is left in. */
    function titleOf(conversation: {kind: string, name?: string | null, others: readonly MessagePersonName[]}): string {
        return conversationTitle(conversation, t('messages.nobodyLeft'))
    }

    return {nameOf, systemLine, titleOf}
}
