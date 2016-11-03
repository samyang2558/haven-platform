package com.codeabovelab.dm.cluman.job;

import lombok.ToString;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 */
@JobBean(repeatable = true)
@ToString
public class ConcurrentScheduleJob implements Runnable {

    private static final AtomicInteger instances = new AtomicInteger();
    private final AtomicInteger counter = new AtomicInteger();

    private final Lock lock = new ReentrantLock();
    private static final AtomicInteger conflicts = new AtomicInteger();
    private final int instance;

    public ConcurrentScheduleJob() {
        instance = instances.incrementAndGet();
    }

    @Override
    public void run() {
        int num = counter.getAndIncrement();
        System.out.println(" *** " + instance + " start iteration " + num);
        boolean locked = lock.tryLock();
        try {
            if(!locked) {
                // when lock is fail then we assume that it conflict
                conflicts.incrementAndGet();
            }
            Thread.sleep(3_000L);
        } catch (InterruptedException e) {
            return;
        } finally {
            if(locked) {
                lock.unlock();
            }
            System.out.println(" * " + instance + " conflicts: \t" + getConflicts() + " instances: \t" + getInstances());
            System.out.println(" *** " + instance + " end iteration " + num);
        }
    }

    public static int getConflicts() {
        return conflicts.get();
    }

    public static int getInstances() {
        return instances.get();
    }
}
