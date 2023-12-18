package com.whoiszxl.plugins;

import com.whoiszxl.entity.Member;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Intercepts({
  @Signature(type = ResultSetHandler.class,method = "handleResultSets",args = {Statement.class})
})
public class PasswordDesensitizePlugin implements Interceptor {

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // 获取原始的查询结果
    Object result = invocation.proceed();

    // 判断查询结果是否是List
    if (result instanceof List) {
      // 强转为 List
      List resultList = (ArrayList) result;

      // 遍历结果，脱敏处理密码字段
      for (Object obj : resultList) {
        if (obj instanceof Member member) {
          // 脱敏处理密码字段（示例：将密码字段设置为 "12**56"）
          String originalPassword = member.getPassword();
          String firstTwoChars = originalPassword.substring(0, 2);
          String lastTwoChars = originalPassword.substring(originalPassword.length() - 2);
          String maskedPassword = firstTwoChars + "**" + lastTwoChars;
          member.setPassword(maskedPassword);
        }
        // 如果有其他结果类型，可以继续添加相应的处理逻辑
      }
    }

    return result;
  }
}
