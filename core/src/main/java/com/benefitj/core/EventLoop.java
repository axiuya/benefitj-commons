package com.benefitj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 事件循环
 */
public class EventLoop implements ScheduledExecutorService {

  private static final int PROCESSOR_SIZE = Runtime.getRuntime().availableProcessors();
  /**
   * 全局事件实例
   */
  private static final SingletonSupplier<EventLoop> MULTI_EVENT_LOOP
      = SingletonSupplier.of(() -> new GlobalEventLoop(PROCESSOR_SIZE, "-multi-", true));
  private static final SingletonSupplier<EventLoop> SINGLE_EVENT_LOOP
      = SingletonSupplier.of(() -> new GlobalEventLoop(1, "-single-", true));
  private static final SingletonSupplier<EventLoop> IO_EVENT_LOOP
      = SingletonSupplier.of(() -> new GlobalEventLoop(128, "-io-", true));

  private static final Logger logger = LoggerFactory.getLogger(EventLoop.class);

  /**
   * 多线程事件
   */
  public static EventLoop multi() {
    return MULTI_EVENT_LOOP.get();
  }

  /**
   * 单线程事件
   */
  public static EventLoop single() {
    return SINGLE_EVENT_LOOP.get();
  }

  /**
   * IO事件，128个线程
   */
  public static EventLoop io() {
    return IO_EVENT_LOOP.get();
  }

  private final ScheduledExecutorService executor;

  public EventLoop(int corePoolSize) {
    this(corePoolSize, false);
  }

  public EventLoop(int corePoolSize, boolean daemon) {
    this(corePoolSize, defaultThreadFactory(daemon));
  }

  public EventLoop(int corePoolSize, ThreadFactory threadFactory) {
    this.executor = Executors.newScheduledThreadPool(corePoolSize, threadFactory);
  }

  protected ScheduledExecutorService getExecutor() {
    return executor;
  }


  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return getExecutor().schedule(wrapped(command), delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return getExecutor().schedule(wrapped(callable), delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return getExecutor().scheduleAtFixedRate(wrapped(command), initialDelay, period, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return getExecutor().scheduleWithFixedDelay(wrapped(command), initialDelay, delay, unit);
  }

  @Override
  public void shutdown() {
    getExecutor().shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return getExecutor().shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return getExecutor().isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return getExecutor().isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return getExecutor().awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return getExecutor().submit(wrapped(task));
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return getExecutor().submit(wrapped(task), result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return getExecutor().submit(wrapped(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return getExecutor().invokeAll(wrapped(tasks));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    return getExecutor().invokeAll(wrapped(tasks), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return getExecutor().invokeAny(wrapped(tasks));
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return getExecutor().invokeAny(wrapped(tasks), timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    getExecutor().execute(wrapped(command));
  }

  /**
   * 包裹 Runnable
   *
   * @param task 任务
   * @return 返回结果
   */
  protected Runnable wrapped(Runnable task) {
    return () -> {
      try {
        task.run();
      } catch (Exception e) {
        logger.error("event_loop throws: "+ e.getMessage(), e);
        throw e;
      }
    };
  }

  /**
   * 包裹 Callable
   *
   * @param task 任务
   * @param <T>  返回类型
   * @return 返回结果
   */
  protected <T> Callable<T> wrapped(Callable<T> task) {
    return () -> {
      try {
        return task.call();
      } catch (Exception e) {
        logger.error("event_loop throws: "+ e.getMessage(), e);
        throw e;
      }
    };
  }

  /**
   * 包裹 Callable
   *
   * @param tasks 任务
   * @param <T>   返回类型
   * @return 返回结果
   */
  protected <T> Collection<? extends Callable<T>> wrapped(Collection<? extends Callable<T>> tasks) {
    return tasks.stream()
        .map(this::wrapped)
        .collect(Collectors.toList());
  }

  /**
   * 创建单线程的EventLoop
   */
  public static EventLoop newSingle(boolean daemon) {
    return newEventLoop(1, daemon);
  }

  /**
   * 创建EventLoop
   */
  public static EventLoop newCoreLoop(boolean daemon) {
    int coreSize = Runtime.getRuntime().availableProcessors();
    return newEventLoop(coreSize, daemon);
  }

  /**
   * 创建EventLoop
   */
  public static EventLoop newEventLoop(int corePoolSize, boolean daemon) {
    return new EventLoop(corePoolSize, defaultThreadFactory(daemon));
  }

  private static final AtomicInteger ID = new AtomicInteger(0);

  private static ThreadFactory defaultThreadFactory(boolean daemon) {
    String prefix = String.format("eventLoop%d-", ID.incrementAndGet());
    return new DefaultThreadFactory(prefix, "-T-", daemon);
  }

  static final class GlobalEventLoop extends EventLoop {

    private GlobalEventLoop(int corePoolSize, String suffix, boolean daemon) {
      super(corePoolSize, new DefaultThreadFactory("global-", suffix, daemon));
      if (!daemon) {
        ShutdownHook.register(super::shutdown);
      }
    }

    @Override
    public void shutdown() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
      throw new UnsupportedOperationException();
    }

  }

  public static void sleep(long duration) {
    sleep(duration, TimeUnit.MILLISECONDS);
  }

  public static void sleepSecond(long duration) {
    sleep(duration, TimeUnit.SECONDS);
  }

  public static void sleepMinute(long duration) {
    sleep(duration, TimeUnit.MINUTES);
  }

  public static void sleep(long duration, TimeUnit unit) {
    try {
      unit.sleep(duration);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String threadName() {
    return Thread.currentThread().getName();
  }

}
