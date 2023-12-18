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

public class MyBatisTest {

  SqlSession sqlSession;

  String namespace = "com.whoiszxl.MemberMapper.";
  @Before
  public void init() throws IOException {
    // 1. 通过 Resources 这个工具类从制定的地址里面拿到对应的 XML 文件的输入流，本质上就是拿到了这个文件
    InputStream inputStream = Resources.getResourceAsStream("com/whoiszxl/mybatis-config.xml");
    //2. 接着通过 SqlSessionFactory 这个工厂对象读取到这个 XML 里面的配置
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, "test");
    //3. 再接着通过这个工厂拿到客户端和数据库服务的一个会话，通过会话就可以去执行对应的 SQL 了
    sqlSession = sqlSessionFactory.openSession();
  }


  /**
   * 测试条件查询
   * @throws IOException
   */
  @Test
  public void testSelectByCondition() {
    HashMap<Object, Object> params = new HashMap<>();
    params.put("id", "1");
    // 通过数据库会话执行一个查询操作，此处需要通过命名空间和查询id获取到对应的SQL语句
    Member member = sqlSession.selectOne(namespace + "findByCondition", params);
    System.out.println("member = " + member);

//    Member member2 = sqlSession.selectOne(namespace + "findByCondition", params);
//    System.out.println("member2 = " + member2);

    MemberMapper mapper = sqlSession.getMapper(MemberMapper.class);
    System.out.println(mapper.findAll());
  }

  /**
   * 测试查询单条记录
   * @throws IOException
   */
  @Test
  public void testSelectById() {
    // 通过数据库会话执行一个查询操作，此处需要通过命名空间和查询id获取到对应的SQL语句
    Member member = sqlSession.selectOne(namespace + "findById", 1);
    System.out.println("member = " + member);
  }

  /**
   * 测试查询所有记录
   * @throws IOException
   */
  @Test
  public void testSelectAll() {
    // 通过数据库会话执行一个查询操作，此处需要通过命名空间和查询id获取到对应的SQL语句
    List<Member> memberList = sqlSession.selectList(namespace + "findAll");
    memberList.forEach(System.out::println);
  }

  @Test
  public void testSelectAllByProxy() {
    MemberMapper mapper = sqlSession.getMapper(MemberMapper.class);
    List<Member> memberList = mapper.findAll();
    memberList.forEach(System.out::println);
  }

  @Test
  public void testInsertOne() {
    try{
      sqlSession.getConnection().setAutoCommit(false);
      Member member = new Member();
      member.setId(100);
      member.setUsername("刘备");
      member.setPassword("123456");
      int row = sqlSession.insert(namespace + "insertOne", member);
      System.out.println(String.format("新增了 %s 行记录", row));
    }catch (Exception e) {
      e.printStackTrace();
      sqlSession.rollback();
    }finally {
      sqlSession.commit();
    }
  }


  @Test
  public void testDeleteOne() {
    try{
      sqlSession.getConnection().setAutoCommit(false);
      int row = sqlSession.delete(namespace + "deleteOne", 100);
      System.out.println(String.format("删除了 %s 行记录", row));
    }catch (Exception e) {
      e.printStackTrace();
      sqlSession.rollback();
    }finally {
      sqlSession.commit();
    }
  }


  @Test
  public void testUpdateOne() {
    try{
      sqlSession.getConnection().setAutoCommit(false);
      Member member = new Member();
      member.setId(1);
      member.setUsername("姜伯约");
      member.setPassword("654321");
      int row = sqlSession.update(namespace + "updateOne", member);
      System.out.println(String.format("更新了 %s 行记录", row));
    }catch (Exception e) {
      e.printStackTrace();
      sqlSession.rollback();
    }finally {
      sqlSession.commit();
    }
  }

}
