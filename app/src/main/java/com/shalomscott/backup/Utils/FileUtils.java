package com.shalomscott.backup.Utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final String DIRECTORY_HASHES = "hashes";
    private static final String DIRECTORY_CONFIG = "config";

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static File getPublicFile(String filename) {
        return new File(Environment.getExternalStorageDirectory(), filename);
    }

    public static File getConfigFile(Context context, String filename) {
        return new File(context.getExternalFilesDir(DIRECTORY_CONFIG), filename);
    }

    public static String getMimeType(String loc) {
        String type = null;

        int i = loc.lastIndexOf('.');
        if (i > 0)
            type = loc.substring(i + 1);

        if (type != null)
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type);

        return type;
    }

    public static byte[] getFileBytes(File file) throws IOException {
        int size = (int) file.length(); // TODO: This limits files to roughly 2GB!!
        byte[] bytes = new byte[size];

        try (BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file))) {
            buf.read(bytes, 0, bytes.length);
        } catch (IOException e) {
            String eMsg = "Could not open " + file.getName() + " for reading";
            Log.e(TAG, eMsg, e);
            throw new IOException(eMsg);
        }

        return bytes;
    }

    public static boolean hasFileChanged(Context context, File file) throws NoSuchAlgorithmException, IOException {
        // Get MD5 instance
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            String eMsg = "Could not get MD5 algorithm";
            Log.e(TAG, eMsg, e);
            throw new NoSuchAlgorithmException(eMsg);
        }

        // Compute the file's current MD5 value
        byte[] hashBytes;
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            hashBytes = digest.digest();
        } catch (IOException e) {
            String eMsg = "Could not open " + file.getName() + " for reading";
            Log.e(TAG, eMsg, e);
            throw new IOException(eMsg);
        }

        // Compare the saved hash to the new one
        boolean retVal = false;
        File hashFile = new File(context.getExternalFilesDir(DIRECTORY_HASHES), file.getName() + ".md5");
        if (hashFile.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(hashFile)) {
                byte[] buffer = new byte[16]; // MD5 digest is 16B
                fileInputStream.read(buffer);
                if (!Arrays.equals(buffer, hashBytes)) {
                    retVal = true;
                }
            } catch (IOException e) {
                String eMsg = "Could not read hash file for " + file.getName();
                Log.e(TAG, eMsg, e);
                throw new IOException(eMsg);
            }
        } else {
            retVal = true;
        }

        // If the hash has changed (or if it never existed), save the new hash
        if (retVal) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(hashFile)) {
                fileOutputStream.write(hashBytes);
            } catch (IOException e) {
                String eMsg = "Could not write to hash file for " + file.getName();
                Log.e(TAG, eMsg, e);
                throw new IOException(eMsg);
            }
        }

        return retVal;
    }
}
