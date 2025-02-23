/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.nio.ch.FileChannelImpl;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.Scanner;

import static java.lang.management.ManagementFactory.getRuntimeMXBean;

/**
 * Low level axcess to OS class.
 */
public class OS {
    private static final String HOST_NAME = getHostName0();
    private static final String USER_NAME = System.getProperty("user.name");
    private static final String TMP = System.getProperty("java.io.tmpdir");
    public static final String TARGET = System.getProperty("project.build.directory", TMP + "/target");
    private static final Logger LOG = LoggerFactory.getLogger(OS.class);
    private static final int MAP_RO = 0;
    private static final int MAP_RW = 1;
    private static final int MAP_PV = 2;
    private static final boolean IS64BIT = is64Bit0();
    private static final int PROCESS_ID = getProcessId0();
    private static final Memory MEMORY = getMemory();
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_LINUX = OS.startsWith("linux");
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_WIN = OS.startsWith("win");
    private static final int MAP_ALIGNMENT = isWindows() ? 64 << 10 : pageSize();

    public static String getHostName() {
        return HOST_NAME;
    }

    public static String getUserName() {
        return USER_NAME;
    }

    public static String getTarget() {
        return TARGET;
    }

    private static String getHostName0() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private static Memory getMemory() {
        Memory memory = null;
        try {
            Class<? extends Memory> java9MemoryClass = Class
                    .forName("software.chronicle.enterprise.core.Java9Memory")
                    .asSubclass(Memory.class);
            Method create = java9MemoryClass.getMethod("create");
            memory = (Memory) create.invoke(null);
        } catch (ClassNotFoundException expected) {
            // expected
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LOG.warn("Unable to load Java9MemoryClass", e);
        }
        if (memory == null)
            memory = UnsafeMemory.create();
        return memory;
    }

    /**
     * @return native memory accessor class
     */
    public static Memory memory() {
        return MEMORY;
    }

    /**
     * @return size of pages
     */
    public static int pageSize() {
        return memory().pageSize();
    }

    /**
     * Align a size of a memoyr mapping based on OS.
     *
     * @param size to align
     * @return size aligned
     */
    public static long mapAlign(long size) {
        int chunkMultiple = MAP_ALIGNMENT;
        return (size + chunkMultiple - 1) / chunkMultiple * chunkMultiple;
    }

    /**
     * @return is the JVM 64-bit
     */
    public static boolean is64Bit() {
        return IS64BIT;
    }

    private static boolean is64Bit0() {
        String systemProp;
        systemProp = System.getProperty("com.ibm.vm.bitmode");
        if (systemProp != null) {
            return "64".equals(systemProp);
        }
        systemProp = System.getProperty("sun.arch.data.model");
        if (systemProp != null) {
            return "64".equals(systemProp);
        }
        systemProp = System.getProperty("java.vm.version");
        return systemProp != null && systemProp.contains("_64");
    }

    public static int getProcessId() {
        return PROCESS_ID;
    }

    private static int getProcessId0() {
        String pid = null;
        final File self = new File("/proc/self");
        try {
            if (self.exists())
                pid = self.getCanonicalFile().getName();
        } catch (IOException ignored) {
            // ignored
        }
        if (pid == null)
            pid = getRuntimeMXBean().getName().split("@", 0)[0];
        if (pid == null) {
            int rpid = new Random().nextInt(1 << 16);
            LOG.warn("Unable to determine PID, picked a random number=" + rpid);
            return rpid;

        } else {
            return Integer.parseInt(pid);
        }
    }

    /**
     * This may or may not be the OS thread id, but should be unique across processes
     *
     * @return a unique tid of up to 48 bits.
     */
/*    public static long getUniqueTid() {
        return getUniqueTid(Thread.currentThread());
    }

    public static long getUniqueTid(Thread thread) {
        // Assume 48 bit for 16 to 24-bit process id and 16 million threads from the start.
        return ((long) getProcessId() << 24) | thread.getId();
    }*/
    public static boolean isWindows() {
        return IS_WIN;
    }

    private static boolean isMacOSX() {
        return IS_MAC;
    }

    private static boolean isLinux() {
        return IS_LINUX;
    }

    /**
     * @return the maximum PID.
     */
    public static long getPidMax() {
        if (isLinux()) {
            File file = new File("/proc/sys/kernel/pid_max");
            if (file.canRead())
                try {
                    return Maths.nextPower2(new Scanner(file).nextLong(), 1);
                } catch (FileNotFoundException e) {
                    LOG.warn("", e);
                }
        } else if (isMacOSX()) {
            return 1L << 24;
        }
        // the default.
        return 1L << 16;
    }

    /**
     * Map a region of a file into memory.
     * @param fileChannel to map
     * @param mode of access
     * @param start offset within a file
     * @param size of region to map.
     * @return the address of the memory mapping.
     * @throws IOException
     */
    public static long map(FileChannel fileChannel, FileChannel.MapMode mode, long start, long size) throws IOException {
        if (isWindows() && size > 4L << 30)
            throw new IllegalArgumentException("Mapping more than 4096 MiB is unusable on Windows, size = " + (size >> 20) + " MiB");
        try {
            return map0(fileChannel, imodeFor(mode), mapAlign(start), mapAlign(size));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            throw asAnIOException(e);
        }
    }

    static long map0(FileChannel fileChannel, int imode, long start, long size) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method map0 = fileChannel.getClass().getDeclaredMethod("map0", int.class, long.class, long.class);
        map0.setAccessible(true);
        return (Long) map0.invoke(fileChannel, imode, start, size);
    }

    /**
     * Unmap a region of memory.
     * @param address of the start of the mapping.
     * @param size of the region mapped.
     * @throws IOException if the unmap fails.
     */
    public static void unmap(long address, long size) throws IOException {
        try {
            Method unmap0 = FileChannelImpl.class.getDeclaredMethod("unmap0", long.class, long.class);
            unmap0.setAccessible(true);
            unmap0.invoke(null, address, mapAlign(size));
        } catch (Exception e) {
            throw asAnIOException(e);
        }
    }

    private static IOException asAnIOException(Throwable e) {
        if (e instanceof InvocationTargetException)
            e = e.getCause();
        if (e instanceof IOException)
            return (IOException) e;
        return new IOException(e);
    }

    static int imodeFor(FileChannel.MapMode mode) {
        int imode = -1;
        if (mode == FileChannel.MapMode.READ_ONLY)
            imode = MAP_RO;
        else if (mode == FileChannel.MapMode.READ_WRITE)
            imode = MAP_RW;
        else if (mode == FileChannel.MapMode.PRIVATE)
            imode = MAP_PV;
        assert (imode >= 0);
        return imode;
    }

    /**
     * Get the sapce actually used by a file.
     * @param filename to get the actual size of
     * @return size in bytes.
     */
    public static long spaceUsed(String filename) {
        return spaceUsed(new File(filename));
    }

    private static long spaceUsed(File file) {
        if (!isWindows()) {
            try {
                String du_k = run("du", "-ks", file.getAbsolutePath());
                return Long.parseLong(du_k.substring(0, du_k.indexOf('\t')));
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        return file.length();
    }

    private static String run(String... cmds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringWriter sw = new StringWriter();
        char[] chars = new char[1024];
        try (Reader r = new InputStreamReader(process.getInputStream())) {
            for (int len; (len = r.read(chars)) > 0; ) {
                sw.write(chars, 0, len);
            }
        }
        return sw.toString();
    }

    public static class Unmapper implements Runnable {
        private final long size;
        private final ReferenceCounted owner;
        private volatile long address;

        public Unmapper(long address, long size, ReferenceCounted owner) {
            owner.reserve();
            this.owner = owner;
            assert (address != 0);
            this.address = address;
            this.size = size;
        }

        public void run() {
            if (address == 0)
                return;

            try {
                unmap(address, size);
                address = 0;

                owner.release();
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
    }
}
