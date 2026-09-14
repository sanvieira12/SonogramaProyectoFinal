# Sonograma — Lightsail Access Discovery

## Scope and safety

This was an access-discovery and production read-only task. No deployment, migration, container restart, service restart, database write, schema change, backup creation, or data repair was executed.

Sensitive values were not printed. The private key contents were not read.

## Local evidence searched

The following sources were inspected safely:

- `deploy/README-LIGHTSAIL.md`
- `deploy/deploy.sh`
- `deploy/backup-db.sh`
- `deploy/setup-server.sh`
- `docker-compose.prod.yml`
- `docs/GOOGLE_AUTH.md`
- `docs/testing/DISCOGS_JOB_22_CLEANUP_MANIFEST.md`
- `docs/REGISTRO_ENTREGA_FINAL.md`
- Git remotes
- `~/.ssh/config`
- `~/.ssh/known_hosts` host labels
- SSH key filenames under `~/.ssh/`
- shell startup files for aliases/functions and Sonograma access references
- `.vscode/` task/launch/settings files
- production-related environment variable names only

## Recovered connection method

The project’s prior production snapshot documents this exact SSH workflow:

```bash
ssh -i ~/.ssh/LightsailDefaultKey-us-east-1.pem ubuntu@tiendasonograma.com
```

Evidence was found in `docs/testing/DISCOGS_JOB_22_CLEANUP_MANIFEST.md` and `deploy/README-LIGHTSAIL.md`.

Local evidence confirms:

- Key filename exists: `~/.ssh/LightsailDefaultKey-us-east-1.pem`.
- `tiendasonograma.com` is present in `~/.ssh/known_hosts`.
- Known-host labels also include `100.29.117.160` and `32.197.194.84`.
- No `~/.ssh/config` file was present.
- No relevant shell alias or function was found.
- Git origin is `https://github.com/sanvieira12/SonogramaProyectoFinal.git`.

## Connectivity verification

The recovered command was tested non-interactively with strict host-key checking and a connection timeout. The only remote command was:

```bash
ssh -i ~/.ssh/LightsailDefaultKey-us-east-1.pem \
  -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=yes \
  ubuntu@tiendasonograma.com 'hostname && whoami && pwd'
```

Read-only result:

```text
hostname: ip-172-26-10-67
user: ubuntu
pwd: /home/ubuntu
```

## Recovered production environment

The remote checkout and expected production files were found:

- Application: `/opt/sonograma/app`
- Environment file: `/etc/sonograma/sonograma.env` (read without printing values)
- Backup directory: `/opt/sonograma/backups`
- Log directory: `/opt/sonograma/logs`
- Production commit: `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5`
- Branch: `agent/fix-catalog-permanent-deletion`
- PostgreSQL container: `sonograma-postgres`
- Backend container: `sonograma-backend`
- Nginx container: `sonograma-nginx`

The production backend and PostgreSQL containers reported healthy status. Separate `sonograma-vinylfuture-test-*` containers were also running; they were not treated as the production stack.

The full production read-only results are in [SONOGRAMA_PRODUCTION_PREFLIGHT_RESULT.md](SONOGRAMA_PRODUCTION_PREFLIGHT_RESULT.md).

## Final status

EXISTING LIGHTSAIL ACCESS RECOVERED
