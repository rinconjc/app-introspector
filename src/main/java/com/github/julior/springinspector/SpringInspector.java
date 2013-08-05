package com.github.julior.springinspector;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.PostConstruct;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Date;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Created by IntelliJ IDEA.
 * User: rinconj
 * Date: 9/12/11
 * Time: 3:13 PM
 * To change this template use File | Settings | File Templates.
 */
@Controller
@RequestMapping("/spring/*")
public class SpringInspector{
    private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(SpringInspector.class);
    private final static HttpHeaders JSON_HEADERS = new HttpHeaders(){{this.setContentType(MediaType.APPLICATION_JSON);}};
    private static final String RES_PREFIX = "/spring/resource/";

    private ObjectMapper jsonMapper;

    @Autowired
    private SpringRuntime springRuntime;

    @PostConstruct
    public void postConstruct(){
        jsonMapper = new ObjectMapper();
        jsonMapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
    }

    @RequestMapping(value = "/beanNames", method = GET)
    public void getBeanNames(HttpServletRequest request, HttpServletResponse response){
        LOGGER.info("beanNames request from " + request.getRemoteHost() + " by " + request.getRemoteUser());
        writeJson(springRuntime.getContextBeanNames(), response);
    }

    @RequestMapping(value = "/bean", method = GET)
    public void getBeanDetails(HttpServletRequest request, HttpServletResponse response, @RequestParam("id") String beanId
            , @RequestParam(value = "im", required = false, defaultValue = "false") boolean includeMethods){

        LOGGER.info("bean details for " + beanId + " request from " + request.getRemoteHost() + " by " + request.getRemoteUser());

        response.setDateHeader("Expires", new Date().getTime() + 24*60*60*1000); //expires in one day?
        response.setHeader("Cache-Control","public");

        if(springRuntime.isBeanAllowed(beanId)){
            BeanDefinition beanDefinition = springRuntime.getBeanDefinition(beanId);
            writeJson(springRuntime.getBeanInfo(beanDefinition, includeMethods), response);
        }else
            writeJson("Bean inspection not allowed", response);
    }

    @RequestMapping(value = "/run", method = POST, params = "src=xml")
    public void execScript(HttpServletRequest request, HttpServletResponse response, Reader reader){

        try {
            ScriptCommand scriptCommand = ScriptCommand.fromXml(reader);
            LOGGER.info("script run request from " + request.getRemoteHost() + " by " + request.getRemoteUser() + " cmd: " + scriptCommand);
            executeCommand(scriptCommand, response);
        } catch (Exception e) {
            LOGGER.error("Failed parsing script command", e);
            writeJson("Failed executing script:" + e.getMessage(), response);
        }
    }

    @RequestMapping(value = "/run", method = POST)
    public void execScript(HttpServletRequest request, HttpServletResponse response, @RequestBody String script){
        try {
            ScriptCommand scriptCommand = ScriptCommand.fromScript(script);
            LOGGER.info("script run request from " + request.getRemoteHost() + " by " + request.getRemoteUser() + " cmd: " + scriptCommand);
            executeCommand(scriptCommand, response);
        } catch (Exception e) {
            LOGGER.error("Failed parsing script command", e);
            writeJson("Failed executing script:" + e.getMessage(), response);
        }
    }

    @RequestMapping(value = "/console")
    public void showConsole(HttpServletRequest request, HttpServletResponse response){
        try {
            InputStream resourceAsStream = getClass().getResourceAsStream("/spring-console.html");
            transfer(resourceAsStream, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            LOGGER.error("Failed returning resource data", e);
            response.setStatus(500);
        }
    }

    @RequestMapping(value = "/resource/**")
    public void getStaticResource(HttpServletRequest request, HttpServletResponse response){
        String servletPath = request.getServletPath();
        String resource = (servletPath.startsWith(RES_PREFIX)? servletPath.substring(RES_PREFIX.length()-1):servletPath);
        int pos = resource.lastIndexOf('_');
        if(pos>0){
            resource = resource.substring(0,pos)+"."+resource.substring(pos+1);
        }
        LOGGER.debug("Serving resource:" + resource);
        try {
            response.setDateHeader("Expires", System.currentTimeMillis()+7*24*60*60000L);
            response.setHeader("Cache-Control","public");
            InputStream resourceAsStream = getClass().getResourceAsStream(resource);
            if(resourceAsStream==null){
                response.setStatus(404);
                response.getWriter().write("Resource not available :" + resource);
                return;
            }
            transfer(resourceAsStream, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            LOGGER.error("Failed returning resource data", e);
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control","no-cache");
            response.setStatus(500);
        }
    }

    private void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len=in.read(buffer))>=0){
            out.write(buffer, 0, len);
        }
    }

    private void executeCommand(ScriptCommand command, HttpServletResponse response) throws Exception{
        try {
            writeJson(springRuntime.evalCommand(command), response);
        } catch (ScriptException e) {
            LOGGER.error("Failed executing script:" + command ,e );
            throw new Exception(e);
        }
    }

    private <T> ResponseEntity<T> jsonEntity(T t){
        return new ResponseEntity<T>(t, JSON_HEADERS, HttpStatus.OK);
    }

    private <T> void writeJson(T t,HttpServletResponse response){
        response.setContentType(MediaType.APPLICATION_JSON.toString());
        response.setStatus(HttpStatus.OK.value());
        try {
            jsonMapper.writeValue(response.getWriter(), t);
            response.flushBuffer();
        } catch (IOException e) {
            response.setDateHeader("Expires",0);
            response.setHeader("Cache-Control","no-cache");
            LOGGER.error("Failed serialising object " +t, e);
        }
    }

}


