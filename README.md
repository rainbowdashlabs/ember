# Ember

Ember manages youth fire brigades (Jugendfeuerwehren): members, attendance, appointments, inventory,
forms, tests, news, a wiki and federation between stations. You host it yourself.

- Website: https://ember-panel.de
- Demo: https://demo.ember-panel.de
- Help center: https://ember-panel.de/helpcenter/station/basics

## Install

You need Docker with the Compose plugin on a 64-bit machine (x86-64 or ARM64). Run this in the
directory you want to install Ember in:

```bash
curl -fsSL https://ember-panel.de/install.sh | bash
```

The installer writes a `compose.yaml`, starts Ember and prints the admin login. It can also add a
cron job for hourly updates.

You can also fill in the answers at https://ember-panel.de/install and run the installer with the
code you get there:

```bash
curl -fsSL https://ember-panel.de/install.sh | bash -s ABC123
```

To set it up by hand, use [`docker/compose.prod.yaml`](docker/compose.prod.yaml) and the
[hosting guide](https://ember-panel.de/helpcenter/station/basics/hosting).

## Documentation

- [Hosting, updates and backups](https://ember-panel.de/helpcenter/station/basics/hosting)
- [Configuration](https://ember-panel.de/helpcenter/station/basics/hosting/configuration)
- [Mailing](https://ember-panel.de/helpcenter/admin/settings/mailing)
- [Legal documents](https://ember-panel.de/helpcenter/admin/settings/legal)
- [Permissions](https://ember-panel.de/helpcenter/station/basics/permissions)
- [Federation](https://ember-panel.de/helpcenter/station/basics/federation)
- [Beacon](https://ember-panel.de/helpcenter/admin/beacon)

## Development

You need JDK 25, Node.js 24 and Docker. All build and test commands are in `toolchain.sh`:

```bash
./toolchain.sh help         # list commands
./toolchain.sh docker-app   # start backend, frontend and database with demo accounts
./toolchain.sh be-verify    # test the backend
./toolchain.sh fe-build     # test and build the frontend
```

## License

AGPL-3.0-only, see [LICENSE](LICENSE).
