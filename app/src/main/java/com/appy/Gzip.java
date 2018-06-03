package com.appy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Gzip {

    public static byte[] compress(String data)
    {
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length());
            GZIPOutputStream gzip = null;

            gzip = new GZIPOutputStream(bos);

            gzip.write(data.getBytes());
            gzip.close();
            byte[] compressed = bos.toByteArray();
            bos.close();
            return compressed;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        throw new IllegalStateException("compress failed");
    }

    public static String decompress(byte[] compressed) {
        try
        {
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            GZIPInputStream gis = new GZIPInputStream(bis);
            BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            gis.close();
            bis.close();
            return sb.toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
            throw new IllegalStateException("compress failed");
        }
}