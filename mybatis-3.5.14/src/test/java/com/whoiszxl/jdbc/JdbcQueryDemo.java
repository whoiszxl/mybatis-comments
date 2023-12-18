package com.whoiszxl.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcQueryDemo {

  public static void main(String[] args) {
    // JDBC连接信息
    String jdbcUrl = "jdbc:mysql://106.13.7.251:3300/mybatis-test";
    String username = "root";
    String password = "123456";

    // SQL查询语句
    String sqlQuery = "SELECT * FROM member where id = ?";

    Connection connection = null;
    PreparedStatement preparedStatement = null;
    ResultSet resultSet = null;
    try {
      // 1. 加载数据库驱动程序
      Class.forName("com.mysql.cj.jdbc.Driver");

      // 2. 建立数据库连接
      connection = DriverManager.getConnection(jdbcUrl, username, password);
      // 3. 创建 PreparedStatement 对象，并设置查询参数（如果有）
      preparedStatement = connection.prepareStatement(sqlQuery);
      preparedStatement.setInt(1, 1);
      // 4. 执行查询，获取结果集
      resultSet = preparedStatement.executeQuery();

      // 5. 处理查询结果
      while (resultSet.next()) {
        // 获取每一行的数据
        int column1Value = resultSet.getInt("id");
        String column2Value = resultSet.getString("username");
        String column3Value = resultSet.getString("password");

        // 在这里处理每一行的数据
        System.out.println("id: " + column1Value + ", username: " + column2Value + ", password: " + column3Value);
      }

    } catch (ClassNotFoundException | SQLException e) {
      e.printStackTrace();
    }finally {
      // 关闭资源
      try {
        if (resultSet != null) {
          resultSet.close();
        }
        if (preparedStatement != null) {
          preparedStatement.close();
        }
        if (connection != null) {
          connection.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }
}
