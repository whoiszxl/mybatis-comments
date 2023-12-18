/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建一个包装了 Configuration 和 id=1 参数的动态上下文对象
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 根据动态标签中的 test 属性条件构建 SQL，如此处走 findByCondition 的逻辑，
    // 则会构建出此SQL：select * from member WHERE id = #{id}
    rootSqlNode.apply(context);

    // 接着创建一个 SqlSource 构建者
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 获取到传入参数的 class 对象，此处为 java.util.HashMap
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 接着将 SqlSource 构建出来，此操作主要是将 SQL 语句中的  #{id} 替换成 ? 占位符，使其可被 JDBC 执行
    // 如：select * from member WHERE id = #{id}，需转换为：select * from member WHERE id = ?
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 构建出 BoundSql 对象
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 将 bindings 参数赋值到 BoundSql 中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
