/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import helpCenter from '@/i18n/de-DE.helpcenter'
import {ENVIRONMENT_GROUPS, SETTINGS, groupOf, rowsOf} from './environmentVariables'

const messages = {helpCenter}

function translated(key: string): unknown {
    return key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], messages)
}

function leafKeys(node: unknown, path: string): string[] {
    if (typeof node === 'string') return [path]
    return Object.entries(node as Record<string, unknown>).flatMap(([key, child]) => leafKeys(child, path ? `${path}.${key}` : key))
}

/**
 * The list of environment variables is rendered from the backend's configuration, so a new setting shows up on
 * its own; what it cannot bring along is a German description, which is what these tests hold it to.
 */
describe('environment variables', () => {
    it('describes every setting of the configuration file', () => {
        const undescribed = SETTINGS.filter(setting => typeof translated(`helpCenter.basics.configuration.settings.${setting.configKey}`) !== 'string')

        expect(undescribed.map(setting => setting.configKey)).toEqual([])
    })

    it('describes no setting the configuration file does not have', () => {
        const known = new Set(SETTINGS.map(setting => setting.configKey))
        const described = leafKeys(translated('helpCenter.basics.configuration.settings'), '')

        expect(described.filter(key => !known.has(key))).toEqual([])
    })

    it('describes every external variable and nothing else', () => {
        const listed = ENVIRONMENT_GROUPS.flatMap(group => group.variables ?? []).map(variable => variable.name)
        const described = leafKeys(translated('helpCenter.basics.configuration.variables'), '')

        expect(described.sort()).toEqual([...listed].sort())
    })

    it('puts every setting into a group', () => {
        const ungrouped = SETTINGS.filter(setting => groupOf(setting.configKey) === undefined)

        expect(ungrouped.map(setting => setting.configKey)).toEqual([])
    })

    it('titles every group and shows every row once', () => {
        for (const group of ENVIRONMENT_GROUPS) {
            expect(typeof translated(group.title)).toBe('string')
            if (group.note) expect(typeof translated(group.note)).toBe('string')
        }
        const settingRows = ENVIRONMENT_GROUPS.flatMap(rowsOf).filter(row => row.configKey !== undefined)

        expect(settingRows).toHaveLength(SETTINGS.length)
        expect(ENVIRONMENT_GROUPS.filter(group => rowsOf(group).length === 0).map(group => group.title)).toEqual([])
    })

    it('takes a key into the group naming its longest prefix', () => {
        expect(groupOf('storage.compressPdfs')?.title).toBe('helpCenter.basics.configuration.envGroupStorageCompress')
        expect(groupOf('storage.defaultTotal')?.title).toBe('helpCenter.basics.configuration.envGroupStorage')
        expect(groupOf('auth.hibp.enabled')?.title).toBe('helpCenter.basics.configuration.envGroupHibp')
        expect(groupOf('mailing.smtp.host')?.title).toBe('helpCenter.basics.configuration.envGroupMailSmtp')
        expect(groupOf('mailing.senderName')?.title).toBe('helpCenter.basics.configuration.envGroupMailing')
    })
})
