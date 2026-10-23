# Embedded database dependency

H2 2.5.252, from Maven Central: `com.h2database:h2:2.5.252`.

SHA-256: `90b11dc413da82070ef15fc943282c1408e6ffd34fa68998335fea8603b5ff20`.

`tools/dependencies.cmd` downloads the pinned driver if missing and verifies its checksum on every launch/check. No database server or Maven installation is required. The JAR is a generated dependency and is ignored by Git. The first download needs internet access; subsequent runs work offline.

H2 is dual licensed under MPL 2.0 and EPL 1.0; see the accompanying extracted license and [official documentation](https://h2database.github.io/html/license.html). The application uses JDBC embedded mode, prepared statements, encrypted database files, and transactions. It does not start the H2 web console or a network server.
