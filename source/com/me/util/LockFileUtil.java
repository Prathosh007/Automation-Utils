package com.me.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class LockFileUtil {

    private File lockFile;
    private FileChannel channel;
    private FileLock lock;

    public LockFileUtil(String path) {
        this.lockFile = new File(path);
    }

    public boolean acquire() throws IOException {
        this.channel = new RandomAccessFile(lockFile, "rw").getChannel();
        this.lock = channel.tryLock();
        return this.lock != null;
    }

    public void release() throws IOException {
        if (this.lock != null) {
            this.lock.release();
        }
        if (this.channel != null) {
            this.channel.close();
        }
    }

    public void delete(){
        lockFile.delete();
    }

    public static boolean isFileLocked(File file) {
        if (!file.exists()) {
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {

            FileLock lock = channel.tryLock();
            if (lock != null) {
                lock.release();
                return false; // Not locked
            } else {
                return true;  // Locked by another process
            }

        } catch (Exception e) {
            // IOException / OverlappingFileLockException → locked
            return true;
        }
    }
}
