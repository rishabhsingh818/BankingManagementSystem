# Banking Management System — Deep Interview Analysis

> **Strictly code-based analysis. No assumptions. No generic answers.**
> **Last updated to reflect current source code state.**

---

## 1. PROJECT UNDERSTANDING

### What the Project Does

This is a **console-based Banking Management System** built in Java. It allows users to:
- Register and log in with email/password
- Open a bank account (one per user, enforced by email uniqueness)
- Credit money into their account
- Debit money from their account
- Transfer money to another account
- Check account balance

All operations are backed by a **MySQL database** via **JDBC**.

### Main Modules / Classes & Responsibilities

| Class | File | Responsibility |
|---|---|---|
| `BankingApp` | BankingApp.java | Entry point. Manages top-level menu loop, creates all objects, holds DB credentials |
| `User` | User.java | User registration, login, existence check |
| `Accounts` | Accounts.java | Account creation, account number generation, account lookup |
| `AccountManager` | AccountManager.java | All financial operations: credit, debit, transfer, balance check |

### Architecture

**Monolithic, single-tier console application.**

- No layered architecture (no Service layer, no DAO layer, no Repository pattern)
- Business logic, DB calls, and UI output are all mixed within the same methods
- Single `Connection` object shared across all classes — passed via constructor injection
- Single `Scanner` object shared across all classes — passed via constructor injection
- No separation of concerns: e.g., `credit_money()` in `AccountManager` handles input reading, DB querying, and printing output simultaneously

**Why "Layered Architecture" is the wrong label for this project:**
A layered architecture requires strict layer boundaries — Presentation never touches DB, Business logic never does I/O. In this project, every `AccountManager` method reads from `Scanner`, checks business rules, and executes SQL — all in one method. That is monolithic design, not layered. The classes are domain-separated (SRP), which is different.

---

## 2. TECH STACK USAGE (CODE-BASED)

### Java
- Java SE (no frameworks)
- Uses `Scanner` for console I/O
- Uses `BigDecimal` for monetary amounts — **correct for financial precision**
- Uses `long` for account numbers
- Package: `BankingManagementSystem`

### JDBC
- Driver loaded explicitly: `Class.forName("com.mysql.cj.jdbc.Driver")` in `BankingApp.java:13`
- Connection obtained via: `DriverManager.getConnection(url, username, password)` in `BankingApp.java:18`
- `PreparedStatement` used for all parameterized queries — **correct**
- `Statement` used once in `generateAccountNumber()` — unparameterized, but no user input so no injection risk
- Manual transaction management: `setAutoCommit(false)`, `commit()`, `rollback()`, reset via `finally` block
- Library: `mysql-connector-j` (MySQL JDBC Connector/J)

### MySQL
- Database name: `banking_system` (from URL `jdbc:mysql://127.0.0.1:3306/banking_system`)
- Host: `127.0.0.1:3306` (localhost only)
- Tables: `User`, `Accounts`

### jBCrypt
- **IMPLEMENTED.** `import org.mindrot.jbcrypt.BCrypt` present in `User.java`, `Accounts.java`, and `AccountManager.java`.
- `User.java:register()` hashes password: `BCrypt.hashpw(password, BCrypt.gensalt())` — hash stored in DB
- `User.java:login()` fetches hash by email, verifies with `BCrypt.checkpw(password, hashed_password)`
- `Accounts.java:open_account()` hashes PIN: `BCrypt.hashpw(security_pin, BCrypt.gensalt())` — hash stored in DB
- All `AccountManager` methods verify PIN with `BCrypt.checkpw(security_pin, stored_pin)` — PIN is never compared via SQL

---

## 3. OOP ANALYSIS

### Encapsulation
- **Present and consistent.**
- `User.java`: `connection` and `scanner` are `private` — correct
- `Accounts.java`: `connection` and `scanner` are `private` — correct
- `AccountManager.java`: `connection` and `scanner` are `private` — correct
- `AccountManager` constructor is `public` — correct
- **Gap:** `BankingApp` stores DB credentials as `private static final` strings in source code — hardcoded credentials, not externalized

### Abstraction
- **Minimal.**
- Each class abstracts its domain (User auth, Account management, Transactions) — that is the extent of it
- No interfaces, no abstract classes, no service contracts
- Methods like `credit_money()`, `debit_money()` expose implementation details through their structure (input reading inside business methods)

### Inheritance
- **Not implemented.** No `extends` keyword used anywhere in the codebase.

### Polymorphism
- **Not implemented.** No method overriding, no interface implementation, no dynamic dispatch.

### Class Design Quality
- **Mediocre.** The class structure shows domain awareness, but violates Single Responsibility Principle (SRP) in every class. Each method does input, validation, DB access, and output together.
- `generateAccountNumber()` is `private` in `Accounts.java` — correct visibility
- `account_exist()` is `public` but used internally and externally — acceptable
- No constructors with validation — null `connection` or `scanner` would silently break the system

---

## 4. DATABASE & SQL ANALYSIS

### Inferred Schema

**Table: `User`**
```sql
CREATE TABLE User (
    full_name VARCHAR(255),
    email     VARCHAR(255) UNIQUE,  -- used as lookup key
    password  VARCHAR(255)          -- stored as BCrypt hash
);
```

**Table: `Accounts`**
```sql
CREATE TABLE Accounts (
    account_number BIGINT PRIMARY KEY,
    full_name      VARCHAR(255),
    email          VARCHAR(255),    -- foreign key to User.email
    balance        DECIMAL(10,2),   -- correct: exact fixed-point arithmetic
    security_pin   VARCHAR(255)     -- stored as BCrypt hash
);
```

### Relationships
- One `User` → One `Accounts` (enforced by `account_exist(email)` check in code, NOT by DB constraint)
- The relationship is email-based, not via a proper foreign key
- No `FOREIGN KEY` constraint observed in any query

### Normalization
- **Approximately 2NF.**
- `full_name` is stored in BOTH `User` and `Accounts` — **data duplication**, violates 3NF
- `email` stored in both tables instead of a proper FK relationship

### SQL Query Efficiency
- All queries use `PreparedStatement` with parameterized inputs — good for security and plan caching
- `generateAccountNumber()` uses `ORDER BY account_number DESC LIMIT 1` — fragile strategy (race condition risk)
- `credit_money()` selects only `security_pin` — correct, minimal column fetch
- `debit_money()` and `transfer_money()` select only `balance, security_pin` — correct
- `user_exist()` selects only `email` — correct
- No `SELECT *` in any method — **improved**
- No indexes are created or referenced in code (email lookups happen frequently but no index evidence)
- Debit and transfer use `UPDATE ... WHERE account_number = ? AND balance >= ?` — atomic balance check in the UPDATE itself

---

## 5. TRANSACTION MANAGEMENT

### Is JDBC Transaction Management Used?

**Yes — in `AccountManager.java` only.**

| Method | setAutoCommit(false) | commit() | rollback() | finally block |
|---|---|---|---|---|
| `credit_money()` | Yes | Yes | Yes | Yes — resets autoCommit |
| `debit_money()` | Yes | Yes | Yes | Yes — resets autoCommit |
| `transfer_money()` | Yes | Yes | Yes | Yes — resets autoCommit |
| `getBalance()` | No | N/A | N/A | N/A (read-only) |

### Transfer Consistency Analysis (`transfer_money`)

```java
connection.setAutoCommit(false);
// ... verify sender PIN with BCrypt.checkpw(), check balance ...
debitPreparedStatement.executeUpdate();   // debit sender (atomic: WHERE balance >= ?)
creditPreparedStatement.executeUpdate();  // credit receiver
if (rowsAffected1 > 0 && rowsAffected2 > 0) {
    connection.commit();
} else {
    connection.rollback();
}
```

**What works:** If both updates succeed → commit. If either fails → rollback. This prevents partial transfers.

**Race Condition — Partially Mitigated:**
The debit UPDATE uses `WHERE account_number = ? AND balance >= ?` — meaning if two concurrent debits race, only one will succeed at the SQL level (the second will see `rowsAffected = 0`). However, the prior SELECT still reads balance in a separate query, meaning a theoretical TOCTOU gap exists. Full elimination requires `SELECT ... FOR UPDATE` to lock the row at read time.

**Fix for full elimination:** Use `SELECT balance, security_pin FROM Accounts WHERE account_number = ? FOR UPDATE` inside the transaction to lock the row during the read, preventing any concurrent read or modify until commit.

**`setAutoCommit(true)` Reset — Now Correct:**
All three transaction methods use a `finally` block to reset `setAutoCommit(true)`, ensuring the connection is always restored to a clean state even if an exception is thrown mid-operation. This was a gap in a prior version; it is now fixed.

### Self-Transfer Guard
`transfer_money()` checks `if(sender_account_number == receiver_account_number)` before any DB operation — blocks self-transfers immediately.

---

## 6. SECURITY REVIEW

### jBCrypt — IMPLEMENTED

- `User.java:register()` → `BCrypt.hashpw(password, BCrypt.gensalt())` stored in DB
- `User.java:login()` → `SELECT password FROM User WHERE email = ?`, then `BCrypt.checkpw(inputPassword, storedHash)`
- `Accounts.java:open_account()` → `BCrypt.hashpw(security_pin, BCrypt.gensalt())` stored in DB
- All `AccountManager` methods → fetch `security_pin` hash from DB, then `BCrypt.checkpw(inputPin, stored_pin)`
- PIN is **never** compared via SQL — it is always verified in Java using BCrypt

### SQL Injection
- **Not vulnerable** for parameterized queries (all user input goes through `PreparedStatement`)
- `generateAccountNumber()` uses `Statement` but no user input is involved — acceptable

### Hardcoded Credentials
- `BankingApp.java:7-9`: DB URL, username, and password are hardcoded as `private static final` strings in source code
- Should use environment variables or a config file excluded from version control
- The DB user is `root` — full database privileges, not a least-privilege account

### Input Validation — IMPLEMENTED
- All three transaction methods (`credit_money`, `debit_money`, `transfer_money`) validate `amount.compareTo(BigDecimal.ZERO) <= 0` and reject with a message
- Zero and negative amounts are both blocked before any DB operation

### Other Security Gaps
1. **No session management:** After login, `email` and `account_number` are local variables — no token, no timeout
2. **No rate limiting:** Unlimited login attempts possible — brute-force vulnerability on login
3. **Security pin format:** No length/format enforcement in code (any string accepted as PIN)
4. **`scanner.nextInt()` on non-integer input:** `InputMismatchException` — unhandled, crashes the app

---

## 7. ERROR HANDLING & EDGE CASES

### Exception Handling
- All SQL operations are wrapped in `try-catch (SQLException e)` blocks
- Handling strategy: `e.printStackTrace()` — logs to stderr, does not inform the user, does not halt cleanly
- `RuntimeException` thrown in `Accounts.java` for account creation failure and missing account — these are unhandled by the caller in `BankingApp.java`
- Transaction methods use `finally` block for `setAutoCommit(true)` reset — correct

### Edge Cases Handled
| Scenario | Handled? | Where |
|---|---|---|
| Insufficient balance on debit | Yes | `AccountManager.debit_money()` |
| Insufficient balance on transfer | Yes | `AccountManager.transfer_money()` |
| Invalid security pin | Yes — BCrypt verified | All AccountManager methods |
| User already exists on register | Yes | `User.register()` |
| Account already exists | Yes | `Accounts.open_account()` |
| Invalid menu choice | Yes | `default` case in switch |
| Negative or zero amount | Yes | All AccountManager transaction methods |
| Self-transfer | Yes | `AccountManager.transfer_money()` |
| Switch fall-through case 2 → case 3 | **Fixed** — `break` present | `BankingApp.java:90` |

### Edge Cases NOT Handled
| Scenario | Issue |
|---|---|
| `scanner.nextInt()` on non-integer input | `InputMismatchException` — unhandled, crashes the app |
| `RuntimeException` from `getAccount_number()` | Not caught in `BankingApp.java` — crashes |
| Receiver account does not exist on transfer | Rollback fires (safe), but no clear error message to user |
| Brute-force PIN attempts | No lockout after N failures |

### `ensureSecurityPinColumnSupportsHash()` — UNDEFINED METHOD (Critical Bug)
`Accounts.java:open_account()` calls `ensureSecurityPinColumnSupportsHash()` at line 30, but this method is **not defined anywhere in the codebase**. This will cause a compile error. This must be defined or the call must be removed before the project can be built.

---

## 8. PERFORMANCE REVIEW

### `BigDecimal` for Money — IMPLEMENTED (Correct)
All monetary values use `BigDecimal`:
- `Accounts.java`: `scanner.nextBigDecimal()`, `preparedStatement.setBigDecimal(4, balance)`
- `AccountManager.java`: `scanner.nextBigDecimal()`, `setBigDecimal()`, `getBigDecimal("balance")`

The DB schema uses `DECIMAL(10,2)` and Java uses `BigDecimal` — exact arithmetic end-to-end. This is correct.

### Shared Single Connection
- One `Connection` object for the entire application lifetime
- No connection pooling (no HikariCP, no c3p0)
- Connection is never explicitly closed — resource leak
- In a multi-user scenario, this architecture collapses entirely

### `generateAccountNumber()` — Race Condition & Inefficiency
```java
SELECT account_number FROM Accounts ORDER BY account_number DESC LIMIT 1
```
- Reads the last account number and increments by 1
- In concurrent access: two users could get the same account number
- Fix: use `AUTO_INCREMENT` in MySQL or a UUID

### No PreparedStatement Reuse
- `PreparedStatement` objects are created fresh inside every method call on every invocation
- Not cached or reused — defeats some of the performance benefit of prepared statements

### Redundant DB Calls
- In `BankingApp.java` after login:
```java
if(!accounts.account_exist(email)){  // DB call 1
    // open account ...
}
account_number = accounts.getAccount_number(email);  // DB call 2
```
`getAccount_number()` always runs, even though `account_exist()` already queried the same table on the same column. Could be merged into one query.

---

## 9. SCALABILITY & DESIGN IMPROVEMENTS

### Immediate Fixes Required
1. **Define or remove `ensureSecurityPinColumnSupportsHash()`** in `Accounts.java` — project does not compile without this
2. **Externalize DB credentials** using environment variables or `.properties` file — do not commit credentials to source control
3. **Add input validation for non-numeric input** — catch `InputMismatchException` on `scanner.nextInt()`/`nextLong()`
4. **Fix account number generation** — use MySQL `AUTO_INCREMENT` or UUID to eliminate race condition
5. **Close resources** — use try-with-resources for `PreparedStatement` and `ResultSet`

### Already Implemented (Do Not Regress)
- BCrypt password and PIN hashing
- BigDecimal for all monetary values
- Amount > 0 validation on all transactions
- Atomic debit SQL (`WHERE balance >= ?`)
- Self-transfer guard
- `finally` block for `setAutoCommit` reset
- Switch fall-through bug fixed

### Architecture Improvements for Production

#### Layered Architecture
```
Presentation Layer  →  CLI (current) or REST API (future)
Service Layer       →  UserService, AccountService, TransactionService
Repository/DAO Layer → UserDAO, AccountDAO, TransactionDAO
Database Layer      →  MySQL
```

#### Connection Pooling
Replace `DriverManager.getConnection()` with HikariCP:
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(url);
config.setMaximumPoolSize(10);
HikariDataSource ds = new HikariDataSource(config);
```

#### Transaction Safety
Use `SELECT balance, security_pin FROM Accounts WHERE account_number = ? FOR UPDATE` inside a transaction to lock the row during balance check + update, fully eliminating the TOCTOU race.

#### Account Number Generation
Replace the `ORDER BY DESC LIMIT 1` approach with MySQL `AUTO_INCREMENT` or `UUID.randomUUID()`.

#### Audit Trail
Add a `Transactions` table to log every credit/debit/transfer with timestamp, sender, receiver, amount, and status.

---

## 10. INTERVIEW PREPARATION OUTPUT

---

### 90-Second Explanation

"I built a console-based Banking Management System in Java using JDBC and MySQL. The system has four main classes: `BankingApp` as the entry point, `User` for registration and login, `Accounts` for opening accounts, and `AccountManager` for financial transactions like credit, debit, and fund transfer.

The application connects to a MySQL database using JDBC's `DriverManager`, and all queries use `PreparedStatement` to prevent SQL injection. Passwords and security PINs are hashed using BCrypt — stored hashes are verified with `BCrypt.checkpw()` on every login and transaction. All monetary values use `BigDecimal` for exact precision.

For critical operations like fund transfer, I used JDBC transaction management — `setAutoCommit(false)`, `commit()`, and `rollback()` inside a `finally` block — to ensure atomicity so that a transfer either fully succeeds or fully rolls back.

If I were to improve this further, I would add connection pooling with HikariCP, a transaction audit table, proper resource cleanup with try-with-resources, and restructure into Service and DAO layers."

---

### 3-Minute Explanation

"This project is a console-based Banking Management System built using core Java, JDBC, and MySQL — no frameworks.

**Structure:** Four classes. `BankingApp` is the entry point — it holds DB credentials and the main while loop. `User` handles registration and login. `Accounts` manages account creation including account number generation. `AccountManager` is the core — it handles credit, debit, transfer, and balance check.

**JDBC Usage:** I load the MySQL JDBC driver using `Class.forName`, get a connection via `DriverManager`, and pass this single connection object to all other classes through constructor injection. All user-input queries use `PreparedStatement`, which prevents SQL injection.

**Security:** Passwords are hashed with BCrypt on registration and verified with `BCrypt.checkpw()` on login. Security PINs are also BCrypt-hashed — they are never compared via SQL, only in Java after fetching the stored hash.

**Transactions:** For credit, debit, and transfer, I disable auto-commit, execute the queries, and then either commit on success or rollback on failure inside a `finally` block. For debit and transfer, the UPDATE itself includes `AND balance >= ?` — so the balance check is atomic at the SQL level, not a separate read.

**What I'd improve:** The biggest remaining gap is that DB credentials are hardcoded in source. I'd move those to environment variables. I'd also add connection pooling, a transactions audit table, and restructure into DAO and Service layers for proper separation of concerns."

---

### Deep-Dive Explanation (10-Minute)

**Opening — Project Overview**

"This is a monolithic, single-tier console application. There's no web layer, no REST API, no framework. Everything runs in a single JVM process interacting with a local MySQL instance. Four Java classes handle distinct domains, but within each method, I/O, business logic, and DB access are mixed — it's SRP at the class level, not a true layered architecture.

**BankingApp — Entry Point & Remaining Issues**

`BankingApp` creates the `Connection`, the `Scanner`, and all three service objects — manual constructor injection. DB credentials (URL, username, password) are hardcoded as `private static final` strings. For any real deployment, these must come from environment variables or a gitignored config file. The switch statement bug (case 2 falling through to case 3) is fixed — `break` is now present at line 90.

**User.java — Authentication with BCrypt**

Registration reads email, full_name, and password. It checks `user_exist(email)` first. If the user is new, it calls `BCrypt.hashpw(password, BCrypt.gensalt())` and stores the hash — plain text is never written to the DB. Login queries `SELECT password FROM User WHERE email = ?`, retrieves the stored hash, and calls `BCrypt.checkpw(inputPassword, storedHash)` — the password is never compared via SQL, eliminating timing attacks and injection on the auth query.

**Accounts.java — Account Creation**

`open_account()` reads full name, initial balance as `BigDecimal`, and security PIN. The PIN is hashed with `BCrypt.hashpw(security_pin, BCrypt.gensalt())` before storing. There is one critical gap: `open_account()` calls `ensureSecurityPinColumnSupportsHash()` which is not defined anywhere in the codebase — this is a compile error that must be resolved.

`generateAccountNumber()` still uses `SELECT account_number FROM Accounts ORDER BY account_number DESC LIMIT 1` — a race condition under concurrent inserts. Fix: MySQL `AUTO_INCREMENT`.

**AccountManager.java — Transactions**

All methods use `BigDecimal` for amounts and validate `amount.compareTo(BigDecimal.ZERO) <= 0` before any DB operation.

`credit_money`: fetches only `security_pin`, verifies with `BCrypt.checkpw()`, then UPDATE balance. Uses `finally` block for `setAutoCommit(true)` reset.

`debit_money`: fetches `balance, security_pin`, BCrypt-verifies PIN, checks balance sufficiency, then `UPDATE Accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?` — the `AND balance >= ?` makes the update atomic: if a concurrent transaction depleted the balance between the read and the write, this UPDATE returns 0 rows and triggers rollback.

`transfer_money`: same pattern plus a self-transfer guard (`sender == receiver` blocked immediately). Both debit and credit execute in one transaction — if credit returns 0 rows (e.g., receiver doesn't exist), rollback fires.

**Security Summary**

BCrypt implemented for passwords and PINs. No SQL injection surface. Credentials hardcoded — must externalize. Brute-force protection absent. Non-numeric input can crash app.

**What I Would Do Next**

Fix `ensureSecurityPinColumnSupportsHash()` first — project doesn't compile. Then: externalize credentials, catch `InputMismatchException`, switch to `AUTO_INCREMENT`, add try-with-resources, add `Transactions` audit table, add HikariCP, restructure into DAO + Service layers."

---

## 10 Likely Interviewer Questions & Strong Answers

---

**Q1: Why did you use `PreparedStatement` instead of `Statement`?**

`PreparedStatement` prevents SQL injection by separating SQL code from user data. The query structure is sent to the database first and compiled, then parameters are bound — user input is treated as data, never as SQL syntax. I use `PreparedStatement` for every query involving user input. I use `Statement` once in `generateAccountNumber()` where there is no user input involved, so there is no injection risk there.

---

**Q2: Explain how you implemented JDBC transaction management in the fund transfer.**

In `transfer_money()`, I call `connection.setAutoCommit(false)` to disable auto-commit. I then execute two `UPDATE` statements — one to debit the sender, one to credit the receiver. I check that both return `rowsAffected > 0`. If both succeed, I call `connection.commit()`. If either fails, I call `connection.rollback()`, undoing both updates. `setAutoCommit(true)` is reset in a `finally` block so the connection is always restored to a clean state, even if an exception is thrown.

---

**Q3: How are passwords and PINs secured in this project?**

Both are hashed with BCrypt. On registration, `BCrypt.hashpw(password, BCrypt.gensalt())` generates a salted hash that is stored in the DB — the plain text is never persisted. On login, I fetch the stored hash by email and call `BCrypt.checkpw(inputPassword, storedHash)` — BCrypt handles the salt internally. Security PINs follow the same pattern in `Accounts.java` and are verified with `BCrypt.checkpw()` in every `AccountManager` method. The PIN is never compared via SQL.

---

**Q4: Why is using `BigDecimal` for monetary values important?**

`double` uses IEEE 754 binary floating-point, which cannot precisely represent many decimal fractions. In Java, `0.1 + 0.2` evaluates to `0.30000000000000004`. In a banking system, these rounding errors accumulate across transactions and create balance discrepancies. `BigDecimal` is exact fixed-point arithmetic — `0.1 + 0.2 == 0.3` always. This project uses `BigDecimal` throughout for all monetary values, matching the `DECIMAL(10,2)` type in MySQL.

---

**Q5: How do you handle the case where a fund transfer is partially completed?**

Both the debit and credit execute inside a single transaction with auto-commit disabled. I check both `rowsAffected > 0` before committing. If the credit fails — for example if the receiver account number doesn't exist — `rowsAffected2` is 0, the condition fails, and `connection.rollback()` is called, undoing the debit. Money is either fully moved or not moved at all.

---

**Q6: What is the race condition in your balance check, and how would you fix it?**

`debit_money()` reads balance in one query, then performs the debit UPDATE with `AND balance >= ?`. The atomic SQL guard means a concurrent debit that races to execute last will return 0 rows and rollback — it cannot produce a negative balance. However, the prior SELECT is still a separate round trip. Full elimination requires `SELECT balance, security_pin FROM Accounts WHERE account_number = ? FOR UPDATE` — this acquires a row-level lock at read time, preventing any concurrent transaction from reading or modifying that row until commit.

---

**Q7: Your `generateAccountNumber()` method is not thread-safe. What would you use instead?**

The current approach queries the maximum account number and adds 1 — two simultaneous registrations could generate the same number, causing a primary key violation. Fix: use MySQL `AUTO_INCREMENT` on the `account_number` column — the database engine handles sequencing atomically. Alternatively, `UUID.randomUUID()` requires no coordination at all.

---

**Q8: Why are hardcoded DB credentials a problem, even for a learning project?**

Hardcoded credentials are dangerous because: if the code is pushed to a public GitHub repository, the credentials are publicly exposed and git history preserves them even after removal. Rotating credentials requires recompiling and redeploying. The correct approach is `System.getenv("DB_PASSWORD")`, a `.properties` file in `.gitignore`, or a secrets manager. The fact that the DB user is `root` compounds the risk — root has full database privileges.

---

**Q9: Is this a layered architecture?**

No. A true layered architecture has strict boundaries — Presentation never touches DB, Business logic never does I/O. In this project, every `AccountManager` method reads from `Scanner`, enforces business rules, and executes SQL — all in one method. That is a monolithic design. The classes are domain-separated following Single Responsibility Principle, which is different from layered architecture. A proper refactor would extract a DAO layer for all SQL and a Service layer for business logic, leaving `BankingApp` as pure I/O.

---

**Q10: How would you scale this system to a production-level application?**

In order of priority: fix the compile error (`ensureSecurityPinColumnSupportsHash` is undefined), externalize DB credentials, catch `InputMismatchException` for scanner input. Then: add connection pooling with HikariCP, switch to `AUTO_INCREMENT` for account numbers, add a `Transactions` audit table. Then: restructure into DAO + Service layers, wrap with Spring Boot REST API, add JWT authentication, add `SELECT ... FOR UPDATE` for full concurrency safety, containerize with Docker.

---

## Quick Reference — Verified Bugs & Gaps

| # | Issue | Severity | Status | Location |
|---|---|---|---|---|
| 1 | Passwords stored in plain text | Critical | ✅ FIXED — BCrypt implemented | User.java |
| 2 | Security PINs stored in plain text | Critical | ✅ FIXED — BCrypt implemented | Accounts.java, AccountManager.java |
| 3 | DB credentials hardcoded in source | Critical | ❌ OPEN | BankingApp.java:7-9 |
| 4 | Switch fall-through: case 2 → case 3 | High | ✅ FIXED — break present | BankingApp.java:90 |
| 5 | `double` used for monetary values | High | ✅ FIXED — BigDecimal used | AccountManager.java, Accounts.java |
| 6 | Race condition on balance check + update | High | Partially mitigated — atomic UPDATE, SELECT not locked | AccountManager.debit_money(), transfer_money() |
| 7 | No validation for negative/zero amounts | High | ✅ FIXED — amount > 0 check added | AccountManager.java (all methods) |
| 8 | `generateAccountNumber()` not thread-safe | Medium | ❌ OPEN | Accounts.java:generateAccountNumber() |
| 9 | `SELECT *` in authentication queries | Medium | ✅ FIXED — specific columns selected | AccountManager.java, User.java |
| 10 | `BigDecimal` imported but never used | Low | ✅ FIXED — BigDecimal is used | AccountManager.java |
| 11 | `AccountManager` constructor is package-private | Low | ✅ FIXED — constructor is public | AccountManager.java:12 |
| 12 | `full_name` duplicated in User and Accounts | Low | ❌ OPEN | Schema design |
| 13 | Connection never closed — resource leak | Medium | ❌ OPEN | BankingApp.java |
| 14 | RuntimeException from getAccount_number() uncaught | Medium | ❌ OPEN | BankingApp.java:56 |
| 15 | jBCrypt not implemented | Critical | ✅ FIXED — fully implemented | User.java, Accounts.java, AccountManager.java |
| 16 | `ensureSecurityPinColumnSupportsHash()` called but not defined — compile error | Critical | ❌ OPEN | Accounts.java:open_account() |
| 17 | No brute-force protection on login or PIN | Medium | ❌ OPEN | User.java, AccountManager.java |
| 18 | `scanner.nextInt()` InputMismatchException unhandled | Medium | ❌ OPEN | BankingApp.java |
