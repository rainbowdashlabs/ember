/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import de.chojo.sadu.core.configuration.DatabaseConfig;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@OverwritePrefix("DB")
public class Database implements DatabaseConfig {
    @Overwrite(env = @Env)
    private String host = "localhost";

    @Overwrite(env = @Env)
    private String port = "5432";

    @Overwrite(env = @Env)
    private String user = "ember";

    @Overwrite(env = @Env)
    private String password = "ember";

    @Overwrite(env = @Env)
    private String database = "ember";

    @Overwrite(env = @Env)
    private String schema = "ember";

    @Overwrite(env = @Env)
    private int poolSize = 5;

    @Override
    public String host() {
        return host;
    }

    @Override
    public String port() {
        return port;
    }

    @Override
    public String user() {
        return user;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String database() {
        return database;
    }

    public String schema() {
        return schema;
    }

    public int poolSize() {
        return poolSize;
    }

    @Override
    public String toString() {
        return "Database{" + "host='"
                + host + '\'' + ", port='"
                + port + '\'' + ", user='"
                + user + '\'' + ", password='"
                + "****" + '\'' + ", database='"
                + database + '\'' + ", schema='"
                + schema + '\'' + ", poolSize="
                + poolSize + '}';
    }
}
