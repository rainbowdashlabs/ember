#!/bin/sh
# atmoz/sftp runs every executable in /etc/sftp.d/ before starting sshd. Use that hook
# to chown the writable upload directory to the ember user on every start, in case a
# persisted named volume was created root-owned by Docker and the image's own first-run
# chown was skipped.
chown ember:users /home/ember/upload
