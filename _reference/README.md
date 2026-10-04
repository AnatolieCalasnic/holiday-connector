# Holiday connector

Public holidays from [Nager.Date](https://date.nager.at) into the Innolics / SaZ platform.
Built to the same convention as the Challenge Tool connector (ADR-001). Plan and design:
[`docs/PLAN.md`](docs/PLAN.md).

```
Nager.Date ──► holidayadapter ──► sazdatastream ──► RabbitMQ ──► holidayprocessor ──► PostgreSQL
```

| Service | Port | What it does |
|---|---|---|
| `holidayadapter` | 8095 | Daily: pulls this year + next per country, pushes only what changed, marks holidays that disappeared as removed |
| `holidayprocessor` | 8096 | Own queue `nagerdate.processor` + DLQ, validates, upserts into its own database |
| `holiday-db` | 5440 | PostgreSQL 16, table `holiday` |

## Run it locally

1. Bring up the SaZ stack as in the group's `local-dev-setup.md` (`sazinfrastrucure`, branch
   `feature/challengetool-connector`, folder `challengetool/docs/`): infrastructure, the two
   ZooKeeper nodes, then `sazdata`.
2. Processor first — it declares and binds the queue. An adapter that pushes earlier is dropped
   silently by RabbitMQ.
   ```powershell
   docker compose -p holidays up -d --build holiday-db holidayprocessor
   docker logs -f holidayprocessor
   ```
3. Then the adapter. It syncs once at startup, then daily at 06:00.
   ```powershell
   docker compose -p holidays up -d --build holidayadapter
   docker logs -f holidayadapter       # expect: Sync done: <n> sent, 0 removed, 0 unchanged, 0 failed
   ```
4. Look at the data:
   ```powershell
   docker exec -it holiday_db psql -U holidays -d holidays -c "select date, local_name, types, version, removed_at from holiday order by date;"
   ```

### From the IDE instead of Docker

Run the processor with defaults (it expects RabbitMQ on `localhost:5672` and the database on
`localhost:5440`). Run the adapter with:

```powershell
$env:DATA_API_OVERRIDE = "http://localhost:9100/data/push"   # registration hands back a container hostname
.\gradlew.bat bootRun
```

## Tests

```powershell
cd holidayadapter;   .\gradlew.bat test    # 13 tests, incl. contract test against a real Nager.Date response
cd holidayprocessor; .\gradlew.bat test    # 11 tests: double/plain JSON, validation, change detection
```

Not covered yet: the repository and the listener against a real Postgres and RabbitMQ
(milestone 4, Testcontainers).

## Configuration

Everything is an environment variable; nothing is hard-coded.

| Variable | Default | Service |
|---|---|---|
| `MANAGEMENT_URL` | `http://localhost:8092` | adapter |
| `NAGER_COUNTRIES` | `NL` (comma-separated ISO codes) | adapter |
| `NAGER_YEARS_AHEAD` | `1` | adapter |
| `POLL_CRON` | `0 0 6 * * *` | adapter |
| `PROVIDER_ID` / `TYPE_ID` | `104` / `904` — **unregistered**, check before sharing a stack | both |
| `DATA_API_OVERRIDE` | empty | adapter, IDE runs only |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local `holiday-db` | processor |
| `RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | processor |
