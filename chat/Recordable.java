package com.kwanii.chat;

import java.io.DataOutput;
import java.io.IOException;


/**
 *  Recordable interface
 */
public interface Recordable
{
    /**
     * Records using output and returns total record size
     *
     * @param output DataOutput
     * @return  The size recorded
     * @throws IOException
     */

    long record(DataOutput output) throws IOException;


    /**
     * Create space padding as long as the length
     *
     * @param length a length of padding characters
     * @return space characters
     */
    default String getPadding(int length)
    {
        if (length > 0)
        {
            StringBuilder padBuilder = new StringBuilder();

            for (int i = 0; i < length; i++)
            {
                padBuilder.append(" ");
            }
            return padBuilder.toString();
        }
        else
            return "";
    }

    default byte[] intToByte(int value)
    {
        byte[] bytes = new byte[Integer.BYTES];

        for (int i = 0; i < Integer.BYTES; i++)
            bytes[i] = (byte)(value >>> (Integer.SIZE - (1 - i) * 8));

        return bytes;
    }

    default byte[] longToByte(long value)
    {
        byte[] bytes = new byte[Long.BYTES];

        for (int i = 0; i < Long.BYTES; i++)
            bytes[i] = (byte)(value >>> (Long.SIZE - (1 - i) * 8));

        return bytes;
    }

    default int byteToInt(byte[] bytes)
    {
        int result = 0;

        for (int i = 0; i < Integer.BYTES; i++)
            result += ((int)bytes[i] & 0xff)  << (Integer.SIZE - (1 - i) * 8);

        return result;
    }

    default long byteToLong(byte[] bytes)
    {
        long result = 0;

        for (int i = 0; i < Long.BYTES; i++)
            result += ((long)bytes[i] & 0xffL)  << (Long.SIZE - (1 - i) * 8);

        return result;
    }
}
