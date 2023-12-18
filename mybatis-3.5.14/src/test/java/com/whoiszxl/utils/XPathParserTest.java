package com.whoiszxl.utils;

import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class XPathParserTest {
  @Test
  public void xPathTest() throws IOException {
    InputStream inputStream = Resources.getResourceAsStream("com/whoiszxl/mybatis-config.xml");
    XPathParser xPathParser = new XPathParser(inputStream, false, null, new XMLMapperEntityResolver());
    XNode xNode = xPathParser.evalNode("/configuration");
    System.out.println(xNode);
  }
}
