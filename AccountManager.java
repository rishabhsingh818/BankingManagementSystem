package BankingManagementSystem;

import org.mindrot.jbcrypt.BCrypt;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Scanner;

public class AccountManager {
    private Connection connection;
    private Scanner scanner;

    public AccountManager(Connection connection, Scanner scanner){
        this.connection = connection;
        this.scanner = scanner;
    }

    public void credit_money(long account_number) throws SQLException {
        scanner.nextLine();
        System.out.print("Enter Amount: ");
        BigDecimal amount = scanner.nextBigDecimal();
        scanner.nextLine();
        System.out.print("Enter Security Pin: ");
        String security_pin = scanner.nextLine();

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            System.out.println("Amount must be greater than zero!");
            return;
        }

        try {
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT security_pin FROM Accounts WHERE account_number = ?");
            preparedStatement.setLong(1, account_number);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String stored_pin = resultSet.getString("security_pin");
                if(!BCrypt.checkpw(security_pin, stored_pin)){
                    System.out.println("Invalid Security Pin!");
                    connection.setAutoCommit(true);
                    return;
                }
                PreparedStatement preparedStatement1 = connection.prepareStatement(
                        "UPDATE Accounts SET balance = balance + ? WHERE account_number = ?");
                preparedStatement1.setBigDecimal(1, amount);
                preparedStatement1.setLong(2, account_number);
                int rowsAffected = preparedStatement1.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Rs." + amount + " credited Successfully");
                    connection.commit();
                } else {
                    System.out.println("Transaction Failed!");
                    connection.rollback();
                }
            } else {
                System.out.println("Account not found!");
                connection.rollback();
            }
        } catch (SQLException e) {
            connection.rollback();
            e.printStackTrace();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void debit_money(long account_number) throws SQLException {
        scanner.nextLine();
        System.out.print("Enter Amount: ");
        BigDecimal amount = scanner.nextBigDecimal();
        scanner.nextLine();
        System.out.print("Enter Security Pin: ");
        String security_pin = scanner.nextLine();

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            System.out.println("Amount must be greater than zero!");
            return;
        }

        try {
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT balance, security_pin FROM Accounts WHERE account_number = ?");
            preparedStatement.setLong(1, account_number);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String stored_pin = resultSet.getString("security_pin");
                if(!BCrypt.checkpw(security_pin, stored_pin)){
                    System.out.println("Invalid Pin!");
                    connection.setAutoCommit(true);
                    return;
                }
                BigDecimal current_balance = resultSet.getBigDecimal("balance");
                if (amount.compareTo(current_balance) > 0) {
                    System.out.println("Insufficient Balance!");
                    connection.setAutoCommit(true);
                    return;
                }
                PreparedStatement preparedStatement1 = connection.prepareStatement(
                        "UPDATE Accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?");
                preparedStatement1.setBigDecimal(1, amount);
                preparedStatement1.setLong(2, account_number);
                preparedStatement1.setBigDecimal(3, amount);
                int rowsAffected = preparedStatement1.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Rs." + amount + " debited Successfully");
                    connection.commit();
                } else {
                    System.out.println("Transaction Failed!");
                    connection.rollback();
                }
            } else {
                System.out.println("Invalid Pin!");
                connection.rollback();
            }
        } catch (SQLException e) {
            connection.rollback();
            e.printStackTrace();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void transfer_money(long sender_account_number) throws SQLException {
        scanner.nextLine();
        System.out.print("Enter Receiver Account Number: ");
        long receiver_account_number = scanner.nextLong();
        System.out.print("Enter Amount: ");
        BigDecimal amount = scanner.nextBigDecimal();
        scanner.nextLine();
        System.out.print("Enter Security Pin: ");
        String security_pin = scanner.nextLine();

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            System.out.println("Amount must be greater than zero!");
            return;
        }

        if(sender_account_number == receiver_account_number){
            System.out.println("Cannot transfer to your own account!");
            return;
        }

        try {
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT balance, security_pin FROM Accounts WHERE account_number = ?");
            preparedStatement.setLong(1, sender_account_number);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String stored_pin = resultSet.getString("security_pin");
                if(!BCrypt.checkpw(security_pin, stored_pin)){
                    System.out.println("Invalid Security Pin!");
                    connection.setAutoCommit(true);
                    return;
                }
                BigDecimal current_balance = resultSet.getBigDecimal("balance");
                if (amount.compareTo(current_balance) > 0) {
                    System.out.println("Insufficient Balance!");
                    connection.setAutoCommit(true);
                    return;
                }
                PreparedStatement debitPreparedStatement = connection.prepareStatement(
                        "UPDATE Accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?");
                PreparedStatement creditPreparedStatement = connection.prepareStatement(
                        "UPDATE Accounts SET balance = balance + ? WHERE account_number = ?");

                debitPreparedStatement.setBigDecimal(1, amount);
                debitPreparedStatement.setLong(2, sender_account_number);
                debitPreparedStatement.setBigDecimal(3, amount);

                creditPreparedStatement.setBigDecimal(1, amount);
                creditPreparedStatement.setLong(2, receiver_account_number);

                int rowsAffected1 = debitPreparedStatement.executeUpdate();
                int rowsAffected2 = creditPreparedStatement.executeUpdate();

                if (rowsAffected1 > 0 && rowsAffected2 > 0) {
                    System.out.println("Transaction Successful!");
                    System.out.println("Rs." + amount + " Transferred Successfully");
                    connection.commit();
                } else {
                    System.out.println("Transaction Failed");
                    connection.rollback();
                }
            } else {
                System.out.println("Invalid Security Pin!");
                connection.rollback();
            }
        } catch (SQLException e) {
            connection.rollback();
            e.printStackTrace();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void getBalance(long account_number) {
        scanner.nextLine();
        System.out.print("Enter Security Pin: ");
        String security_pin = scanner.nextLine();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT balance, security_pin FROM Accounts WHERE account_number = ?");
            preparedStatement.setLong(1, account_number);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next()){
                String stored_pin = resultSet.getString("security_pin");
                if(!BCrypt.checkpw(security_pin, stored_pin)){
                    System.out.println("Invalid Pin!");
                    return;
                }
                BigDecimal balance = resultSet.getBigDecimal("balance");
                System.out.println("Balance: " + balance);
            } else {
                System.out.println("Invalid Pin!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
