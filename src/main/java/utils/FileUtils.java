package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by david on 6/07/15.
 */
public class FileUtils {

    public static void create(String fileName, String data){

        try {
            //write converted json data to a file named "file.json"
            FileWriter writer = new FileWriter(fileName);
            writer.write(data);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static String readFile(String path, Charset encoding)
    {
        byte[] encoded = new byte[0];

        try {
            encoded = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String(encoded, encoding);
    }
}
