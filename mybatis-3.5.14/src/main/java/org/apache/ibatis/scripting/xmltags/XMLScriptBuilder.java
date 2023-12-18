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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Clinton Begin
 */
public class XMLScriptBuilder extends BaseBuilder {

  private final XNode context;
  private boolean isDynamic;
  private final Class<?> parameterType;
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    // 将 configuration 配置类赋值到当前脚本构建器中
    super(configuration);
    // 将脚本内容赋值到当前脚本构建器中
    this.context = context;
    // 将请求参数赋值到当前脚本构建器中
    this.parameterType = parameterType;
    // 初始化 node 处理器，处理动态 SQL 语句。使用参考官方文档：https://mybatis.org/mybatis-3/zh/dynamic-sql.html
    initNodeHandlerMap();
  }

  private void initNodeHandlerMap() {
    // 用于处理<trim>节点，该节点用于修剪或添加SQL语句的部分。
    nodeHandlerMap.put("trim", new TrimHandler());
    // 用于处理<where>节点，该节点用于在生成动态SQL语句时添加WHERE子句。
    nodeHandlerMap.put("where", new WhereHandler());
    // 用于处理<set>节点，该节点用于在生成动态SQL语句时添加SET子句。
    nodeHandlerMap.put("set", new SetHandler());
    // 用于处理<foreach>节点，该节点用于在生成动态SQL语句时进行循环迭代。
    nodeHandlerMap.put("foreach", new ForEachHandler());
    // 用于处理<if>节点，该节点用于条件判断。
    nodeHandlerMap.put("if", new IfHandler());
    // 用于处理<choose>节点，该节点类似于Java中的switch-case语句，包含多个<when>和一个可选的<otherwise>。
    nodeHandlerMap.put("choose", new ChooseHandler());
    // 用于处理<when>节点，该节点是<choose>的子节点，表示一种条件分支。
    nodeHandlerMap.put("when", new IfHandler());
    // 用于处理<otherwise>节点，该节点是<choose>的子节点，表示<choose>中所有条件都不满足时的默认分支。
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    // 用于处理<bind>节点，该节点用于绑定表达式的值，并可以在后续SQL语句中引用。
    nodeHandlerMap.put("bind", new BindHandler());
  }

  public SqlSource parseScriptNode() {
    // 解析动态标签，带有${}符号的，带有<if>、<where>等标签的都属于动态标签
    // 带有#{}符号的，或者没有任何符号的SQL语句，则是静态标签
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource;
    // 如果是动态标签，则创建一个 DynamicSqlSource 进行包装
    if (isDynamic) {
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      // 没有动态标签，则通过 RawSqlSource 进行包装
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  /**
   * 案例代码：
   *   <select id="findByCondition" resultType="com.whoiszxl.entity.Member">
   *     select * from member
   *     <where>
   *       <if test="id != null">id = #{id}</if>
   *       <if test="username != null">AND username like #{username}</if>
   *       <if test="password != null">AND password like #{password}</if>
   *     </where>
   *   </select>
   *
   * @param node
   * @return
   */
  protected MixedSqlNode parseDynamicTags(XNode node) {
    List<SqlNode> contents = new ArrayList<>();
    // 获取 <select> <update> 等标签的子节点
    NodeList children = node.getNode().getChildNodes();
    // 将节点进行遍历
    for (int i = 0; i < children.getLength(); i++) {
      // 迭代获取节点，根据以上代码，此处会获取到三个节点，一个 SQL 的文本节点，一个 <where> 标签的元素节点，还有一个空字符串的文本节点
      XNode child = node.newXNode(children.item(i));
      // 此处判断当前节点是否是文本节点，如果是 SQL 语句，则直接走此流程
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        // 获取到文本本身
        String data = child.getStringBody("");
        // 封装成一个 TextSqlNode 文本SQL节点
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // 判断此 SQL 语句是否是一个动态 SQL，若是则将其添加到 contents 中，并将 isDynamic 标记为 true
        if (textSqlNode.isDynamic()) {
          contents.add(textSqlNode);
          isDynamic = true;
        } else {
          // 静态文本则直接通过 StaticTextSqlNode 包装后添加到 contents 中
          contents.add(new StaticTextSqlNode(data));
        }
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628
        // 如果是标签元素，如：<where> ，则需要进一步解析，比如说其中的 <if> 标签

        // 获取到标签的名称，如：<where> 获取到的 nodeName 为 where
        String nodeName = child.getNode().getNodeName();

        /**
         * 通过节点名称获取到对应的节点处理器
         * 参照：{@link org.apache.ibatis.scripting.xmltags.XMLScriptBuilder#initNodeHandlerMap()}
         */
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // 通过节点处理器进行标签的进一步处理
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    return new MixedSqlNode(contents);
  }

  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  private static class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      final String name = nodeToHandle.getStringAttribute("name");
      final String expression = nodeToHandle.getStringAttribute("value");
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析 where 标签和其子标签
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 构建出 WhereSqlNode 进行包装
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String collection = nodeToHandle.getStringAttribute("collection");
      Boolean nullable = nodeToHandle.getBooleanAttribute("nullable");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, nullable, index, item,
          open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析 <if> 标签和其子标签
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 将 <if> 标签中的 test 属性拿出来
      String test = nodeToHandle.getStringAttribute("test");
      // 将上述两个变量包装成一个 IfSqlNode 对象
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> whenSqlNodes = new ArrayList<>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes,
        List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
