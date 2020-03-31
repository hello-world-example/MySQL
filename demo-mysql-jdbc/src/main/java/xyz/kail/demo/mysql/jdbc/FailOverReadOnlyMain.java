package xyz.kail.demo.mysql.jdbc;

import lombok.Cleanup;

import java.sql.*;
import java.util.concurrent.TimeUnit;

public class FailOverReadOnlyMain {

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Class.forName("com.mysql.jdbc.Driver");

        @Cleanup Connection connection = DriverManager.getConnection(
                // failOverReadOnly=false 不管是什么都可以更新
                // mysql5以上的，设置autoReconnect=true 是无效的 只有4.x版本，起作用
                // https://blog.csdn.net/henryzhang2009/article/details/44175725
                "jdbc:mysql://localhost:3307/test?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&autoReconnect=true&failOverReadOnly=true",
                "root",
                "123456"
        );

        @Cleanup Statement statement = connection.createStatement();
        for (int i = 0; i < 100; i++) {
            try {
                int count = statement.executeUpdate("update T_TEST set name = " + i + " where ID = 1");
                System.out.println(count);
            } catch (Exception ex) {
                ex.printStackTrace();
            }


            TimeUnit.SECONDS.sleep(1);
        }
    }

}
