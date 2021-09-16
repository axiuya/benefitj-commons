package com.benefitj.netty.server.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;

import java.lang.reflect.Method;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Enumeration;
import java.util.Map;

import static io.netty.channel.ChannelOption.*;

/**
 * datagram config
 */
public class DatagramServerChannelConfig extends DefaultDatagramChannelConfig implements ServerSocketChannelConfig {

  private static final Object IP_MULTICAST_TTL;
  private static final Object IP_MULTICAST_IF;
  private static final Object IP_MULTICAST_LOOP;
  private static final Method GET_OPTION;
  private static final Method SET_OPTION;

  private volatile int backlog = NetUtil.SOMAXCONN;

  static {
    ClassLoader classLoader = PlatformDependent.getClassLoader(DatagramChannel.class);
    Class<?> socketOptionType = null;
    try {
      socketOptionType = Class.forName("java.net.SocketOption", true, classLoader);
    } catch (Exception e) {
      // Not Java 7+
    }
    Class<?> stdSocketOptionType = null;
    try {
      stdSocketOptionType = Class.forName("java.net.StandardSocketOptions", true, classLoader);
    } catch (Exception e) {
      // Not Java 7+
    }

    Object ipMulticastTtl = null;
    Object ipMulticastIf = null;
    Object ipMulticastLoop = null;
    Method getOption = null;
    Method setOption = null;
    if (socketOptionType != null) {
      try {
        ipMulticastTtl = stdSocketOptionType.getDeclaredField("IP_MULTICAST_TTL").get(null);
      } catch (Exception e) {
        throw new Error("cannot locate the IP_MULTICAST_TTL field", e);
      }

      try {
        ipMulticastIf = stdSocketOptionType.getDeclaredField("IP_MULTICAST_IF").get(null);
      } catch (Exception e) {
        throw new Error("cannot locate the IP_MULTICAST_IF field", e);
      }

      try {
        ipMulticastLoop = stdSocketOptionType.getDeclaredField("IP_MULTICAST_LOOP").get(null);
      } catch (Exception e) {
        throw new Error("cannot locate the IP_MULTICAST_LOOP field", e);
      }

      Class<?> networkChannelClass = null;
      try {
        networkChannelClass = Class.forName("java.nio.channels.NetworkChannel", true, classLoader);
      } catch (Throwable ignore) {
        // Not Java 7+
      }

      if (networkChannelClass == null) {
        getOption = null;
        setOption = null;
      } else {
        try {
          getOption = networkChannelClass.getDeclaredMethod("getOption", socketOptionType);
        } catch (Exception e) {
          throw new Error("cannot locate the getOption() method", e);
        }

        try {
          setOption = networkChannelClass.getDeclaredMethod("setOption", socketOptionType, Object.class);
        } catch (Exception e) {
          throw new Error("cannot locate the setOption() method", e);
        }
      }
    }
    IP_MULTICAST_TTL = ipMulticastTtl;
    IP_MULTICAST_IF = ipMulticastIf;
    IP_MULTICAST_LOOP = ipMulticastLoop;
    GET_OPTION = getOption;
    SET_OPTION = setOption;
  }

  public DatagramServerChannelConfig(NioDatagramServerChannel channel) {
    this(channel, new FixedRecvByteBufAllocator(1024 << 4));
  }

  public DatagramServerChannelConfig(NioDatagramServerChannel channel, RecvByteBufAllocator allocator) {
    super(channel, channel.javaSocket());
    this.setRecvByteBufAllocator(allocator);
  }

  private DatagramServerChannelConfig self() {
    return this;
  }

  public NioDatagramServerChannel channel() {
    return (NioDatagramServerChannel) channel;
  }

  @Override
  public int getTimeToLive() {
    return (Integer) getOption0(IP_MULTICAST_TTL);
  }

  @Override
  public DatagramChannelConfig setTimeToLive(int ttl) {
    setOption0(IP_MULTICAST_TTL, ttl);
    return this;
  }

  @Override
  public InetAddress getInterface() {
    NetworkInterface inf = getNetworkInterface();
    if (inf != null) {
      Enumeration<InetAddress> addresses = SocketUtils.addressesFromNetworkInterface(inf);
      if (addresses.hasMoreElements()) {
        return addresses.nextElement();
      }
    }
    return null;
  }

  @Override
  public DatagramServerChannelConfig setInterface(InetAddress interfaceAddress) {
    try {
      setNetworkInterface(NetworkInterface.getByInetAddress(interfaceAddress));
    } catch (SocketException e) {
      throw new ChannelException(e);
    }
    return this;
  }

  @Override
  public NetworkInterface getNetworkInterface() {
    return (NetworkInterface) getOption0(IP_MULTICAST_IF);
  }

  @Override
  public DatagramServerChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
    setOption0(IP_MULTICAST_IF, networkInterface);
    return this;
  }

  @Override
  public boolean isLoopbackModeDisabled() {
    return (Boolean) getOption0(IP_MULTICAST_LOOP);
  }

  @Override
  public DatagramServerChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
    setOption0(IP_MULTICAST_LOOP, loopbackModeDisabled);
    return this;
  }

  @Override
  protected void autoReadCleared() {
    channel().clearReadPending0();
  }

  private Object getOption0(Object option) {
    if (GET_OPTION == null) {
      throw new UnsupportedOperationException();
    } else {
      try {
        return GET_OPTION.invoke(channel().javaSocket(), option);
      } catch (Exception e) {
        throw new ChannelException(e);
      }
    }
  }

  private void setOption0(Object option, Object value) {
    if (SET_OPTION == null) {
      throw new UnsupportedOperationException();
    } else {
      try {
        SET_OPTION.invoke(channel().javaSocket(), option, value);
      } catch (Exception e) {
        throw new ChannelException(e);
      }
    }
  }

  /**
   * Gets the backlog value to specify when the channel binds to a local
   * address.
   */
  @Override
  public int getBacklog() {
    return backlog;
  }

  /**
   * Sets the backlog value to specify when the channel binds to a local
   * address.
   *
   * @param backlog
   */
  @Override
  public DatagramServerChannelConfig setBacklog(int backlog) {
    //super.setBacklog(backlog);
    return self();
  }

  /**
   * Gets the {@link StandardSocketOptions#SO_REUSEADDR} option.
   */
  @Override
  public boolean isReuseAddress() {
    try {
      return javaSocket().getReuseAddress();
    } catch (SocketException e) {
      throw new ChannelException(e);
    }
  }

  /**
   * Sets the {@link StandardSocketOptions#SO_REUSEADDR} option.
   *
   * @param reuseAddress
   */
  @Override
  public DatagramServerChannelConfig setReuseAddress(boolean reuseAddress) {
    try {
      javaSocket().setReuseAddress(reuseAddress);
    } catch (SocketException e) {
      throw new ChannelException(e);
    }
    return self();
  }

  /**
   * Gets the {@link StandardSocketOptions#SO_RCVBUF} option.
   */
  @Override
  public int getReceiveBufferSize() {
    try {
      return javaSocket().getReceiveBufferSize();
    } catch (SocketException e) {
      throw new ChannelException(e);
    }
  }

  /**
   * Gets the {@link StandardSocketOptions#SO_SNDBUF} option.
   *
   * @param receiveBufferSize
   */
  @Override
  public DatagramServerChannelConfig setReceiveBufferSize(int receiveBufferSize) {
    try {
      javaSocket().setReceiveBufferSize(receiveBufferSize);
      return self();
    } catch (SocketException e) {
      throw new ChannelException(e);
    }
  }

  /**
   * Sets the performance preferences as specified in
   * {@link ServerSocket#setPerformancePreferences(int, int, int)}.
   *
   * @param connectionTime
   * @param latency
   * @param bandwidth
   */
  @Override
  public DatagramServerChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    /* Not implemented yet */
    return self();
  }

  /**
   * Return all set {@link ChannelOption}'s.
   */
  @Override
  public Map<ChannelOption<?>, Object> getOptions() {
    if (PlatformDependent.javaVersion() >= 7) {
      return getOptions(super.getOptions(), NioChannelOption.getOptions(channel().javaChannel()));
    }
    return super.getOptions(super.getOptions(), SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG);
  }

  /**
   * Sets the configuration properties from the specified {@link Map}.
   *
   * @param options
   */
  @Override
  public boolean setOptions(Map<ChannelOption<?>, ?> options) {
    return super.setOptions(options);
  }

  /**
   * Return the value of the given {@link ChannelOption}
   *
   * @param option
   */
  @Override
  public <T> T getOption(ChannelOption<T> option) {
    if (option == SO_RCVBUF) {
      return (T) Integer.valueOf(getReceiveBufferSize());
    }
    if (option == SO_REUSEADDR) {
      return (T) Boolean.valueOf(isReuseAddress());
    }
    if (option == SO_BACKLOG) {
      return (T) Integer.valueOf(getBacklog());
    }

    return super.getOption(option);
  }

  /**
   * Sets a configuration property with the specified name and value.
   * To override this method properly, you must call the super class:
   * <pre>
   * public boolean setOption(ChannelOption&lt;T&gt; option, T value) {
   *     if (super.setOption(option, value)) {
   *         return true;
   *     }
   *
   *     if (option.equals(additionalOption)) {
   *         ....
   *         return true;
   *     }
   *
   *     return false;
   * }
   * </pre>
   *
   * @param option
   * @param value
   * @return {@code true} if and only if the property has been set
   */
  @Override
  public <T> boolean setOption(ChannelOption<T> option, T value) {
    validate(option, value);

    if (option == SO_RCVBUF) {
      setReceiveBufferSize((Integer) value);
    } else if (option == SO_REUSEADDR) {
      setReuseAddress((Boolean) value);
    } else if (option == SO_BACKLOG) {
      setBacklog((Integer) value);
    } else {
      return super.setOption(option, value);
    }

    return super.setOption(option, value);
  }

  /**
   * Returns the connect timeout of the channel in milliseconds.  If the
   * {@link Channel} does not support connect operation, this property is not
   * used at all, and therefore will be ignored.
   *
   * @return the connect timeout in milliseconds.  {@code 0} if disabled.
   */
  @Override
  public int getConnectTimeoutMillis() {
    return super.getConnectTimeoutMillis();
  }

  @Override
  public DatagramServerChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
    super.setConnectTimeoutMillis(connectTimeoutMillis);
    return self();
  }

  /**
   * @deprecated Use {@link MaxMessagesRecvByteBufAllocator} and
   * {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead()}.
   * <p>
   * Returns the maximum number of messages to read per read loop.
   * a {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object) channelRead()} event.
   * If this value is greater than 1, an event loop might attempt to read multiple times to procure multiple messages.
   */
  @Deprecated
  @Override
  public int getMaxMessagesPerRead() {
    return super.getMaxMessagesPerRead();
  }

  @Deprecated
  @Override
  public DatagramServerChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
    super.setMaxMessagesPerRead(maxMessagesPerRead);
    return self();
  }

  /**
   * Returns the maximum loop count for a write operation until
   * {@link WritableByteChannel#write(ByteBuffer)} returns a non-zero value.
   * It is similar to what a spin lock is used for in concurrency programming.
   * It improves memory utilization and write throughput depending on
   * the platform that JVM runs on.  The default value is {@code 16}.
   */
  @Override
  public int getWriteSpinCount() {
    return super.getWriteSpinCount();
  }

  @Override
  public DatagramServerChannelConfig setWriteSpinCount(int writeSpinCount) {
    super.setWriteSpinCount(writeSpinCount);
    return self();
  }

  /**
   * Returns {@link ByteBufAllocator} which is used for the channel
   * to allocate buffers.
   */
  @Override
  public ByteBufAllocator getAllocator() {
    return super.getAllocator();
  }

  @Override
  public DatagramServerChannelConfig setAllocator(ByteBufAllocator allocator) {
    super.setAllocator(allocator);
    return self();
  }

  /**
   * Returns {@link RecvByteBufAllocator} which is used for the channel to allocate receive buffers.
   */
  @Override
  public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
    return super.getRecvByteBufAllocator();
  }

  @Override
  public DatagramServerChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
    super.setRecvByteBufAllocator(allocator);
    return self();
  }

  /**
   * Returns {@code true} if and only if {@link ChannelHandlerContext#read()} will be invoked automatically so that
   * a user application doesn't need to call it at all. The default value is {@code true}.
   */
  @Override
  public boolean isAutoRead() {
    return super.isAutoRead();
  }

  @Override
  public DatagramServerChannelConfig setAutoRead(boolean autoRead) {
    super.setAutoRead(autoRead);
    return self();
  }

  /**
   * Returns {@code true} if and only if the {@link Channel} will be closed automatically and immediately on
   * write failure. The default is {@code true}.
   */
  @Override
  public boolean isAutoClose() {
    return super.isAutoClose();
  }

  /**
   * Sets whether the {@link Channel} should be closed automatically and immediately on write failure.
   * The default is {@code true}.
   *
   * @param autoClose
   */
  @Override
  public DatagramServerChannelConfig setAutoClose(boolean autoClose) {
    super.setAutoClose(autoClose);
    return self();
  }

  /**
   * Returns the high water mark of the write buffer.  If the number of bytes
   * queued in the write buffer exceeds this value, {@link Channel#isWritable()}
   * will start to return {@code false}.
   */
  @Override
  public int getWriteBufferHighWaterMark() {
    return super.getWriteBufferHighWaterMark();
  }

  @Override
  public DatagramServerChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
    super.setMessageSizeEstimator(estimator);
    return self();
  }

  /**
   * Returns the {@link WriteBufferWaterMark} which is used for setting the high and low
   * water mark of the write buffer.
   */
  @Override
  public WriteBufferWaterMark getWriteBufferWaterMark() {
    return super.getWriteBufferWaterMark();
  }

  @Override
  public DatagramServerChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
    super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
    return self();
  }

  /**
   * Returns the low water mark of the write buffer.  Once the number of bytes
   * queued in the write buffer exceeded the
   * {@linkplain #setWriteBufferHighWaterMark(int) high water mark} and then
   * dropped down below this value, {@link Channel#isWritable()} will start to return
   * {@code true} again.
   */
  @Override
  public int getWriteBufferLowWaterMark() {
    return super.getWriteBufferLowWaterMark();
  }

  @Override
  public DatagramServerChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
    super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
    return self();
  }

  /**
   * Returns {@link MessageSizeEstimator} which is used for the channel
   * to detect the size of a message.
   */
  @Override
  public MessageSizeEstimator getMessageSizeEstimator() {
    return super.getMessageSizeEstimator();
  }

  @Override
  public DatagramServerChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
    super.setWriteBufferWaterMark(writeBufferWaterMark);
    return self();
  }
}
