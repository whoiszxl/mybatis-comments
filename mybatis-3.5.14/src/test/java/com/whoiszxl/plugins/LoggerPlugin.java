package com.whoiszxl.plugins;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.sql.Connection;
import java.util.Properties;

/**
 * mybatis 日志打印插件
 */
@Intercepts({
  @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class LoggerPlugin implements Interceptor {

  /**
   * 拦截增强
   */
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
    BoundSql boundSql = statementHandler.getBoundSql();
    String sql = boundSql.getSql();
    System.out.printf("【LoggerPlugin 插件增强】 SQL 打印: %s %n", sql);
    return invocation.proceed();
  }

  @Override
  public void setProperties(Properties properties) {
    Interceptor.super.setProperties(properties);
  }
}
