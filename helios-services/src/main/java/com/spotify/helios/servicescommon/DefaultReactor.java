/**
 * Copyright (C) 2013 Spotify AB
 */

package com.spotify.helios.servicescommon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Semaphore;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A reactor loop that collapses event updates and calls a provided callback.
 */
public class DefaultReactor extends InterruptingExecutionThreadService implements Reactor {

  private static final Logger log = LoggerFactory.getLogger(DefaultReactor.class);

  private final Semaphore semaphore = new Semaphore(0);

  private final String name;
  private final Callback callback;
  private final long timeoutMillis;

  /**
   * Create a reactor that calls the provided callback with the specified timeout interval.
   *
   * @param name          The reactor name.
   * @param callback      The callback to call.
   * @param timeoutMillis The timeout in millis after which the callback should be called even if
   *                      there has been no updates.
   */
  public DefaultReactor(final String name, final Callback callback, final long timeoutMillis) {
    super("Reactor(" + name + ")");
    this.name = name;
    this.callback = callback;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Create a reactor that calls the provided callback.
   *
   * @param name          The reactor name.
   * @param callback      The callback to call.
   */
  public DefaultReactor(final String name, final Callback callback) {
    this(name, callback, 0);
  }

  @Override
  public void signal() {
    semaphore.release();
  }

  @Override
  public Runnable signalRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        signal();
      }
    };
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      try {
        if (timeoutMillis == 0) {
          semaphore.acquire();
        } else {
          final boolean acquired = semaphore.tryAcquire(timeoutMillis, MILLISECONDS);
          if (!acquired) {
            log.debug("reactor timeout");
          }
        }
      } catch (InterruptedException e) {
        continue;
      }

      semaphore.drainPermits();

      try {
        callback.run();
      } catch (InterruptedException e) {
        log.debug("reactor interrupted: {}", name);
      } catch (Exception e) {
        if (e.getCause() instanceof ClosedByInterruptException ||
            e.getCause() instanceof InterruptedIOException ||
            e.getCause() instanceof InterruptedException) {
          log.debug("reactor interrupted: {}", name);
        } else {
          log.error("reactor runner threw exception: {}", name, e);
        }
      }
    }
  }
}
