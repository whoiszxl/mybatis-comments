package com.whoiszxl;

import com.whoiszxl.entity.Member;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class MyBatisCacheTest {

  SqlSession sqlSession;
  SqlSessionFactory sqlSessionFactory;

  String namespace = "com.whoiszxl.MemberMapper.";
  @Before
  public void init() throws IOException {
    // 1. 通过 Resources 这个工具类从制定的地址里面拿到对应的 XML 文件的输入流，本质上就是拿到了这个文件
    InputStream inputStream = Resources.getResourceAsStream("com/whoiszxl/mybatis-config.xml");
    //2. 接着通过 SqlSessionFactory 这个工厂对象读取到这个 XML 里面的配置
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, "test");
    //3. 再接着通过这个工厂拿到客户端和数据库服务的一个会话，通过会话就可以去执行对应的 SQL 了
    sqlSession = sqlSessionFactory.openSession();
  }


  /**
   * 缓存测试
   */
  @Test
  public void testFirstLevelCache() {
    HashMap<Object, Object> params = new HashMap<>();
    params.put("id", "1");

    Member member1 = sqlSession.selectOne(namespace + "findByCondition", params);
    System.out.println("member = " + member1);

    Member member2 = sqlSession.selectOne(namespace + "findByCondition", params);
    System.out.println("member2 = " + member2);

    System.out.println("member1 和 member2 是否是同一个对象: " + (member1 == member2));

    try{
      sqlSession.getConnection().setAutoCommit(false);
      Member member = new Member();
      member.setId(2);
      member.setPassword("654321");
      int row = sqlSession.update(namespace + "updateOne", member);
      System.out.println(String.format("更新了 %s 行记录", row));
    }catch (Exception e) {
      e.printStackTrace();
      sqlSession.rollback();
    }finally {
      sqlSession.commit();
    }

    Member member3 = sqlSession.selectOne(namespace + "findByCondition", params);

    System.out.println("member1 和 member3 是否是同一个对象: " + (member1 == member3));
  }


  @Test
  public void testSecondLevelCache() {
    HashMap<Object, Object> params = new HashMap<>();
    params.put("id", "1");

    Member member1 = sqlSession.selectOne(namespace + "findByCondition", params);
    System.out.println("member = " + member1);
    sqlSession.commit();

    SqlSession sqlSession2 = sqlSessionFactory.openSession();
    Member member2 = sqlSession2.selectOne(namespace + "findByCondition", params);
    System.out.println("member2 = " + member2);

    System.out.println("member1 和 member2 是否是同一个对象: " + (member1 == member2));

  }

}
