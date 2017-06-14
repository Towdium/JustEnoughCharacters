package towdium.je_characters.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
public class MailSender {
    @SuppressWarnings("SameParameterValue")
    public static void send(String title, String content) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket("smtp.163.com", 25);
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream os = socket.getOutputStream();
            socket.setSoTimeout(10000);
            br.readLine();

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String subject = format.format(new Date()) + ' ' + title;

            send(br, os, "HELO smtp", 250);
            send(br, os, "AUTH login", 334);
            send(br, os, "amVjX3JvYm90QDE2My5jb20=", 334);
            send(br, os, "MTIzcXdl", 235);
            send(br, os, "MAIL from:<jec_robot@163.com>", 250);
            send(br, os, "RCPT to:<jec_robot@163.com>", 250);
            send(br, os, "DATA", 354);
            send(br, os, "from:<jec_robot@163.com>", -1);
            send(br, os, "to:<jec_robot@163.com>", -1);
            send(br, os, "subject:" + subject, -1);
            send(br, os, "", -1);
            send(br, os, content, -1);
            send(br, os, ".", -1);
            send(br, os, "", 250);
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            if (socket != null)
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    static String send(BufferedReader br, OutputStream os, String cmd, int check) throws IOException {
        os.write((cmd + "\r\n").getBytes(Charset.forName("UTF-8")));
        String ret = "";
        if (check != -1) {
            ret = br.readLine();
            int i = Integer.parseInt(ret.split(" ")[0]);
            if (i != check)
                throw new IOException("Communication failed. Expected: " + check + ", Received: " + i);
        }
        return ret;
    }
}
