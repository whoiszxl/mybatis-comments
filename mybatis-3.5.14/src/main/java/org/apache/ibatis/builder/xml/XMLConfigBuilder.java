/*
 *    Copyright 2009-2023 the original author or authors.
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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(Configuration.class, reader, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
      Properties props) {
    this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(Configuration.class, inputStream, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
      Properties props) {
    this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
      Properties props) {
    // 反射创建配置实例，并拿到别名注册器和类型处理注册器
    super(newConfig(configClass));
    // 错误上下文 resource 信息填充
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 设置变量信息，默认为 null
    this.configuration.setVariables(props);
    // 是否开始解析，默认为 false
    this.parsed = false;
    // 赋值环境信息，为字符串，值为 `<environment id="prod">` 中的id值，可以动态切换数据库环境
    this.environment = environment;
    // 指定解析器，默认为 XPath 的解析器
    this.parser = parser;
  }

  public Configuration parse() {
    // 判断是否已经开始解析了，防止重复解析
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 当解析开始时，需要将解析状态赋值为 true，表示已经开始解析了
    parsed = true;

    /**
     * 开始解析
     * parser.evalNode("/configuration"): 通过 XPath 解析器进行解析，拿到 <configuration></configuration> 标签里面所有的节点
     * 参考案例: {@link com.whoiszxl.utils.XPathParserTest#xPathTest()}
     *
     * parseConfiguration(): 将解析出来的 XPath 节点中的内容赋值到 Configuration 实例里面
     */
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析标签，标签参考：{@link mybatis-3.5.14/src/main/resources/org/apache/ibatis/builder/xml/mybatis-3-config.dtd}
   *
   * <!ELEMENT configuration (properties?, settings?, typeAliases?, typeHandlers?,
   * objectFactory?, objectWrapperFactory?, reflectorFactory?, plugins?, environments?,
   * databaseIdProvider?, mappers?)>
   *
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析 properties 标签
      propertiesElement(root.evalNode("properties"));
      // 解析 settings 标签
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfsImpl(settings);
      loadCustomLogImpl(settings);

      // 解析 typeAliases 标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析 plugins 标签
      pluginsElement(root.evalNode("plugins"));
      // 解析 objectFactory 标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 objectWrapperFactory 标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 reflectorFactory 标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 environments 标签
      environmentsElement(root.evalNode("environments"));
      // 解析 databaseIdProvider 标签
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 typeHandlers 标签
      typeHandlersElement(root.evalNode("typeHandlers"));
      // 解析 mappers 标签
      mappersElement(root.evalNode("mappers"));

    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException(
            "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value == null) {
      return;
    }
    String[] clazzes = value.split(",");
    for (String clazz : clazzes) {
      if (!clazz.isEmpty()) {
        @SuppressWarnings("unchecked")
        Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
        configuration.setVfsImpl(vfsImpl);
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode context) {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      if ("package".equals(child.getName())) {
        String typeAliasPackage = child.getStringAttribute("name");
        configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
      } else {
        String alias = child.getStringAttribute("alias");
        String type = child.getStringAttribute("type");
        try {
          Class<?> clazz = Resources.classForName(type);
          if (alias == null) {
            typeAliasRegistry.registerAlias(clazz);
          } else {
            typeAliasRegistry.registerAlias(alias, clazz);
          }
        } catch (ClassNotFoundException e) {
          throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
        }
      }
    }
  }


  /**
   * <plugins> 标签解析
   * 案例参考：
   *   <plugins>
   *     <plugin interceptor="com.whoiszxl.plugins.LoggerPlugin"></plugin>
   *     <plugin interceptor="com.whoiszxl.plugins.PasswordDesensitizePlugin"></plugin>
   *   </plugins>
   */
  private void pluginsElement(XNode context) throws Exception {
    // 判断 <plugins> 节点是否存在，存在则处理
    if (context != null) {
      // 遍历 <plugins> 节点下的 <plugin> 节点
      for (XNode child : context.getChildren()) {
        // 获取 <plugin> 标签里的 interceptor 属性，例如：com.whoiszxl.plugins.LoggerPlugin
        String interceptor = child.getStringAttribute("interceptor");
        // 获取 <plugin> 标签的 <property> 子标签
        Properties properties = child.getChildrenAsProperties();
        // 获取到 com.whoiszxl.plugins.LoggerPlugin 这个字符串对应的 Class 对象，并通过其定义的构造器进行构造初始化
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 将子标签 <property> 配置的属性设置到这个拦截器插件中
        interceptorInstance.setProperties(properties);
        // 将创建出来的拦截器实例添加到 Configuration 对象里的 interceptorChain 成员变量的 interceptors List 列表中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    Properties defaults = context.getChildrenAsProperties();
    String resource = context.getStringAttribute("resource");
    String url = context.getStringAttribute("url");
    if (resource != null && url != null) {
      throw new BuilderException(
          "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }
    if (resource != null) {
      defaults.putAll(Resources.getResourceAsProperties(resource));
    } else if (url != null) {
      defaults.putAll(Resources.getUrlAsProperties(url));
    }
    Properties vars = configuration.getVariables();
    if (vars != null) {
      defaults.putAll(vars);
    }
    parser.setVariables(defaults);
    configuration.setVariables(defaults);
  }

  private void settingsElement(Properties props) {
    configuration
        .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(
        AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(
        booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  private void environmentsElement(XNode context) throws Exception {
    // 如果节点信息不存在，直接返回
    if (context == null) {
      return;
    }
    // 如果没有指定是哪个环境，那么获取到默认的环境 <environments default="dev">
    if (environment == null) {
      environment = context.getStringAttribute("default");
    }

    // 遍历 <environments> 标签下的子节点
    for (XNode child : context.getChildren()) {
      // 获取到子节点的环境id
      String id = child.getStringAttribute("id");
      // 判断子节点的id是否和我们指定的环境信息id一致
      if (isSpecifiedEnvironment(id)) {
        // 获取到 transactionManager 标签配置的 JDBC 事务工厂
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        // 获取到 dataSource 标签下的数据库连接等配置信息
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        DataSource dataSource = dsFactory.getDataSource();
        // 构建一个 Environment 环境对象出来，其中存有事务工厂和数据源工厂
        Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
            .dataSource(dataSource);
        // 再将环境信息保存到 Configuration 对象里面
        configuration.setEnvironment(environmentBuilder.build());
        break;
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    String type = context.getStringAttribute("type");
    // awful patch to keep backward compatibility
    if ("VENDOR".equals(type)) {
      type = "DB_VENDOR";
    }
    Properties properties = context.getChildrenAsProperties();
    DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
        .newInstance();
    databaseIdProvider.setProperties(properties);
    Environment environment = configuration.getEnvironment();
    if (environment != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    // context内容：<transactionManager type="JDBC"/>
    if (context != null) {
      // 从节点中拿到 type 也就是 JDBC
      String type = context.getStringAttribute("type");
      // 看看当前节点下还有没有其他属性，如果有，则将这些属性设置到 Properties 对象中，方便后续设置到事务工厂里
      Properties props = context.getChildrenAsProperties();
      // 此处通过 type 也就是 JDBC 去别名注册器里面找到对应的JDBC事务工厂，此操作在 Configuration 的构造函数中赋值
      // typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
      // 拿到 JdbcTransactionFactory.class 后，获取到它的构造函数进行实例化
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 如果存在属性则赋值到工厂中
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    /**
     * 此处 context 的内容如下：
     * <dataSource type="POOLED">
     *     <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
     *     <property name="url" value="jdbc:mysql://106.13.7.251:3300/mybatis-prod?characterEncoding=utf-8"/>
     *     <property name="username" value="root"/>
     *     <property name="password" value="123456"/>
     * </dataSource>
     */
    if (context != null) {
      // 获取到 <dataSource type="POOLED"> 标签内的 type 值 POOLED
      String type = context.getStringAttribute("type");
      // 获取到 <dataSource> 标签下的数据库连接属性
      Properties props = context.getChildrenAsProperties();
      // 通过 resolveClass(type) 从别名注册器中获取到 POOLED 对应的 class 对象：PooledDataSourceFactory
      // 然后再拿到其对应的构造函数进行实例化，获取到数据库源的工厂对象
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 将数据库连接属性信息都保存到工厂对象里面
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlersElement(XNode context) {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      if ("package".equals(child.getName())) {
        String typeHandlerPackage = child.getStringAttribute("name");
        typeHandlerRegistry.register(typeHandlerPackage);
      } else {
        String javaTypeName = child.getStringAttribute("javaType");
        String jdbcTypeName = child.getStringAttribute("jdbcType");
        String handlerTypeName = child.getStringAttribute("handler");
        Class<?> javaTypeClass = resolveClass(javaTypeName);
        JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
        Class<?> typeHandlerClass = resolveClass(handlerTypeName);
        if (javaTypeClass != null) {
          if (jdbcType == null) {
            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
          } else {
            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
          }
        } else {
          typeHandlerRegistry.register(typeHandlerClass);
        }
      }
    }
  }

  private void mappersElement(XNode context) throws Exception {
    /**
     * context 内容如下:
     * <mappers>
     *     <mapper resource="com/whoiszxl/mapper/memberMapper.xml"/>
     * </mappers>
     */
    if (context == null) {
      return;
    }

    // 遍历 <mappers> 标签下的子节点
    for (XNode child : context.getChildren()) {
      // 如果是 <package> 标签
      if ("package".equals(child.getName())) {
        // 则从中拿到 <package name="com.whoiszxl"> 中的 name 值
        String mapperPackage = child.getStringAttribute("name");
        // 并将这个包下的所有Mapper接口添加到 Configuration 对象中
        configuration.addMappers(mapperPackage);
      } else {
        // 如果不是 <package> 标签，是 <mapper> 标签，则获取其中的 resource 属性
        // 属性值参考: com/whoiszxl/mapper/memberMapper.xml
        String resource = child.getStringAttribute("resource");
        // 获取 url 属性
        String url = child.getStringAttribute("url");
        // 获取 class 属性
        String mapperClass = child.getStringAttribute("class");

        // 如果 resource 存在，其他的不存在
        if (resource != null && url == null && mapperClass == null) {
          // 记录错误上下文的资源
          ErrorContext.instance().resource(resource);
          // 通过 Resources 工具从这个路径里拿到对应文件的输入流，此处也就是 memberMapper.xml 的输入流
          try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 通过 XPath 进行解析
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                configuration.getSqlFragments());
            mapperParser.parse();
          }
        } else if (resource == null && url != null && mapperClass == null) {
          // 如果 url 存在，其他的不存在
          ErrorContext.instance().resource(url);
          // 通过 url 获取到输入流后，再通过 XPath 进行解析
          try (InputStream inputStream = Resources.getUrlAsStream(url)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            mapperParser.parse();
          }
        } else if (resource == null && url == null && mapperClass != null) {
          // 如果 mapperClass 存在，其他的不存在，直接将 class 对象添加到 Configuration 实例中
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          configuration.addMapper(mapperInterface);
        } else {
          throw new BuilderException(
              "A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    // 如果没有指定环境id，则直接抛出异常
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    // 如果 <environment> 标签没有指定 id，也直接抛出异常
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    // 通过 equals 判断两个ID是否相等
    return environment.equals(id);
  }

  private static Configuration newConfig(Class<? extends Configuration> configClass) {
    try {
      // 通过反射的方式将 Configuration 实例化出来
      return configClass.getDeclaredConstructor().newInstance();
    } catch (Exception ex) {
      throw new BuilderException("Failed to create a new Configuration instance.", ex);
    }
  }

}
