package com.whoiszxl.entity;

public class Member {

  private Integer id1;

  private Integer id;

  private String username;

  private String password;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Integer getId1() {
    return id1;
  }

  public void setId1(Integer id1) {
    this.id1 = id1;
  }

  @Override
  public String toString() {
    return "Member{" +
      "id=" + id +
      ", username='" + username + '\'' +
      ", password='" + password + '\'' +
      '}';
  }
}
