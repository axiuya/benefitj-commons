package com.benefitj.core.concurrent;

import com.benefitj.core.AttributeMap;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 可取消的调度
 *
 * @param <V>
 */
public class CancelableScheduledFuture<V> implements ScheduledFuture<V>, AttributeMap {

  private final ScheduledFuture<V> original;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();

  public CancelableScheduledFuture(ScheduledFuture<V> original) {
    this.original = original;
  }

  public ScheduledFuture<V> original() {
    return original;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return original().getDelay(unit);
  }

  @Override
  public int compareTo(Delayed o) {
    return original().compareTo(o);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return original().cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return original().isCancelled();
  }

  @Override
  public boolean isDone() {
    return original().isDone();
  }

  @Override
  public V get() {
    try {
      return original().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public V get(long timeout, TimeUnit unit) {
    try {
      return original().get(timeout, unit);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, Object> attributes() {
    return attributes;
  }
}
