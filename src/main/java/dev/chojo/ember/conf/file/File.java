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
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.Theming;

/**
 * Root configuration file containing all application settings.
 * Deserialized from {@code config/config.yaml}.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class File {

    private Database database = new Database();
    private Api api = new Api();
    private Mailing mailing = new Mailing();
    private Auth auth = new Auth();
    private Demo demo = new Demo();
    private Theming theming = new Theming();
    private Storage storage = new Storage();

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

    public Theming theming() {
        return theming;
    }

    public Storage storage() {
        return storage;
    }

    @Override
    public String toString() {
        return "File{" + "database="
                + database + ", api="
                + api + ", mailing="
                + mailing + ", auth="
                + auth + ", demo="
                + demo + ", theming="
                + theming + ", storage="
                + storage + '}';
    }
}
