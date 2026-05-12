/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Database;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ocular.key.Key;

import java.nio.file.Path;

public class File {

    private Database database = new Database();
    private Api api = new Api();
    private Mailing mailing = new Mailing();
    private Auth auth = new Auth();
    private Demo demo = new Demo();

    public Database database() {
        return database;
    }

    public Api api() {
        return api;
    }

    public Mailing mailing() {
        return mailing;
    }

    public Auth auth() {
        return auth;
    }

    public Demo demo() {
        return demo;
    }
}
