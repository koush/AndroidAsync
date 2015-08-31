package com.koushikdutta.async.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class StreamUtility {
    public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) {
            // prepare the buffer to be drained
            buffer.flip();
            // write to the channel, may block
            dest.write(buffer);
            // If partial transfer, shift remainder down
            // If buffer is empty, same as doing recycle()
            buffer.compact();
        }
        // EOF will leave buffer in fill state
        buffer.flip();
        // make sure the buffer is fully drained.
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

	public static void copyStream(InputStream input, OutputStream output) throws IOException
	{
	    final ReadableByteChannel inputChannel = Channels.newChannel(input);
	    final WritableByteChannel outputChannel = Channels.newChannel(output);
	    // copy the channels
	    fastChannelCopy(inputChannel, outputChannel);
	}

    public static byte[] readToEndAsArray(InputStream input) throws IOException
    {
        DataInputStream dis = new DataInputStream(input);
        byte[] stuff = new byte[1024];
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int read = 0;
        while ((read = dis.read(stuff)) != -1)
        {
            buff.write(stuff, 0, read);
        }
        dis.close();
        return buff.toByteArray();
    }
    
	public static String readToEnd(InputStream input) throws IOException
	{
	    return new String(readToEndAsArray(input));
	}

    static public String readFile(String filename) throws IOException {
        return readFile(new File(filename));
    }

    static public String readFileSilent(String filename) {
        try {
            return readFile(new File(filename));
        }
        catch (IOException e) {
            return null;
        }
    }

    static public String readFile(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(file));
            input.readFully(buffer);
        } finally {
            closeQuietly(input);
        }
        return new String(buffer);
    }
    
    public static void writeFile(File file, String string) throws IOException {
        file.getParentFile().mkdirs();
        DataOutputStream dout = new DataOutputStream(new FileOutputStream(file));
        dout.write(string.getBytes());
        dout.close();
    }
    
    public static void writeFile(String file, String string) throws IOException {
        writeFile(new File(file), string);
    }
    
    public static void closeQuietly(Closeable... closeables) {
        if (closeables == null)
            return;
        for (Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // http://stackoverflow.com/a/156525/9636
                }
            }
        }
    }

    public static void eat(InputStream input) throws IOException {
        byte[] stuff = new byte[1024];
        while (input.read(stuff) != -1);
    }
}

