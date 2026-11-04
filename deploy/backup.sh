#!/bin/sh
# Daily database backup (NFR-13, docs/DEPLOYMENT.md). Run by cron on the server, from this folder:
#   15 21 * * * cd /opt/schoollink/deploy && ./backup.sh >> backups/backup.log 2>&1
# Keeps 14 days of backups in deploy/backups; copy them off the server too (see the guide).
set -eu
cd "$(dirname "$0")"
mkdir -p backups
file="backups/schoollink-$(date +%Y-%m-%d).dump"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$file.tmp"
mv "$file.tmp" "$file"
find backups -name 'schoollink-*.dump' -mtime +14 -delete
echo "$(date '+%F %T') backup written: $file ($(du -h "$file" | cut -f1))"
