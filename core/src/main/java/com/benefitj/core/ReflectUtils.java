package com.benefitj.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 反射工具
 */
public class ReflectUtils {


  public static final Predicate<Field> NOT_STATIC_FINAL = f ->
      !(Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()));

  public static final Predicate<Field> NOT_STATIC_FINAL_VOLATILE = f ->
      NOT_STATIC_FINAL.test(f) || !Modifier.isVolatile(f.getModifiers());

  /**
   * 判断是否被static和final修饰
   *
   * @param member Member类型
   * @return 返回判断结果
   */
  public static boolean isStaticFinal(Member member) {
    return isStaticFinal(member.getModifiers());
  }

  /**
   * 判断是否被static和final修饰
   *
   * @param modifiers 修饰符
   * @return 返回判断结果
   */
  public static boolean isStaticFinal(int modifiers) {
    return Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
  }

  /**
   * 判断是否被static或final修饰
   *
   * @param member Member类型
   * @return 返回判断结果
   */
  public static boolean isStaticOrFinal(Member member) {
    return isStaticOrFinal(member.getModifiers());
  }

  /**
   * 判断是否被static或final修饰
   *
   * @param modifiers 修饰符
   * @return 返回判断结果
   */
  public static boolean isStaticOrFinal(int modifiers) {
    return Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers);
  }

  /**
   * 是否被注解注释
   *
   * @param element     Class、Field、Method
   * @param annotations 注解
   * @return 返回是否被注释
   */
  public static boolean isAnnotationPresent(AnnotatedElement element, Class<? extends Annotation>... annotations) {
    for (Class<? extends Annotation> annotation : annotations) {
      if (!element.isAnnotationPresent(annotation)) {
        return false;
      }
    }
    return annotations.length > 0;
  }

  /**
   * 获取参数化类型
   *
   * @param clazz         实现类
   * @param typeParamName 泛型参数名
   * @param <T>           查找的泛型类型
   * @return 返回查找到的泛型类
   */
  public static <T> Class<T> getParameterizedTypeClass(Class<?> clazz, String typeParamName) {
    Class<T> realClass = null;
    Type type = clazz.getGenericSuperclass();
    if (type instanceof ParameterizedType) {
      realClass = (Class<T>) findParameterizedType(((ParameterizedType) type), typeParamName);
    } else {
      if (type == Proxy.class) {
        // 尝试从接口中获取
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> interfaceClazz : interfaces) {
          for (Type interfaceType : interfaceClazz.getGenericInterfaces()) {
            if (interfaceType instanceof ParameterizedType) {
              realClass = (Class<T>) findParameterizedType((ParameterizedType) interfaceType, typeParamName);
              if (realClass != null) {
                return realClass;
              }
            }
          }
        }
      }
    }
    return realClass;
  }

  /**
   * 查找参数化类型
   *
   * @param parameterizedType 类型对象
   * @param typeParamName     泛型类型名
   * @return 返回查找到的类
   */
  public static Type findParameterizedType(ParameterizedType parameterizedType, String typeParamName) {
    TypeVariable<? extends Class<?>>[] typeParameters = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
    for (int i = 0; i < actualTypeArguments.length; i++) {
      if (typeParameters[i].getName().equals(typeParamName)) {
        return actualTypeArguments[i];
      }
    }
    return null;
  }

  /**
   * 设置是否可以访问
   *
   * @param ao   可访问对象
   * @param flag 是否可以访问
   */
  public static void setAccessible(AccessibleObject ao, boolean flag) {
    if (ao != null) {
      ao.setAccessible(flag);
    }
  }

  /**
   * 迭代Class
   *
   * @param type        类
   * @param call        *
   * @param filter      过滤器 -> 返回true表示符合要求，需要处理
   * @param consumer    消费者
   * @param interceptor 拦截器 -> 返回true表示停止循环
   */
  public static <T> void foreach(final Class<?> type,
                                 final Function<Class<?>, T[]> call,
                                 final Predicate<T> filter,
                                 final Consumer<T> consumer,
                                 final Predicate<T> interceptor) {
    foreach(type, call, filter, consumer, interceptor, true);
  }

  /**
   * 迭代Class
   *
   * @param type        类
   * @param call        *
   * @param filter      过滤器 -> 返回true表示符合要求，需要处理
   * @param consumer    消费者
   * @param interceptor 拦截器 -> 返回true表示停止循环
   * @param superclass  是否继续迭代父类
   */
  public static <T> void foreach(final Class<?> type,
                                 final Function<Class<?>, T[]> call,
                                 final Predicate<T> filter,
                                 final Consumer<T> consumer,
                                 final Predicate<T> interceptor,
                                 boolean superclass) {
    if (type == null || type == Object.class) {
      return;
    }
    T[] ts = call.apply(type);
    for (T field : ts) {
      if (filter != null) {
        if (filter.test(field)) {
          consumer.accept(field);
        }
      } else {
        consumer.accept(field);
      }
      if (interceptor.test(field)) {
        return;
      }
    }

    if (superclass) {
      foreach(type.getSuperclass(), call, filter, consumer, interceptor, superclass);
    }
  }


  /**
   * 迭代 field
   *
   * @param type        类
   * @param filter      过滤器
   * @param consumer    消费者
   * @param interceptor 拦截器
   */
  public static void foreachField(Class<?> type,
                                  Predicate<Field> filter,
                                  Consumer<Field> consumer,
                                  Predicate<Field> interceptor) {
    foreachField(type, filter, consumer, interceptor, true);
  }

  /**
   * 迭代 field
   *
   * @param type        类
   * @param filter      过滤器
   * @param consumer    消费者
   * @param interceptor 拦截器
   * @param superclass  是否继续迭代父类
   */
  public static void foreachField(Class<?> type,
                                  Predicate<Field> filter,
                                  Consumer<Field> consumer,
                                  Predicate<Field> interceptor,
                                  boolean superclass) {
    foreach(type, Class::getDeclaredFields, filter, consumer, interceptor, superclass);
  }

  /**
   * 迭代字段，并返回处理后的结果集
   *
   * @param type     类型
   * @param filter   过滤器，过滤出匹配的字段
   * @param function 处理Field，并返回结果
   * @param <T>      类型
   * @return 返回处理后的结果集
   */
  public static <T> List<T> foreachFields(Class<?> type, Predicate<Field> filter, Function<Field, T> function) {
    final List<T> list = new LinkedList<>();
    foreachField(type
        , filter
        , f -> list.add(function.apply(f))
        , f -> false);
    return list;
  }

  /**
   * 是否为泛型字段: 如果字段不为空，判断getType() == getGenericType()
   *
   * @param field 字段
   * @return 如果相等，返回true
   */
  public static boolean isFieldTypeEquals(Field field) {
    return field != null && (field.getType() == field.getGenericType());
  }

  /**
   * 获取某个字段
   *
   * @param type    类型
   * @param matcher 匹配
   * @return 返回获取的字段对象
   */
  public static Field getField(Class<?> type, Predicate<Field> matcher) {
    AtomicReference<Field> field = new AtomicReference<>();
    foreachField(type, matcher, field::set, f -> field.get() != null);
    return field.get();
  }

  /**
   * 获取某个字段
   *
   * @param type  类型
   * @param field 字段
   * @return 返回获取的字段对象
   */
  @Nullable
  public static Field getField(Class<?> type, String field) {
    if (type != null && field != null
        && !field.isEmpty() && type != Object.class) {
      try {
        return type.getDeclaredField(field);
      } catch (NoSuchFieldException e) {/* ~ */}
      return getField(type.getSuperclass(), field);
    }
    return null;
  }

  /**
   * 获取字段的类型
   *
   * @param field 字段
   * @param obj   对象
   * @return 返回字段的类型
   */
  @Nullable
  public static Type getFieldOfType(Field field, Object obj) {
    Type genericType = field.getGenericType();
    if (genericType instanceof TypeVariable) {
      return getGenericType(obj.getClass(), 0);
    } else if (genericType instanceof ParameterizedType) {
      return getRawType((ParameterizedType) genericType);
    } else {
      return genericType;
    }
  }

  /**
   * 获取字段的值
   *
   * @param obj     原对象
   * @param matcher 匹配器
   * @param <V>     值类型
   * @return 返回获取到的值
   */
  public static <V> V getFieldValue(Object obj, Predicate<Field> matcher) {
    if (obj != null) {
      Field field = getField(obj.getClass(), matcher);
      return field != null ? getFieldValue(field, obj) : null;
    }
    return null;
  }

  /**
   * 获取字段的值
   *
   * @param field 字段
   * @param obj   原对象
   * @param <V>   值类型
   * @return 返回获取到的值
   */
  public static <V> V getFieldValue(Field field, Object obj) {
    try {
      setAccessible(field, true);
      return (V) field.get(obj);
    } catch (IllegalAccessException ignore) {/* ~ */}
    return null;
  }

  /**
   * 设置字段的值
   *
   * @param field 字段
   * @param obj   对象
   * @param value 值
   * @return 返回是否设置成功
   */
  public static boolean setFieldValue(Field field, Object obj, Object value) {
    if (field != null && obj != null) {
      try {
        setAccessible(field, true);
        field.set(obj, value);
        return true;
      } catch (IllegalAccessException ignore) {/* ~ */}
    }
    return false;
  }

  /**
   * 获取指定注解的全部属性
   *
   * @param klass           class
   * @param annotationClass 注解
   * @return 返回获取的全部属性
   */
  public static Field getFieldByAnnotation(Class<?> klass,
                                           Class<? extends Annotation> annotationClass) {
    List<Field> fieldList = getFieldByAnnotation(klass, annotationClass, true);
    return fieldList.isEmpty() ? null : fieldList.get(0);
  }

  /**
   * 获取指定注解的全部属性
   *
   * @param klass           class
   * @param annotationClass 注解
   * @return 返回获取的全部属性
   */
  public static List<Field> getFieldByAnnotation(Class<?> klass,
                                                 Class<? extends Annotation> annotationClass,
                                                 boolean first) {
    if (klass == null || klass == Object.class) {
      return Collections.emptyList();
    }

    final List<Field> fields = new LinkedList<>();
    for (Field field : klass.getDeclaredFields()) {
      if (field.isAnnotationPresent(annotationClass)) {
        fields.add(field);
        if (first) {
          return fields;
        }
      }
    }
    List<Field> nextFields = getFieldByAnnotation(klass.getSuperclass(), annotationClass, first);
    fields.addAll(nextFields);
    return fields;
  }


  /**
   * 迭代 method
   *
   * @param type        类
   * @param filter      过滤器
   * @param consumer    处理器
   * @param interceptor 拦截器
   * @param superclass  是否继续迭代父类
   */
  public static void foreachMethod(Class<?> type,
                                   Predicate<Method> filter,
                                   Consumer<Method> consumer,
                                   Predicate<Method> interceptor,
                                   boolean superclass) {
    foreach(type, Class::getDeclaredMethods, filter, consumer, interceptor, superclass);
  }

  /**
   * 迭代 method
   *
   * @param type        类
   * @param filter      过滤器
   * @param consumer    处理器
   * @param interceptor 拦截器
   */
  public static void foreachMethod(Class<?> type,
                                   Predicate<Method> filter,
                                   Consumer<Method> consumer,
                                   Predicate<Method> interceptor) {
    foreachMethod(type, filter, consumer, interceptor, true);
  }

  /**
   * 迭代方法，并返回处理的集合
   *
   * @param type     类型
   * @param filter   过滤器，过滤出匹配的方法
   * @param function 处理Method，并返回结果
   * @param <T>      类型
   * @return 返回处理后的结果集
   */
  public static <T> List<T> foreachMethods(Class<?> type, Predicate<Method> filter, Function<Method, T> function) {
    final List<T> list = new LinkedList<>();
    foreachMethod(type
        , filter
        , m -> list.add(function.apply(m))
        , m -> false);
    return list;
  }

  /**
   * 获取 method
   *
   * @param type    类
   * @param matcher 匹配器
   * @return 返回获取到的Method
   */
  public static Method getMethod(Class<?> type, @Nonnull Predicate<Method> matcher) {
    AtomicReference<Method> method = new AtomicReference<>();
    foreachMethod(type, matcher, method::set, m -> method.get() != null);
    return method.get();
  }

  /**
   * 获取 method
   *
   * @param type    类
   * @param matcher 匹配器
   * @return 返回 methods
   */
  public static LinkedList<Method> getMethods(Class<?> type, @Nullable Predicate<Method> matcher) {
    final LinkedList<Method> methods = new LinkedList<>();
    foreachMethod(type, matcher, methods::add, m -> false);
    return methods;
  }

  /**
   * 获取 get method
   *
   * @param type 类
   * @return 返回 methods
   */
  public static LinkedList<Method> getGetterMethods(Class<?> type) {
    final LinkedList<Method> methods = new LinkedList<>();
    foreachMethod(type, ReflectUtils::isGetterMethod, methods::add, m -> false);
    return methods;
  }

  /**
   * 获取 method
   *
   * @param type 类
   * @return 返回 methods
   */
  public static LinkedList<Method> getSetterMethods(Class<?> type) {
    final LinkedList<Method> methods = new LinkedList<>();
    foreachMethod(type, ReflectUtils::isSetterMethod, methods::add, m -> false);
    return methods;
  }

  /**
   * 是否为 get 方法
   *
   * @param m 方法
   * @return 返回是否为 get 方法
   */
  public static boolean isGetterMethod(Method m) {
    if (m.getReturnType() != void.class && m.getParameterCount() == 0) {
      String name = m.getName();
      return name.startsWith("get") || name.startsWith("is");
    }
    return false;
  }

  /**
   * 是否为 set 方法
   *
   * @param m 方法
   * @return 返回是否为 set 方法
   */
  public static boolean isSetterMethod(Method m) {
    return m.getParameterCount() == 1 && m.getName().startsWith("set");
  }

  /**
   * 调用方法
   *
   * @param obj    对象
   * @param method 方法
   * @param args   参数
   * @param <T>    返回值类型
   * @return 返回返回值
   */
  public static <T> T invoke(Object obj, Method method, Object... args) {
    try {
      setAccessible(method, true);
      return (T) method.invoke(obj, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 获取泛型参数，默认返回 null
   *
   * @return 如果是泛型类型，返回类上的泛型数组
   */
  public static Type[] getActualTypeArguments(Type genericSuperclass) {
    return getActualTypeArguments(genericSuperclass, null);
  }

  /**
   * 获取泛型参数
   *
   * @param defaultValues 默认值
   * @return 如果是泛型类型，返回类上的泛型数组
   */
  public static Type[] getActualTypeArguments(Type genericSuperclass, Type[] defaultValues) {
    if (genericSuperclass instanceof ParameterizedType) {
      Type[] arguments = ((ParameterizedType) genericSuperclass).getActualTypeArguments();
      return arguments != null ? arguments : defaultValues;
    }
    return defaultValues;
  }

  /**
   * 获取某一个位置的泛型参数类型
   *
   * @param index 参数位置
   * @return 返回对应的参数类型
   */
  public static <V> Class<V> getActualType(Type genericSuperclass, int index) {
    Type[] arguments = getActualTypeArguments(genericSuperclass);
    if (arguments != null && arguments.length > index) {
      return (Class<V>) arguments[index];
    }
    return null;
  }

  /**
   * 获取当前类的泛型类型
   *
   * @param clazz 当前类
   * @param index 获取的泛型类型
   * @return 返回对应的泛型类型
   */
  public static Type getGenericType(Class<?> clazz, int index) {
    Type[] params = getActualTypeArguments(clazz.getGenericSuperclass());

    if (index >= params.length || index < 0) {
      return Object.class;
    }

    if (!(params[index] instanceof Class)) {
      return Object.class;
    }

    return params[index];
  }

  /**
   * 获取原类型
   *
   * @param type 原类型
   * @return 返回对应的原类型或Null
   */
  @Nullable
  public static Type getRawType(ParameterizedType type) {
    return type != null ? type.getRawType() : null;
  }

  /**
   * 创建对象实例
   *
   * @param klass 类
   * @param args  参数
   * @param <T>   类型
   * @return 返回对象实例
   */
  public static <T> T newInstance(Class<T> klass, Object... args) {
    try {
      for (Constructor<?> c : klass.getConstructors()) {
        if (isParameterTypesMatch(c.getParameterTypes(), args)) {
          setAccessible(c, true);
          return (T) c.newInstance(args);
        }
      }
      if (args != null && args.length != 0) {
        throw new IllegalStateException("无法实例化\"" + klass + "\"的对象，没有对应参数的构造函数!");
      }
      return klass.newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 检查参数是否匹配
   *
   * @param parameterTypes 参数类型
   * @param args           参数
   * @return 返回校验结果
   */
  public static boolean isParameterTypesMatch(Class<?>[] parameterTypes, Object[] args) {
    if (parameterTypes != null && args != null) {
      if (parameterTypes.length != args.length) {
        return false;
      }
      for (int i = 0; i < parameterTypes.length; i++) {
        if (args[i] != null && !parameterTypes[i].isInstance(args[i])) {
          return false;
        }
      }
      return true;
    }
    return parameterTypes == null && args == null;
  }

}
