package xyz.kail.demo.mysql.jdbc;

import lombok.Cleanup;

import java.sql.*;
import java.util.concurrent.TimeUnit;

public class AutoReconnectMain {

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Class.forName("com.mysql.jdbc.Driver");

        @Cleanup Connection connection = DriverManager.getConnection(
                // kill 后报 The last packet successfully received from the server was 1,025 milliseconds ago.  The last packet sent successfully to the server was 21 milliseconds ago.
                // 重试报 No operations allowed after statement closed.
//                "jdbc:mysql://localhost:3307/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull",
                // kill 后 同 autoReconnect=false
                // 重试自动重连，不会报 No operations allowed after statement closed.
                "jdbc:mysql://localhost:3307/mysql?autoReconnect=true",
                "root",
                "123456"
        );

        @Cleanup Statement statement = connection.createStatement();
        for (int i = 0; i < 100; i++) {
            try {
                @Cleanup ResultSet resultSet = statement.executeQuery("show variables like 'wait_timeout'");
                resultSet.next();
                System.out.println(resultSet.getInt(2));
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            }

            TimeUnit.SECONDS.sleep(5);
        }
    }

}
