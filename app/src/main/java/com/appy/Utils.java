package com.appy;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils
{
    public static String join(String delimiter, Iterable<String> sequence)
    {
        StringBuilder result = new StringBuilder();
        for(String s : sequence)
        {
            if(result.length() != 0)
            {
                result.append(delimiter);
            }
            result.append(s);
        }
        return result.toString();
    }

    public static String hashFile(String path)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            InputStream fis = new FileInputStream(path);
            while (true)
            {
                int read = fis.read(buffer);
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
                else
                {
                    break;
                }
            }
            byte[] digestResult = digest.digest();
            return hexlify(digestResult);
        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            return "";
        }
    }

    public static String hexlify(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes){
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.toString();
    }
}
