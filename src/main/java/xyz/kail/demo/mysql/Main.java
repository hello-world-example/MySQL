package xyz.kail.demo.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");

        String url = "jdbc:mysql://localhost:3307/test?useSSL=false";
        try (Connection connection = DriverManager.getConnection(url, "kail", "1723")) {

            String querySQL = "insert into t_tt (NAME) VALUES (1)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(querySQL)) {
                for (int i = 0; i < 1_0000; i++) {
                    System.out.println(i + ":" + preparedStatement.executeUpdate());
                }
            }
        }

    }

}
