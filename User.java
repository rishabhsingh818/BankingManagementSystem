package BankingManagementSystem;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class User {
    private Connection connection;
    private Scanner scanner;

    public User(Connection connection, Scanner scanner){
        this.connection = connection;
        this.scanner = scanner;
    }

    public void register(){
        scanner.nextLine();
        System.out.print("Full Name: ");
        String full_name = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        if(user_exist(email)) {
            System.out.println("User Already Exists for this Email Address!!");
            return;
        }
        String hashed_password = BCrypt.hashpw(password, BCrypt.gensalt());
        String register_query = "INSERT INTO User(full_name, email, password) VALUES(?, ?, ?)";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(register_query);
            preparedStatement.setString(1, full_name);
            preparedStatement.setString(2, email);
            preparedStatement.setString(3, hashed_password);
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Registration Successfull!");
            } else {
                System.out.println("Registration Failed!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String login(){
        scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        String login_query = "SELECT password FROM User WHERE email = ?";
        try{
            PreparedStatement preparedStatement = connection.prepareStatement(login_query);
            preparedStatement.setString(1, email);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next()){
                String hashed_password = resultSet.getString("password");
                if(BCrypt.checkpw(password, hashed_password)){
                    return email;
                }
            }
            return null;
        }catch (SQLException e){
            e.printStackTrace();
        }
        return null;
    }

    public boolean user_exist(String email){
        String query = "SELECT email FROM User WHERE email = ?";
        try{
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, email);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
        }catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }
}
