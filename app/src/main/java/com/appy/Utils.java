package com.appy;

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
}
