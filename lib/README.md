# Database and mail dependencies

H2 2.5.252, from Maven Central: `com.h2database:h2:2.5.252`.

SHA-256: `90b11dc413da82070ef15fc943282c1408e6ffd34fa68998335fea8603b5ff20`.

`tools/dependencies.cmd` downloads the pinned driver if missing and verifies its checksum on every launch/check. No database server or Maven installation is required. The JAR is a generated dependency and is ignored by Git. The first download needs internet access; subsequent runs work offline.

MySQL Connector/J **8.4.0**, pinned Maven artifact `com.mysql:mysql-connector-j:8.4.0`:

- URL: https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
- SHA-256: `d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5`
- Initial download was compared with Maven Central's published `.jar.sha1`
  (`b1bc0f47bcad26ad5f9bceefb63fcb920d868fca`), then SHA-256 was computed locally.
  Both dependency scripts pin and verify SHA-256 before installation and on every run.
  This verifies integrity against the pinned artifact, not a separate publisher signature.
- License: GPL v2 with the Universal FOSS Exception; license files are inside the JAR.

`sh tools/dependencies.sh` provides the same pinned downloads and SHA-256 checks
on macOS/Linux, using `sha256sum` or `shasum -a 256`.

The approved Jakarta Mail implementation uses Angus Mail 2.0.3, Angus Core
2.0.3, Angus Activation 2.0.2, Jakarta Mail API 2.1.3, and Jakarta Activation
API 2.1.3. The dependency script downloads and verifies these artifacts for
SMTP email. SMS uses the JDK HTTP client and adds no library.

H2 is dual licensed under MPL 2.0 and EPL 1.0; see the accompanying extracted license and [official documentation](https://h2database.github.io/html/license.html). The application uses JDBC embedded mode, prepared statements, encrypted database files, and transactions. It does not start the H2 web console or a network server. Angus Mail is licensed under EPL 2.0 or GPL 2.0 with the Classpath Exception.
