# Holiday-Connector – how to test it yourself


Which code: the complete version is in HolidayConnector\_reference. Your own projects
(HolidayConnector\holidayadapter and holidayprocessor) are at step 0, so they only start and
report health. The steps below test the _reference version. Write what you see in the step log
of the experiment document.

Before you start
- Docker Desktop running, Java 21 installed.
- The local SaZ stack is up: sazinfrastrucure, branch feature/challengetool-connector, follow
  challengetool/docs/local-dev-setup.md (infrastructure, the two ZooKeeper nodes, then sazdata).
- Open PowerShell in C:\Users\calas\Projects\HolidayConnector\_reference

1. Unit tests (no Docker needed)
   cd holidayadapter;   .\gradlew.bat test      expect: 13 tests passed
   cd ..\holidayprocessor; .\gradlew.bat test   expect: 11 tests passed
   Report: build\reports\tests\test\index.html in each project. Take a screenshot as evidence.

2. Start the processor first (it creates and binds the queue)
   cd ..
   docker compose -p holidays up -d --build holiday-db holidayprocessor
   docker logs -f holidayprocessor
   expect: the service starts without errors and connects to RabbitMQ.
   Check in the RabbitMQ UI (http://localhost:15672, guest / guest) that the queues
   nagerdate.processor and nagerdate.processor.dlq exist.

3. Start the adapter
   docker compose -p holidays up -d --build holidayadapter
   docker logs -f holidayadapter
   expect: registration returns a dataApi, then a line like
   "Sync done: <n> sent, 0 removed, 0 unchanged, 0 failed".
   If dataApi is null: restart sazmanagement (known platform defect, ADR-001 finding 3).

4. Check the data in the database
   docker exec -it holiday_db psql -U holidays -d holidays -c "select date, local_name, types, version, removed_at from holiday order by date;"
   expect: one row per Dutch holiday for this year and next year.

5. Change detection (only changes are sent)
   Let a second sync run without restarting the adapter (for a quick check, set
   POLL_CRON: "0 */5 * * * *" in compose.yaml and run step 3 again, then wait 5 minutes).
   expect: "0 sent, <n> unchanged". Note: a restart resends everything (memory is reset);
   the processor should then skip the rows as unchanged.

6. Failure test (dead-letter queue)
   In the RabbitMQ UI: Exchanges → saz-data-exchange → Publish message,
   routing key raw.nagerdate.904, payload: this is not json
   expect: the message ends up in nagerdate.processor.dlq with the reason, and the processor
   keeps running. Run step 3/5 again to check that normal messages still get through.

7. Stop everything
   docker compose -p holidays down
   (never add --remove-orphans: it removes containers from the SaZ stack too)
