# Banking Management System

A console-based Banking Management System built with Java and MySQL that provides essential banking operations including user registration, account management, and financial transactions.

## How to Use

### Quick Start

1. **Setup MySQL Database**
   - Install MySQL Server on your system
   - Create a database named `banking_system`
   - Run the SQL scripts provided in the [Database Setup](#database-setup) section

2. **Download MySQL Connector**
   - Download MySQL Connector/J from [here](https://dev.mysql.com/downloads/connector/j/)
   - Place the JAR file in the `lib/` folder of this project

3. **Configure Database Credentials**
   - Configure DB credentials via environment variables (recommended):
     - `BANK_DB_URL` (default: `jdbc:mysql://127.0.0.1:3306/banking_system`)
     - `BANK_DB_USERNAME` (default: `root`)
     - `BANK_DB_PASSWORD` (default: empty)

4. **Compile the Project**
   ```bash
   javac -cp ".;lib/*" BankingApp.java User.java Accounts.java AccountManager.java
   ```

5. **Run the Application**
   ```bash
   java -cp ".;lib/*" BankingManagementSystem.BankingApp
   ```

6. **Follow the Menu**
   - Register a new user
   - Login with your credentials
   - Open a bank account
   - Perform banking operations (deposit, withdraw, transfer, check balance)

## Features

- **User Management**
  - User Registration
  - User Login
  - Email-based authentication

- **Account Management**
  - Open new bank account
  - Auto-generated account numbers
  - Security PIN protection

- **Financial Operations**
  - Credit Money (Deposit)
  - Debit Money (Withdrawal)
  - Transfer Money between accounts
  - Check Account Balance

- **Security**
  - PIN-based transaction verification
  - SQL injection prevention using PreparedStatements
  - Transaction rollback on failure

## Requirements

### Software Requirements
- Java Development Kit (JDK) 8 or higher
- MySQL Server 5.7 or higher
- MySQL Connector/J (JDBC Driver)

### Dependencies
- **MySQL Connector/J**: Required JDBC driver for MySQL database connectivity
  - Place the `mysql-connector-j-x.x.x.jar` file in the `lib/` folder
  - Download from: [MySQL Connector/J Downloads](https://dev.mysql.com/downloads/connector/j/)

## Database Setup

1. Create a MySQL database named `banking_system`:
```sql
CREATE DATABASE banking_system;
USE banking_system;
```

2. Create the `User` table:
```sql
CREATE TABLE User (
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) PRIMARY KEY,
    password VARCHAR(255) NOT NULL
);
```

3. Create the `Accounts` table:
```sql
CREATE TABLE Accounts (
    account_number BIGINT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    balance DECIMAL(10, 2),
    security_pin VARCHAR(255) NOT NULL,
    FOREIGN KEY (email) REFERENCES User(email)
);
```

## Project Structure

```
BankingManagementSystem/
├── BankingApp.java          # Main application entry point
├── User.java                # User registration and login
├── Accounts.java            # Account creation and management
├── AccountManager.java      # Financial transaction operations
├── lib/                     # External libraries
│   └── mysql-connector-j-x.x.x.jar
└── README.md
```

## Configuration

Configure database credentials using environment variables (recommended):
- `BANK_DB_URL` (default: `jdbc:mysql://127.0.0.1:3306/banking_system`)
- `BANK_DB_USERNAME` (default: `root`)
- `BANK_DB_PASSWORD` (default: empty)

Example (PowerShell):
```powershell
$env:BANK_DB_URL = "jdbc:mysql://127.0.0.1:3306/banking_system"
$env:BANK_DB_USERNAME = "root"
$env:BANK_DB_PASSWORD = "YOUR_PASSWORD"
```

## Compilation and Execution

### Using Command Line

1. **Compile** (with MySQL connector in lib folder):
```bash
javac -cp ".;lib/*" BankingApp.java User.java Accounts.java AccountManager.java
```

2. **Run**:
```bash
java -cp ".;lib/*" BankingManagementSystem.BankingApp
```

### Using IDE
1. Add `mysql-connector-j-x.x.x.jar` to project classpath
2. Run `BankingApp.java`

## Usage

1. **Register**: Create a new user account with email and password
2. **Login**: Access your account using credentials
3. **Open Account**: Create a bank account with initial deposit and security PIN
4. **Perform Transactions**:
   - Deposit money to your account
   - Withdraw money from your account
   - Transfer money to other accounts
   - Check your balance

## Security Features

- All transactions require security PIN verification
- Database transactions with auto-commit disabled for integrity
- Automatic rollback on transaction failures
- Prepared statements to prevent SQL injection

## Notes

- Account numbers are auto-generated starting from 10000100
- All monetary transactions require security PIN
- Insufficient balance checks before debit/transfer operations
- Transaction atomicity maintained using database commits and rollbacks

## Author

Developed as a Java-MySQL banking application project.

## License

This project is for educational purposes.
