# Banking Management System — Complete Interview Guide

> **Stack:** Java · MySQL · JDBC · PreparedStatements · Transaction Management  
> **Type:** Console-based application | **Pattern:** Layered Architecture

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture & Class Design](#2-architecture--class-design)
3. [Database Schema](#3-database-schema)
4. [Core Feature Walkthrough](#4-core-feature-walkthrough)
5. [Security Implementation](#5-security-implementation)
6. [Transaction Management](#6-transaction-management)
7. [JDBC Deep Dive](#7-jdbc-deep-dive)
8. [Known Issues & Improvements](#8-known-issues--improvements)
9. [OOP Principles in This Project](#9-oop-principles-in-this-project)
10. [Secure Registration & PIN Validation — Deep Dive](#10-secure-registration--pin-validation--deep-dive)
11. [Modularity, Scalability, and Reliability](#11-modularity-scalability-and-reliability--how-to-defend-these-resume-words)
12. [MySQL Concepts You Must Know](#12-mysql-concepts-you-must-know)
13. [JDBC Full API Chain — End to End](#13-jdbc-full-api-chain--end-to-end)
14. [Transaction Management — Deep Dive](#14-transaction-management--deep-dive)
15. [Verbal Answer Scripts](#15-verbal-answer-scripts)
16. [Interview Q&A — Concept Level](#16-interview-qa--concept-level)
17. [Interview Q&A — Code Level](#17-interview-qa--code-level)
18. [Resume Talking Points](#18-resume-talking-points)

---

## 1. Project Overview

### What it does
A fully functional console-based banking system that allows users to:
- Register and log in via email/password
- Open a bank account with a security PIN
- Perform credit, debit, and fund transfer operations
- Check account balance — all PIN-verified

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Java (JDK 8+) |
| Database | MySQL 5.7+ |
| DB Connectivity | JDBC (MySQL Connector/J) |
| Security | PreparedStatements, PIN auth |
| Transaction Control | Manual commit/rollback |

---

## 2. Architecture & Class Design

### Class Responsibility Map

```
BankingApp.java          ← Entry point. DB connection setup, menu loop
User.java                ← Registration, Login, user_exist check
Accounts.java            ← Account creation, account number generation, lookups
AccountManager.java      ← Credit, Debit, Transfer, Balance operations
```

### Design Pattern: Layered Architecture
- **Presentation Layer:** `BankingApp.java` — handles all user I/O and menu flow
- **Business Logic Layer:** `AccountManager.java`, `Accounts.java` — core banking rules
- **Data Access Layer:** All classes interact with DB via JDBC PreparedStatements

### Dependency Injection via Constructor
All service classes (`User`, `Accounts`, `AccountManager`) receive `Connection` and `Scanner` through their constructors — a manual form of dependency injection that promotes testability and single connection reuse.

```java
AccountManager accountManager = new AccountManager(connection, scanner);
```

---

## 3. Database Schema

### User Table
```sql
CREATE TABLE User (
    full_name VARCHAR(255) NOT NULL,
    email     VARCHAR(255) PRIMARY KEY,
    password  VARCHAR(255) NOT NULL
);
```

### Accounts Table
```sql
CREATE TABLE Accounts (
    account_number BIGINT       PRIMARY KEY,
    full_name      VARCHAR(255) NOT NULL,
    email          VARCHAR(255) UNIQUE,
    balance        DECIMAL(10, 2),
    security_pin   VARCHAR(255) NOT NULL,
    FOREIGN KEY (email) REFERENCES User(email)
);
```

### Relationships
- One `User` → One `Account` (enforced via UNIQUE on `email` in Accounts)
- Foreign key ensures referential integrity: you cannot have an account without a registered user

### Account Number Generation Strategy
```java
// Fetches the highest existing account_number and increments by 1
SELECT account_number FROM Accounts ORDER BY account_number DESC LIMIT 1
// Starting seed: 10000100
```

**Interview Note:** This is a simple sequential ID strategy. In production, use `AUTO_INCREMENT` or a UUID-based approach to avoid race conditions under concurrent inserts.

---

## 4. Core Feature Walkthrough

### 4.1 User Registration (`User.java`)
1. Read full name, email, password from console
2. Check if user already exists via `user_exist(email)`
3. If not, insert into `User` table using PreparedStatement
4. Password stored in **plaintext** — a known vulnerability (see Section 8)

### 4.2 User Login (`User.java`)
```java
SELECT * FROM User WHERE email = ? AND password = ?
```
- Returns the email string on success, `null` on failure
- Email is used as the session token for the current menu loop

### 4.3 Account Opening (`Accounts.java`)
1. Checks `account_exist(email)` — one account per user enforced
2. Generates next account number via `generateAccountNumber()`
3. Inserts into `Accounts` with initial balance and 4-digit (or any) security PIN

### 4.4 Credit Money (`AccountManager.java`)
1. Verifies account + PIN via SELECT
2. If valid, issues UPDATE: `balance = balance + ?`
3. Uses explicit transaction (`setAutoCommit(false)` → `commit()`)

### 4.5 Debit Money (`AccountManager.java`)
1. Verifies account + PIN
2. Reads `current_balance` from ResultSet
3. Checks: `amount <= current_balance` (prevents overdraft)
4. Issues UPDATE: `balance = balance - ?`

### 4.6 Transfer Money (`AccountManager.java`)
1. Reads receiver account number and amount
2. Verifies sender PIN and balance sufficiency
3. **Atomic execution:** Debit sender + Credit receiver in one transaction
4. Rolls back both on any failure — ensures no money is lost or duplicated

```java
// Both must succeed — atomicity guaranteed
int rowsAffected1 = debitPreparedStatement.executeUpdate();
int rowsAffected2 = creditPreparedStatement.executeUpdate();
if (rowsAffected1 > 0 && rowsAffected2 > 0) {
    connection.commit();
} else {
    connection.rollback();
}
```

### 4.7 Check Balance (`AccountManager.java`)
- PIN-verified SELECT of balance column only — least privilege principle applied

---

## 5. Security Implementation

### 5.1 SQL Injection Prevention
All database queries use **PreparedStatements** with parameterized inputs:
```java
PreparedStatement ps = connection.prepareStatement(
    "SELECT * FROM Accounts WHERE account_number = ? AND security_pin = ?"
);
ps.setLong(1, account_number);
ps.setString(2, security_pin);
```
User input never concatenates into SQL strings — this eliminates SQL injection entirely.

### 5.2 PIN-Based Transaction Authorization
Every financial operation (credit, debit, transfer, balance check) requires the security PIN — separate from the login password. This provides a second factor of verification.

### 5.3 Insufficient Balance Guard
```java
if (amount <= current_balance) { // debit/transfer only proceed if funds exist
```

### 5.4 Known Security Gaps (for interview honesty)

| Issue | Current State | Production Fix |
|---|---|---|
| Password storage | Plaintext | BCrypt/Argon2 hashing |
| Security PIN storage | Plaintext | Hashed with salt |
| Session management | In-memory variable | JWT or session tokens |
| Account number generation | Sequential, race-prone | DB AUTO_INCREMENT or UUID |
| Input validation | None | Regex/length checks |

---

## 6. Transaction Management

### Why Transactions Matter Here
In `transfer_money()`, two UPDATE operations must succeed or fail together. If debit succeeds but credit fails (e.g., invalid receiver account), the sender loses money with no credit to receiver — a catastrophic inconsistency.

### Implementation Pattern Used
```java
connection.setAutoCommit(false);    // Begin manual transaction
try {
    // execute operations
    connection.commit();            // Persist on success
    connection.setAutoCommit(true);
} catch (SQLException e) {
    connection.rollback();          // Undo everything on failure
    connection.setAutoCommit(true);
}
```

### ACID Properties Satisfied

| Property | How it's met |
|---|---|
| **Atomicity** | `commit()` / `rollback()` ensures all-or-nothing |
| **Consistency** | Balance checks before debit; FK constraints in DB |
| **Isolation** | MySQL default isolation (REPEATABLE READ) |
| **Durability** | `commit()` persists to disk via MySQL InnoDB |

---

## 7. JDBC Deep Dive

### Connection Lifecycle
```java
Class.forName("com.mysql.cj.jdbc.Driver");   // Load driver (explicit, legacy style)
Connection connection = DriverManager.getConnection(url, username, password);
```

**Note:** `Class.forName()` is not required from JDBC 4.0+ (Java 6+) because drivers register via ServiceLoader — but explicitly calling it does not break anything.

### PreparedStatement vs Statement

| Feature | Statement | PreparedStatement |
|---|---|---|
| SQL Injection | Vulnerable | Safe (parameterized) |
| Performance | Compiled each time | Pre-compiled, cached |
| Readability | String concat | Clean placeholders |
| Use case | Static DDL | Dynamic DML with inputs |

This project correctly uses `PreparedStatement` throughout.

### ResultSet Usage
```java
ResultSet rs = ps.executeQuery();
if (rs.next()) {                         // Moves cursor to first row
    double balance = rs.getDouble("balance");
}
```
- `rs.next()` returns `false` if no rows — used as existence/auth check throughout
- Always access columns by name (not index) for readability and safety against schema changes

---

## 8. Known Issues & Improvements

### Bug: Missing `break` in `BankingApp.java` switch-case
```java
case 2:   // Login
    // ... login logic
    // NO break — falls through to case 3 (Exit) after login!
case 3:
    System.out.println("THANK YOU...");
    return;
```
After a successful login and logout (choice2 = 5), control falls through to `case 3`, exiting the entire app. **Fix:** Add `break;` at the end of `case 2`.

### Issue: `scanner.nextLine()` Buffer Flush Pattern
`nextInt()` leaves a newline in the buffer. The code manually calls `scanner.nextLine()` at the start of methods to consume it. This is fragile — a better approach is to wrap Scanner with a utility method or always use `nextLine()` + `Integer.parseInt()`.

### Issue: Receiver Account Existence Not Verified in Transfer
```java
// No check whether receiver_account_number actually exists before crediting
creditPreparedStatement.setLong(2, receiver_account_number);
```
If the receiver account doesn't exist, `rowsAffected2 = 0` and rollback occurs — so money is safe — but the UX is poor. A pre-check with a SELECT would give a clear error message.

### Improvement Roadmap

| Priority | Improvement |
|---|---|
| Critical | Hash passwords and PINs (BCrypt) |
| High | Use connection pooling (HikariCP) |
| High | Add input validation (amount > 0, PIN format) |
| Medium | Add transaction history table + logging |
| Medium | Replace plaintext `double` balance with `BigDecimal` for precision |
| Low | Migrate to Spring Boot + JPA for production |
| Low | Add unit tests with Mockito for service layer |

### `double` vs `BigDecimal` for Money
The project uses `double` for balance. This is a classic interview trap:
```java
// Floating point precision error
0.1 + 0.2 = 0.30000000000000004
```
**Always use `BigDecimal` for financial calculations.** The DB schema correctly uses `DECIMAL(10, 2)` — but Java-side, `double` introduces precision errors.

---

## 9. OOP Principles in This Project

Your resume says: *"Applied OOP principles (User, Account, AccountManager)"* — interviewers **will** ask you to explain this. Here is the full breakdown.

### 9.1 Encapsulation

**Definition:** Bundling data and the methods that operate on that data inside a single class, and restricting direct access from outside.

**In this project:**
- `User.java` encapsulates user credentials and all user-related DB operations (`register`, `login`, `user_exist`). The `connection` and `scanner` fields are `private` — no outside class directly touches them.
- `AccountManager.java` encapsulates all financial operation logic. `BankingApp` doesn't know *how* a debit works — it just calls `accountManager.debit_money(account_number)`.

```java
// BankingApp has no idea about SQL or PIN logic — it's encapsulated inside AccountManager
accountManager.debit_money(account_number);
```

**Interview answer:** "I encapsulated data and behavior together in each class. For example, `AccountManager` owns all transaction logic — the main class just calls the method without knowing the implementation details."

---

### 9.2 Abstraction

**Definition:** Hiding internal complexity and exposing only what is necessary to the caller.

**In this project:**
- `BankingApp.java` calls `user.login()` and gets back either an email string or `null`. It has no idea about PreparedStatements, ResultSets, or SQL queries happening inside.
- `accounts.open_account(email)` returns an `account_number` — the caller doesn't know about `generateAccountNumber()` or the INSERT query.

```java
// Caller sees a simple method. Complexity of DB interaction is abstracted away.
long account_number = accounts.open_account(email);
```

**Interview answer:** "Each class exposes clean methods with simple inputs and outputs. The main application doesn't deal with SQL or JDBC — that complexity is abstracted inside the service classes."

---

### 9.3 Single Responsibility Principle (SRP — part of OOP best practices)

Each class has exactly one reason to change:

| Class | Sole Responsibility |
|---|---|
| `User` | User identity — registration and authentication |
| `Accounts` | Account lifecycle — creation, lookup, existence check |
| `AccountManager` | Financial operations — credit, debit, transfer, balance |
| `BankingApp` | Application flow — menu, user input, orchestration |

**Interview answer:** "I split responsibilities so that if the transaction logic changes, I only touch `AccountManager`. If the DB schema for users changes, I only touch `User`. This is the Single Responsibility Principle."

---

### 9.4 Why No Inheritance or Polymorphism Here?

Interviewers may ask: *"Did you use inheritance?"*

**Honest answer:** "This project doesn't use inheritance because the entities (`User`, `Account`, `AccountManager`) don't share a parent-child relationship — they represent different domains. Forcing inheritance here would violate good design. However, in a scaled version, I would introduce an interface like `TransactionService` that `AccountManager` implements, enabling polymorphism if multiple transaction strategies (e.g., savings vs. current account) are needed."

---

## 10. Secure Registration & PIN Validation — Deep Dive

Your resume says: *"secure user registration"* and *"PIN-based transaction validation"*. Here is how to defend both in an interview.

### 10.1 What "Secure Registration" Means in This Project

**What is implemented:**
- Duplicate check via `user_exist(email)` before insertion — prevents duplicate accounts
- PreparedStatement for INSERT — prevents SQL injection during registration
- Email as primary key — enforces uniqueness at the DB level as a second guard

**What is NOT implemented (be honest in interviews):**
- Password hashing — passwords are stored in plaintext. In production, BCrypt or Argon2 should be used
- Email format validation — no regex check on input
- Password strength rules — no minimum length or complexity enforcement

**How to say this in an interview:**
> "Registration prevents duplicate users and uses PreparedStatements to prevent SQL injection. For a production system, I would add BCrypt password hashing — storing plaintext passwords is a security vulnerability I'm aware of and would fix."

This shows self-awareness, which interviewers value more than pretending the project is perfect.

---

### 10.2 PIN-Based Transaction Validation — Full Flow

Every financial operation uses the same validation pattern. Understanding this flow lets you answer any question about it.

**Step 1: User provides PIN**
```java
System.out.print("Enter Security Pin: ");
String security_pin = scanner.nextLine();
```

**Step 2: PIN is verified against the DB in the same query that fetches account data**
```java
PreparedStatement ps = connection.prepareStatement(
    "SELECT * FROM Accounts WHERE account_number = ? AND security_pin = ?"
);
ps.setLong(1, account_number);
ps.setString(2, security_pin);
ResultSet rs = ps.executeQuery();

if (rs.next()) {
    // PIN is correct — proceed with transaction
} else {
    System.out.println("Invalid Security Pin!");
    // Transaction blocked
}
```

**Why this design works:**
- PIN validation and account fetch happen in a **single round trip** to the DB — efficient
- `rs.next()` returning `false` means either the account doesn't exist OR the PIN is wrong — both are blocked
- The PIN is checked **before** any balance modification — no partial state is ever written

**Two-factor analogy for interview:**
> "Login uses email + password. But every transaction requires the security PIN separately. So even if someone is logged in, they can't move money without the PIN — it's a second layer of verification."

**PIN Security Gap (be honest):**
- PINs are stored in plaintext, same as passwords
- No brute-force lockout after N failed attempts
- Production fix: hash PINs, add attempt counter with lockout

---

### 10.3 How PIN Validation Differs Across Operations

| Operation | PIN Required | Balance Check | Atomic Transaction |
|---|---|---|---|
| Credit | Yes | No | Yes |
| Debit | Yes | Yes (overdraft guard) | Yes |
| Transfer | Yes (sender only) | Yes (sender balance) | Yes (both accounts) |
| Balance Check | Yes | No | No (read-only) |

**Interview Q: Why doesn't the receiver need to enter a PIN during a transfer?**  
Because the receiver is not initiating the transaction. The sender authorizes the outgoing transfer with their own PIN. The receiver's account is the destination — this mirrors how real banking works (you don't need the recipient's PIN to send them money).

---

## 11. Modularity, Scalability, and Reliability — How to Defend These Resume Words

Your resume uses: *"modularity, scalability, and reliability"*. Interviewers may directly ask: *"How is your project modular/scalable/reliable?"*

### Modularity

**Definition:** System is divided into independent, interchangeable components.

**Evidence in this project:**
- 4 classes, each independently responsible for one domain
- `AccountManager` can be tested or replaced without touching `User` or `Accounts`
- Shared `Connection` object is injected — swapping to a connection pool requires changing only `BankingApp.java`, not the service classes
- Adding a new feature (e.g., loan management) = new class `LoanManager`, no changes to existing classes

### Scalability

**Definition:** System can handle growth — more users, more features — with minimal restructuring.

**Evidence in this project:**
- New transaction types (e.g., recurring transfers, fixed deposits) can be added as new methods in `AccountManager` without changing the menu structure significantly
- DB schema uses standard relational design — adding new tables (e.g., `TransactionHistory`) requires no changes to existing tables
- The layered architecture means the UI layer (`BankingApp`) can be swapped for a web interface without rewriting business logic

**Honest caveat for interview:**
> "Currently it's a single-user console app with one DB connection. To scale to multiple concurrent users, I'd add connection pooling (HikariCP), handle concurrent access with proper isolation levels, and migrate to a web framework like Spring Boot."

### Reliability

**Definition:** System behaves correctly under normal and error conditions.

**Evidence in this project:**
- **Transaction rollback:** If any step in a transfer fails, the entire operation is rolled back — no partial state
- **Overdraft prevention:** Debit and transfer check balance before modifying it
- **Duplicate guards:** `user_exist()` and `account_exist()` prevent duplicate insertions
- **SQL exception handling:** All DB operations are wrapped in try-catch with rollback in catch blocks

**Interview answer:**
> "Reliability is enforced through DB transactions with commit/rollback — if anything fails mid-transfer, no money is lost or duplicated. I also added application-level guards like overdraft checks and duplicate account prevention."

---

## 12. MySQL Concepts You Must Know

Your resume says "MySQL database" — interviewers expect MySQL-specific knowledge, not just generic SQL.

### 12.1 Why InnoDB Storage Engine Matters

MySQL has multiple storage engines. This project depends on **InnoDB** (default since MySQL 5.5).

| Feature | InnoDB | MyISAM |
|---|---|---|
| Transactions | ✅ Supports COMMIT/ROLLBACK | ❌ No |
| Foreign Keys | ✅ Enforced | ❌ Not enforced |
| Row-level locking | ✅ Yes | ❌ Table-level only |
| Crash recovery | ✅ Yes (redo log) | ❌ No |
| ACID compliance | ✅ Full | ❌ No |

**Interview answer:** "This project relies on InnoDB — it's the only MySQL engine that supports transactions and enforces foreign keys. If the table used MyISAM, `commit()` and `rollback()` would silently do nothing and foreign key constraints would not be checked."

---

### 12.2 Why `DECIMAL(10, 2)` and Not `FLOAT` or `DOUBLE`

```sql
balance DECIMAL(10, 2)   -- correct: exact fixed-point arithmetic
-- NOT: balance FLOAT    -- wrong: binary floating-point imprecision
-- NOT: balance DOUBLE   -- wrong: same problem, larger range
```

`DECIMAL(10, 2)` means up to 10 total digits, exactly 2 after the decimal. MySQL stores it as exact digits — no rounding.

`FLOAT`/`DOUBLE` use IEEE 754 binary representation and cannot represent `0.1` exactly, leading to errors like `0.1 + 0.2 = 0.30000000000000004`.

**Interview answer:** "I used `DECIMAL(10,2)` because floating-point types introduce precision errors in financial calculations. Money requires exact arithmetic — decimal type guarantees that Rs.100.50 stored is Rs.100.50 retrieved, always."

---

### 12.3 Primary Key Internals

`account_number BIGINT PRIMARY KEY` does three things at the MySQL/InnoDB level:
- Creates a **clustered index** — InnoDB physically stores data rows ordered by this key
- Enforces uniqueness at every INSERT — duplicate throws `SQLException` (error 1062)
- Makes `WHERE account_number = ?` an O(log n) B-tree lookup — no full table scan

**Interview answer:** "InnoDB's primary key is a clustered index — the data is physically stored in key order. Every `WHERE account_number = ?` is a B-tree search, not a scan. That's why I used BIGINT for the account number — efficient numeric comparison."

---

### 12.4 Foreign Key — What Happens at Runtime

```sql
FOREIGN KEY (email) REFERENCES User(email)
```

InnoDB enforces this at every write operation:
- INSERT into Accounts with email not in User → **blocked**, error 1452
- DELETE a User who has an Account → **blocked**, error 1451
- No `CASCADE` is defined, so parent deletion is fully blocked

**Interview Q: What happens if you delete a user who has a bank account?**
The DELETE is rejected by MySQL with a foreign key constraint violation. The fix would be to add `ON DELETE CASCADE` to auto-delete the account, or `ON DELETE SET NULL` to nullify the FK — depending on business requirements.

---

### 12.5 Indexes Created Automatically

| Column | Constraint | Index Type |
|---|---|---|
| `account_number` | PRIMARY KEY | Clustered (InnoDB) |
| `email` in User | PRIMARY KEY | Clustered |
| `email` in Accounts | UNIQUE | Non-clustered unique index |

Every `WHERE` clause in this project filters on indexed columns — no operation does a full table scan.

---

## 13. JDBC Full API Chain — End to End

Your resume says "integrated a MySQL database using JDBC." You must explain the full flow.

### 13.1 The Complete JDBC Object Chain

```
DriverManager
    └── Connection               (one live DB session / socket)
            └── PreparedStatement    (compiled SQL template)
                    └── ResultSet    (cursor over result rows)
```

### 13.2 Step-by-Step JDBC Flow

**Step 1 — Register Driver**
```java
Class.forName("com.mysql.cj.jdbc.Driver");
```
Registers the MySQL driver. In JDBC 4.0+ (Java 6+) this is automatic via ServiceLoader — but explicit registration is harmless and shows intent.

**Step 2 — Open Connection**
```java
Connection connection = DriverManager.getConnection(url, username, password);
```
Opens a TCP socket to MySQL, authenticates, and returns a `Connection` representing a live DB session. This is the most expensive operation — creating a new connection per request in a web app is why connection pools exist.

**Step 3 — Prepare Statement**
```java
PreparedStatement ps = connection.prepareStatement(
    "SELECT * FROM Accounts WHERE account_number = ? AND security_pin = ?"
);
```
The SQL template is sent to MySQL for parsing and pre-compilation. The `?` placeholders are typed slots — not yet filled.

**Step 4 — Bind Parameters**
```java
ps.setLong(1, account_number);   // first ?
ps.setString(2, security_pin);   // second ?
```
Parameters are bound with type-safe setters. MySQL receives them as typed values, never as raw SQL text — this is exactly what prevents injection.

**Step 5 — Execute**
```java
ResultSet rs = ps.executeQuery();     // for SELECT
int rows   = ps.executeUpdate();      // for INSERT / UPDATE / DELETE
```

**Step 6 — Read ResultSet**
```java
if (rs.next()) {
    double bal = rs.getDouble("balance");
}
```
Cursor starts before the first row. `rs.next()` advances it and returns `false` when rows are exhausted.

**Step 7 — Resource Cleanup (gap in this project)**

This project does NOT close `ResultSet` or `PreparedStatement` — a resource leak in production. Correct pattern:
```java
try (PreparedStatement ps = connection.prepareStatement(query);
     ResultSet rs = ps.executeQuery()) {
    // both auto-closed when block exits, even on exception
}
```

### 13.3 executeQuery() vs executeUpdate()

| Method | Use case | Returns |
|---|---|---|
| `executeQuery()` | SELECT | `ResultSet` |
| `executeUpdate()` | INSERT, UPDATE, DELETE | `int` rows affected |
| `execute()` | Any SQL | `boolean` |

**Interview Q: How do you confirm a transaction succeeded?**
`executeUpdate()` returns rows affected. Zero means no row matched the WHERE clause — the operation had no effect. This project checks `rowsAffected > 0` for every financial update for exactly this reason.

---

## 14. Transaction Management — Deep Dive

### 14.1 What "Atomic" Means for This Specific Transfer

```
Transfer Rs.500 from Account A → Account B:
  Op 1: UPDATE Accounts SET balance = balance - 500 WHERE account_number = A
  Op 2: UPDATE Accounts SET balance = balance + 500 WHERE account_number = B
```

**Without atomicity (autoCommit = true):**
Op 1 commits immediately. If any failure occurs before Op 2 executes, A has lost Rs.500 and B never received it. Rs.500 is destroyed.

**With atomicity (this project):**
Both ops run inside one transaction. If Op 2 fails → rollback undoes Op 1. Either both succeed or neither does. Rs.500 is never lost.

**Interview answer:** "Atomicity in my transfer means the debit and credit are one indivisible unit. I disable autoCommit, run both UPDATEs, and only commit if both return rowsAffected > 0. Any failure triggers rollback — the sender's balance is restored exactly as it was."

---

### 14.2 What "Consistent" Means in This Project

The DB moves from one valid state to another — no integrity rule is violated after a transaction.

Constraints that must hold after every operation:
- Balance ≥ 0 (application-level overdraft check)
- Every `Accounts.email` exists in `User.email` (FK constraint)
- No two accounts share an email (UNIQUE constraint)
- Account numbers are unique (PRIMARY KEY)

---

### 14.3 Isolation Levels — Full Breakdown

MySQL default is **REPEATABLE READ**. All four levels explained:

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ UNCOMMITTED | ✅ Possible | ✅ Possible | ✅ Possible |
| READ COMMITTED | ❌ Prevented | ✅ Possible | ✅ Possible |
| **REPEATABLE READ** (default) | ❌ | ❌ Prevented | ✅ Possible |
| SERIALIZABLE | ❌ | ❌ | ❌ Prevented |

**Dirty Read:** You read a row another transaction modified but hasn't committed yet. If it rolls back, you read data that never officially existed.

**Non-Repeatable Read:** You read a row, another transaction updates and commits it, you read the same row again — different values within the same transaction.

**Phantom Read:** You query rows matching a condition, another transaction inserts a new matching row and commits, you re-run the query — a new "phantom" row appears.

**Why REPEATABLE READ is acceptable here:** This is a single-user console app. In a concurrent banking app, you'd use `SELECT ... FOR UPDATE` to lock rows during a balance check + debit sequence, preventing two concurrent transactions from both seeing the same balance as sufficient.

---

### 14.4 setAutoCommit — Why It's Reset After Every Transaction

```java
connection.setAutoCommit(false);
try {
    // ... operations ...
    connection.commit();
} catch (SQLException e) {
    connection.rollback();
}
connection.setAutoCommit(true);   // ← CRITICAL — always reset
```

The same `Connection` object is reused for ALL subsequent operations in this app. If `setAutoCommit(true)` is not called at the end, every future SELECT, INSERT, or UPDATE also sits in a pending transaction requiring an explicit commit — breaking all non-transactional operations silently.

---

## 15. Verbal Answer Scripts

Interviewers say: *"Walk me through how X works."* These are the answers to know cold.

### "Walk me through how a user registers"

> "The user chooses Register and provides full name, email, and password. Before inserting, I call `user_exist(email)` — a SELECT to check if that email already exists in the User table. If it does, I block registration with a clear message. If not, I use a PreparedStatement to INSERT the new record. PreparedStatement is important here because the email and password fields are user inputs — without parameterized queries, a malicious email like `' OR '1'='1` could manipulate the SQL. On success, `executeUpdate()` returns 1 confirming the row was inserted."

---

### "Walk me through how login works"

> "Login takes email and password and runs a SELECT WHERE both match — using a PreparedStatement to prevent injection. If `resultSet.next()` returns true, a matching row exists and I return the email string. Null means no match — invalid credentials. The returned email acts as the in-memory session for the rest of the loop. There's no formal token — the program knows you're logged in because the email variable is non-null within the while loop scope."

---

### "Walk me through how deposit works"

> "After login, the user's account number is fetched once and held in a variable throughout the session. For deposit, the user enters an amount and their security PIN. I run a SELECT filtering on both account number and PIN — this is the authorization check. If the row is returned, the PIN is valid. I then run `UPDATE Accounts SET balance = balance + ? WHERE account_number = ?` inside a transaction. I commit only if rowsAffected is greater than zero. If the update fails, I rollback and report failure."

---

### "Walk me through a fund transfer"

> "Transfer is the most complex operation. The user enters the receiver's account number, amount, and their security PIN. First I verify the sender's PIN and read their current balance. If balance is less than the amount, I block immediately with an Insufficient Balance message — no DB modification happens. If valid, I prepare two UPDATE statements: debit sender, credit receiver. Both execute inside a single transaction with autoCommit disabled. I check that both rowsAffected values are greater than 0 and only then commit. If either fails — for example if the receiver account number doesn't exist — I rollback both, so the sender's money is not lost."

---

### "What happens if two users transfer money simultaneously?"

> "The current design has a single connection and no concurrency handling — it's built for single-user use. In a concurrent scenario, two transactions could both read the same balance, both find it sufficient, and both debit — resulting in a negative balance. The correct fix is `SELECT balance FROM Accounts WHERE account_number = ? FOR UPDATE` — this row-level lock prevents any other transaction from reading or modifying that row until the lock is released at commit. MySQL InnoDB supports this."

---

### "How does your project handle failures and errors?"

> "All DB operations are in try-catch blocks for SQLException. In transaction methods, the catch block calls `rollback()` before printing the error — ensuring no partial state persists. AutoCommit is reset to true in both success and failure paths so the connection stays usable for subsequent operations. The main weakness is that exceptions print stack traces rather than user-friendly messages — in production I'd use a proper logging framework and custom exception classes."

---

## 16. Interview Q&A — Concept Level

**Q: What is JDBC and how does it work?**  
JDBC (Java Database Connectivity) is a Java API that provides a standard interface to interact with relational databases. It uses a driver model — `DriverManager` loads the appropriate driver (e.g., MySQL Connector/J), which translates Java calls into database-specific protocol.

**Q: What is the difference between `Statement` and `PreparedStatement`?**  
`Statement` concatenates user input directly into SQL — vulnerable to injection and recompiled each time. `PreparedStatement` pre-compiles the query template and binds parameters separately — safe from injection, better performance on repeated execution.

**Q: What are ACID properties?**  
- **Atomicity:** All operations in a transaction succeed or all fail  
- **Consistency:** DB moves from one valid state to another  
- **Isolation:** Concurrent transactions don't interfere  
- **Durability:** Committed data persists even after crashes

**Q: Why do we call `setAutoCommit(false)` before a transfer?**  
By default, every SQL statement auto-commits. For a transfer (debit + credit), we need both to be atomic. Disabling auto-commit lets us group them into a single transaction and call `commit()` only when both succeed — or `rollback()` if either fails.

**Q: What is a Foreign Key? How is it used here?**  
A FK enforces referential integrity between tables. Here, `Accounts.email` references `User.email`, ensuring no account can exist for an unregistered user. Deleting a user without cascade rules would be blocked.

**Q: What is SQL Injection? How does this project prevent it?**  
SQL Injection is an attack where malicious SQL is inserted into input fields to manipulate queries. This project prevents it by using PreparedStatements, where user input is always treated as data (bound parameters), never as executable SQL.

**Q: What is Connection Pooling and why is it not used here?**  
Connection pooling maintains a pool of reusable DB connections to avoid the overhead of creating a new connection per request. This project creates one connection at startup and holds it — acceptable for a single-user console app, but a bottleneck in a multi-user web application. HikariCP is the standard pool for Java.

**Q: What is the difference between `commit()` and `rollback()`?**  
`commit()` permanently saves all changes made since the last commit. `rollback()` undoes all changes back to the last commit point — used when an error occurs to preserve data integrity.

---

## 17. Interview Q&A — Code Level

**Q: Why does `open_account` throw `RuntimeException` instead of `SQLException`?**  
`RuntimeException` is unchecked — it propagates up the call stack without forcing callers to handle it with try-catch. This simplifies the caller code in `BankingApp.java`. However, for production, a custom checked exception (e.g., `AccountCreationException`) would be more explicit and descriptive.

**Q: What happens if `generateAccountNumber()` is called concurrently by two users?**  
Both would read the same `MAX(account_number)` and generate the same next number — causing a `PRIMARY KEY` constraint violation. Fix: use `AUTO_INCREMENT` in MySQL or a sequence, which is atomic at the DB level.

**Q: Why is `account_exist()` called before opening an account?**  
To enforce the business rule of one account per email. While the DB UNIQUE constraint on `email` would also prevent duplicates, the application-level check provides a user-friendly error message instead of an unhandled `SQLException`.

**Q: Explain the `resultSet.next()` call used as an auth check.**  
`resultSet.next()` moves the cursor to the first row and returns `true` if a row exists, `false` otherwise. Since the SELECT filters by both `account_number` AND `security_pin`, a returned row confirms both are correct — functioning as authentication.

**Q: Why is there a `scanner.nextLine()` at the start of some methods?**  
`scanner.nextInt()` in the menu reads the integer but leaves the newline character (`\n`) in the buffer. The next `scanner.nextLine()` call would immediately return an empty string if that leftover isn't consumed first. The `scanner.nextLine()` at method start discards that leftover newline.

**Q: What would you change to make this production-ready?**  
Hash passwords with BCrypt, use `BigDecimal` for money, add a connection pool (HikariCP), implement a transaction history table, add input validation, introduce a service/DAO layer separation, and write JUnit + Mockito tests for business logic.

---

## 18. Resume Talking Points

**Project Title:** Banking Management System (Java + MySQL)

**One-liner:** Built a console-based banking system in Java using JDBC and MySQL with full CRUD, PIN-authenticated transactions, and ACID-compliant fund transfers.

**Bullet points for resume:**
- Implemented multi-class layered architecture (User, Account, Transaction) with shared JDBC connection via constructor injection
- Used PreparedStatements throughout to prevent SQL injection across all DML operations
- Enforced ACID atomicity for fund transfers using manual transaction control (`setAutoCommit`, `commit`, `rollback`)
- Designed relational schema with Foreign Key constraints ensuring referential integrity between User and Account tables
- Implemented overdraft prevention and PIN-based authorization for all financial operations

**Skills demonstrated:**
`Java` · `JDBC` · `MySQL` · `SQL` · `PreparedStatement` · `Transaction Management` · `Layered Architecture` · `Database Design` · `Security Best Practices`
