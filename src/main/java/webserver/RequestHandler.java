package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

import javax.jws.soap.SOAPBinding;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    private HttpRequest httpRequest;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in,"UTF-8"));
            httpRequest = new HttpRequest(in);
            byte[] body = null;
/*
            String line = bufferedReader.readLine();
            log.debug("request line : {}", line);
            if(line == null) return;
            String url = HttpRequestUtils.getUrl(line);

            Map<String, String> headerMap = new HashMap<>();
            boolean logined = false;
            while(! line.equals("")) {
                log.debug("header : {} ", line );
                line = bufferedReader.readLine();
                String[] headerTokens = line.split(":");
                if(headerTokens.length == 2) {
                    headerMap.put(headerTokens[0], headerTokens[1].trim());
                }
                if(line.contains("Cookie")) {
                    logined = isLogin(line);
                }
            }
*/

        /*
            // Get 방식
            if(url.startsWith("/user/create?")) {
                int index = url.indexOf("?");
                String requestUrl =  url.substring(0, index);
                String paramUrl =  url.substring(index+1);
                Map<String, String> map = HttpRequestUtils.parseQueryString(paramUrl);
                User user = new User(map.get("userId"), map.get("password"), map.get("name"), map.get("email"));
                log.debug("requestUrl : {}", requestUrl);
                log.debug("paramUrl : {}", paramUrl);
                log.debug("User Info : {}", user);
                url = "/index.html";
            }
        */
            // Post 방식
            if(httpRequest.getUrl().startsWith("/user/create")) {
                User user = new User(httpRequest.getParam("userId"), httpRequest.getParam("password"), httpRequest.getParam("name"), httpRequest.getParam("email"));
                log.debug("user : {}", user);
                DataBase.addUser(user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
            } else if(httpRequest.getUrl().equals("/user/login")){
                log.debug("userId : {}, password : {}",  httpRequest.getParam("userId"), httpRequest.getParam("password"));
                User user = DataBase.findUserById(httpRequest.getParam("userId"));
                log.debug("Database User : {}", user);
                if(user == null) {
                    log.debug("User not exists");
                    responseResource(out, "/user/login_failed.html","text/html");
                } else if(user.getPassword().equals(httpRequest.getParam("password"))) {
                    log.debug("Login Success");
                    DataOutputStream dos = new DataOutputStream(out);
                    response302HeaderWithCookie(dos, "/user/list.html","logined=true");
                } else {
                    log.debug("Login failed");
                    responseResource(out, "/user/login_failed.html","text/html");
                }
            } else if(httpRequest.getUrl().equals("/user/list")){
                if(!httpRequest.isLogined()) {
                    responseResource(out, "/user/login.html","text/html");
                    return;
                }

                Collection<User> users = DataBase.findAll();
                StringBuilder sb = new StringBuilder();
                sb.append("<table border='1'>");
                for(User user : users) {
                    sb.append("<tr>");
                    sb.append("<td>" + user.getUserId() + "</td>");
                    sb.append("<td>" + user.getName() + "</td>");
                    sb.append("<td>" + user.getEmail() + "</td>");
                    sb.append("</tr>");
                }
                sb.append("</table>");
                body = sb.toString().getBytes();
                DataOutputStream dos = new DataOutputStream(out);
                response200Header(dos, body.length, "text/html");
                responseBody(dos, body);
            } else if(httpRequest.getUrl().endsWith(".css")) {
                responseResource(out, httpRequest.getUrl(),"text/css");
            } else {
                responseResource(out, httpRequest.getUrl(),"text/html");
            }

            } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private boolean isLogin(String line) {
        String[] headerTokens = line.split(":");
        Map<String, String> cookies = HttpRequestUtils.parseCookies(headerTokens[1].trim());
        String value = cookies.get("logined");
        if(value==null) return false;
        return Boolean.parseBoolean(value);
    }

    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: "+url+"\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String url, String Cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: "+url+"\r\n");
            dos.writeBytes("Set-Cookie: "+Cookie+"\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseResource(OutputStream out, String url, String type) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length, type);
        responseBody(dos,body);
    }
    private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String type) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: "+type+";charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
