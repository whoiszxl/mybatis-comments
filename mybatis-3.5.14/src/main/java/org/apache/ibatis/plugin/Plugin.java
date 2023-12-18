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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  private final Object target;
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  public static Object wrap(Object target, Interceptor interceptor) {
    // 获取拦截器中拦截的接口和方法，比如说：
    //@Intercepts({
    //  @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
    //})
    // 那么拿到的接口 class 就是 StatementHandler，方法就是 StatementHandler.prepare(Connection, Integer)
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    // 获取到被代理对象的 class 对象
    Class<?> type = target.getClass();
    // 获取被代理对象中被拦截器拦截了的接口
    // 如上案例里的拦截接口是 StatementHandler.class，如果被代理对象是 CachingExecutor，那么就匹配不上
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    // 如果存在被拦截了的接口，那么就需要通过 JDK 动态代理基于被代理对象生成一个代理对象
    if (interfaces.length > 0) {
      // 创建代理对象，InvocationHandler 回调对象为当前 Plugin，也就是会回调到下面的 invoke 方法中
      return Proxy.newProxyInstance(type.getClassLoader(), interfaces, new Plugin(target, interceptor, signatureMap));
    }
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 获取拦截器中被拦截的方法集合
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      // 判断当前执行的方法是不是在被拦截的方法集合内
      if (methods != null && methods.contains(method)) {
        // 如果在集合内，则通过拦截器执行增强逻辑，此处 new Invocation() 的这个对象就是拦截器增强方法里的接收参数
        return interceptor.intercept(new Invocation(target, method, args));
      }
      // 执行原有代码的 method 逻辑
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException(
          "No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e,
            e);
      }
    }
    return signatureMap;
  }

  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      // 获取被代理类的所有接口
      for (Class<?> c : type.getInterfaces()) {
        // 判断被代理的接口是否在 signatureMap 中存在
        // signatureMap 就是插件类中 @Signature 注解里配置的类 class
        if (signatureMap.containsKey(c)) {
          // 存在说明被代理类需要生成对应的代理独享
          interfaces.add(c);
        }
      }
      // 获取到被代理类的父类再循环判断
      type = type.getSuperclass();
    }
    // Set 集合转数组返回，使用 Set 便于去重
    return interfaces.toArray(new Class<?>[0]);
  }

}
