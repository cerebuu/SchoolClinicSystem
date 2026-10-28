# MySQL connection implementation

Verified against the existing `school_clinic` database on MySQL **9.7.1**.
The server was initially at schema version **5** and is now at **12**. No base
tables were recreated, renamed or reseeded. No existing medicine names or
password hashes were rewritten. `ClinicSystem` signatures are unchanged.

## Connection and dependency

Normal startup reads `SCMS_DB_URL`, `SCMS_DB_USER`, `SCMS_DB_PASSWORD`.
An unset/blank URL preserves encrypted embedded H2, including its key and lock
handling. Explicit remote failures stop startup rather than opening a different
database. Explicit Path constructors and `--demo` remain isolated from remote
environment settings. Credentials and connection URLs are not shown in storage
labels or committed to configuration.

Installed `lib/mysql-connector-j-8.4.0.jar` (the existing Git rule ignores generated
JARs; both dependency scripts reproduce it):

- Download: https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
- Published `.jar.sha1`, checked against the initial download:
  `b1bc0f47bcad26ad5f9bceefb63fcb920d868fca`
- Computed and pinned SHA-256:
  `d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5`
- macOS/Linux: `shasum -a 256` or `sha256sum`; Windows: `Get-FileHash -Algorithm SHA256`.
  Both scripts verify downloads before installation and verify existing files.

Connector/J worked with the actual server, so no MariaDB driver was needed.
`jdbc:mariadb://` is normalized to the driver's `jdbc:mysql://` protocol.
MariaDB itself was not available for live testing. See the check-constraint
limitation below. Driver documentation: [MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/).

## SQL differences

| Area | Handling |
| --- | --- |
| Schema bootstrap | MySQL validates all existing v5 base tables/columns and schema_version before DDL. Missing tables and unsupported versions fail with actionable messages. H2's bootstrap DDL never runs on MySQL. |
| Additive migration | Separate MySQL v5-to-v12 steps add current notification, campus, allocation, inventory movement and ATC schema. Each step advances schema_version only after success; metadata checks make interrupted additions retryable. No application-table or constraint DROP statements are used. |
| Identity | Existing MySQL AUTO_INCREMENT columns remain intact; the new inventory_movements sequence uses BIGINT AUTO_INCREMENT. H2 retains GENERATED ALWAYS AS IDENTITY. Business IDs remain Java-generated strings and batch ordinals retain their existing handling. JDBC generated-key retrieval was tested on both engines. |
| Text | Existing MySQL TEXT and H2 CLOB values use getString and bound strings. Removed H2-only VARCHAR casts from the medication-history UNION. No text columns or clinical content were rewritten. |
| Time | New MySQL date-time columns use DATETIME(6); LocalDateTime parameters truncate sub-microsecond digits before binding. JDBC 4.2 typed reads round-trip six digits without a timezone conversion. H2 retains TIMESTAMP(9) and its full nanoseconds. |
| Columns/indexes | Catalog-scoped information_schema checks precede plain ADD COLUMN and CREATE INDEX on MySQL. H2 retains its existing IF NOT EXISTS DDL. |
| Nullable transaction | MySQL MODIFY COLUMN replaces H2 ALTER COLUMN DROP NOT NULL. The ID width, charset and collation come from the parent key rather than assuming H2's VARCHAR(500). |
| Foreign keys | New allocation/actor foreign-key columns preserve the existing parent's width, charset and collation (the supplied database uses VARCHAR(100) IDs). |
| Notification checks | On MySQL, add the expanded PENDING/SENT/FAILED check, retain the obsolete v5 check as NOT ENFORCED, and add the current message-type check. No check is dropped. MariaDB with a restrictive v5 check fails preflight before DDL: a DBA must widen that check first because disabling an individual check is unavailable there. |
| Concatenation | CONCAT replaces H2's `||` in stock, medication-history and audit searches. |
| Numeric aggregation | SUM(quantity) replaces CAST(quantity AS BIGINT), which MySQL does not accept. Long reads and checked integer conversion remain in place. |
| String casts | Audit date searches use CHAR on MySQL and VARCHAR on H2. UNION NULL values are inferred from the opposite branch. |
| Paging | Audit paging now uses bound LIMIT/OFFSET, matching the other paged repositories and working on both engines. |
| Boolean/DATE | JDBC bound booleans, getBoolean, TRUE/FALSE, typed LocalDate and CAST(timestamp AS DATE) work on both; round-trip tests cover boolean and DATE values. |

Remote application startup does not insert default campus rows. The existing
approved UIC choices remain available in memory; explicit administrator writes
still persist normally. H2 retains its existing campus initialization.

The supplied accounts use `1000:salt:hash` with hex hashes. The source hasher
previously accepted only its two-field Base64 format. Added PBKDF2-SHA256
verification for the legacy iteration-count format (literal or hex salt bytes),
retaining existing two-field verification and unchanged new-password hashing.
Malformed hashes fail authentication. No account is rehashed or reset.

## Verification and remaining manual work

`sh tools/verify.sh` compiled all `src`, `tests`, and `demo` with Java 17 and
finished with exit code **0**. The requested whole-src compile to `out` also
finished with exit code **0**. Shell syntax and `git diff --check` passed.
The Windows dependency/launch instructions were inspected, not executed on macOS.

Selected real output (full output in [H2 checks](../verification/mysql-h2-checks.txt)):

```text
PASS: 12 password-format checks (current Base64, legacy literal/hex salts, wrong passwords, malformed input). No rehash.
PASS: H2 generated keys, Unicode text, LocalDateTime, DATE, BOOLEAN, CONCAT, casts, LIMIT/OFFSET.
PASS: H2 fallback/configuration and JDBC value checks.
PASS: 49 persistence, restart, foreign-key, transaction rollback, and authorization checks.
PASS: 389 SQL audit page, filter, ordering, and summary checks.
```

Real live output ([complete log](../verification/mysql-live-checks.txt)):

```text
Server: MySQL 9.7.1
PASS: schema 12, patients=70, batches=7, recent visits=4 (unscoped repository).
PASS: MySQL generated keys, Unicode text, LocalDateTime, DATE, BOOLEAN, CONCAT, casts, LIMIT/OFFSET.
PASS: unsupported-version/incomplete-schema guards and retryable column/index metadata checks.
PASS: repository searches/paging, legacy medicine names, account loading; existing base rows/hashes unchanged.
MANUAL CHECK BLOCKED: N001 has no campus; legacy visits have no campus. Administrator assignment and approved visit backfill are required for nurse-scoped UI access.
PASS: production application startup selects MySQL without seeding existing tables.
```

The live harness opens storage twice, checks original-column fingerprints over
reopen and production startup, and tests values/invalid schemas with session-only
temporary tables. Those temporary fixtures are removed; application tables are
never dropped. The initial migration run completed but its harness then failed
on a campus-required notification query; the corrected harness subsequently
passed. Fingerprint assertions cover the successful reopen/startup checks;
pre/post-migration counts also remained 70/7/4.

An empty root password was rejected. After explicit authorization, the existing
MySQL client's saved credential was used only in process memory for the checks;
it was not displayed or stored. Production reads only the requested environment
variables, so the actual password must be supplied there.

The **N001 native UI login/count check remains unverified**. The v5 import has no
campus assignments. Assign N001 through the existing administrator editor and
obtain the data owner's campus mapping before backfilling legacy clinical rows.
No campus mapping, access-rule bypass, password reset, or data rewrite was made.
The README contains the macOS/Windows run commands and the manual 70/7/4 checklist.

## Files added or changed for this task

```text
README.md
lib/README.md
lib/mysql-connector-j-8.4.0.jar (installed, Git-ignored generated dependency)
src/clinic/app/ApplicationContext.java
src/clinic/core/PasswordHasher.java
src/clinic/persistence/Database.java
src/clinic/persistence/Schema.java
src/clinic/persistence/SchemaException.java (new)
src/clinic/persistence/MySqlSchema.java (new)
src/clinic/persistence/MySqlMetadata.java (new)
src/clinic/persistence/MySqlNotificationSchema.java (new)
src/clinic/persistence/Sql.java
src/clinic/persistence/AdminRepository.java
src/clinic/persistence/ClinicRepository.java
src/clinic/persistence/DispenseRepository.java
src/clinic/service/AdminService.java
tests/PasswordFormatChecks.java (new)
tests/JdbcValueChecks.java (new)
tests/MySqlChecks.java (new)
tools/dependencies.cmd
tools/dependencies.sh (extended existing script)
tools/verify.cmd
tools/verify.sh
docs/MYSQL_IMPLEMENTATION.md (new)
verification/mysql-h2-checks.txt (new)
verification/mysql-live-checks.txt (new)
```

Pre-existing changes to other files/screenshots were preserved.
