package net.openhft.chronicle.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by daniel on 06/07/2015. A class to measure how much allocation there has been on a
 * thread. Useful in tests to make sure there has been little or even zreo allocation.
 */
public class AllocationMeasure {

    private static final Logger LOG = LoggerFactory.getLogger(AllocationMeasure.class);


    private static final String GET_THREAD_ALLOCATED_BYTES = "getThreadAllocatedBytes";
    private final String[] SIGNATURE = new String[]{long.class.getName()};
    private final String threadName = Thread.currentThread().getName();
    private final Object[] PARAMS = new Object[]{Thread.currentThread().getId()};
    private MBeanServer mBeanServer;
    private ObjectName name = null;
    private AtomicLong allocated = new AtomicLong();
    private long BYTES_USED_TO_MEASURE = 336;
    private long tid;

    public AllocationMeasure() {
        tid = Thread.currentThread().getId();
        try {
            name = new ObjectName(
                    ManagementFactory.THREAD_MXBEAN_NAME);
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } catch (MalformedObjectNameException e) {
            LOG.error("", e);
        }

        //calibrate
        for (int i = 0; i < 100; i++) {
            //run a few loops to allow for startup anomalies
            markAllocations();
        }
        long callibrate = threadAllocatedBytes();
        BYTES_USED_TO_MEASURE = threadAllocatedBytes() - callibrate;
        reset();
    }

    public void reset() {
        if (tid != Thread.currentThread().getId())
            throw new AssertionError("AllocationMeasure must not be used over more than 1 thread.");
        allocated.set(threadAllocatedBytes());
    }

    private long threadAllocatedBytes() {
        try {
            return (long) mBeanServer.invoke(
                    name,
                    GET_THREAD_ALLOCATED_BYTES,
                    PARAMS,
                    SIGNATURE
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Calculates the number of bytes allocated since the last reset. Reset is cause by a call to
     * reset(), markAllocations(), printAllocations().
     *
     * @return The number of bytes since the last reset.
     */
    public long markAllocations() {
        if (tid != Thread.currentThread().getId())
            throw new AssertionError("AllocationMeasure must not be used over more than 1 thread.");
        long mark1 = ((threadAllocatedBytes() - BYTES_USED_TO_MEASURE) - allocated.get());
        allocated.set(threadAllocatedBytes());
        return mark1;
    }

    public void printAllocations(CharSequence marker) {
        if (tid != Thread.currentThread().getId())
            throw new AssertionError("AllocationMeasure must not be used over more than 1 thread.");
        long mark1 = ((threadAllocatedBytes() - BYTES_USED_TO_MEASURE) - allocated.get());
        LOG.info(threadName + " allocated " + marker + ":" + mark1);
        allocated.set(threadAllocatedBytes());
    }


    public static void main(String[] args) {
        String TEST = "Test";
        AllocationMeasure allocationMeasure = new AllocationMeasure();

        for (int i = 0; i < 1000; i++) {
            allocationMeasure.reset();
            //allocationMeasure = new AllocationMeasure();

            long mark1 = allocationMeasure.markAllocations();


            if (mark1 > 0)
                System.out.println("m1:" + mark1);
        }
        allocationMeasure.printAllocations(TEST);
    }
}
