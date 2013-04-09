package com.github.julior.springinspector;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.PostConstruct;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

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
public class SpringInspector implements BeanFactoryAware{
    private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(SpringInspector.class);


    private final static HttpHeaders JSON_HEADERS = new HttpHeaders(){{this.setContentType(MediaType.APPLICATION_JSON);}};
    private final static ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private final static ScriptEngine jsEngine = scriptEngineManager.getEngineByExtension("js");
    private static final String RES_PREFIX = "/spring/resource/";
    private ObjectMapper jsonMapper;

    static {
        try {
            Bindings globalScope = scriptEngineManager.getBindings();
            InputStreamReader reader = new InputStreamReader(SpringInspector.class.getResourceAsStream("/com/github/julior/springinspector/builtin-functions.js"));
            //globalScope.put("timer",new Timer());
            globalScope.putAll((Map<String, Object>)jsEngine.eval(reader));
            globalScope.put("_globalScope", globalScope);
            scriptEngineManager.setBindings(globalScope);
        } catch (Exception e) {
            LOGGER.error("Failed registering built-in functions", e);
        }
    }


    private DefaultListableBeanFactory beanFactory;

    @Value("${spring.inspector.beanwhitelist:}")
    private String[] beanWhitelist;

    @Value("${spring.inspector.beanblacklist:}")
    private String[] beanBlacklist;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if(beanFactory instanceof DefaultListableBeanFactory)
            this.beanFactory = (DefaultListableBeanFactory)beanFactory;
        else
            LOGGER.error("BeanFactory " + beanFactory + " doesn't implement DefaultListableBeanFactory. Spring inspector will be disabled.");

    }

    @PostConstruct
    public void postConstruct(){
        jsonMapper = new ObjectMapper();
        jsonMapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
    }

    @RequestMapping(value = "/beanNames", method = GET)
    public void getBeanNames(HttpServletRequest request, HttpServletResponse response){
        LOGGER.info("beanNames request from " + request.getRemoteHost() + " by " + request.getRemoteUser());
        writeJson(getContextBeanNames(beanFactory), response);
    }

    @RequestMapping(value = "/bean", method = GET)
    public void getBeanDetails(HttpServletRequest request, HttpServletResponse response, @RequestParam("id") String beanId
            , @RequestParam(value = "im", required = false, defaultValue = "false") boolean includeMethods){

        LOGGER.info("bean details for " + beanId + " request from " + request.getRemoteHost() + " by " + request.getRemoteUser());

        response.setDateHeader("Expires", new Date().getTime() + 24*60*60*1000); //expires in one day?
        response.setHeader("Cache-Control","public");

        if(isBeanAllowed(beanId)){
            BeanDefinition beanDefinition = getBeanDefinition(beanFactory, beanId);
            writeJson(getBeanInfo(beanDefinition, includeMethods), response);
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
            InputStream resourceAsStream = getClass().getResourceAsStream("/inspector-form.html");
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
        LOGGER.debug("Serving resource:" + resource);
        try {
            response.setDateHeader("Expires", System.currentTimeMillis()+3600000L);
            response.setHeader("Cache-Control","public");
            InputStream resourceAsStream = getClass().getResourceAsStream(resource);
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
        Bindings bindings = jsEngine.createBindings();
        for (Map.Entry<String, String> entry : command.getArguments().entrySet()) {
            if(!isBeanAllowed(entry.getValue()))
                throw new Exception("Bean '" + entry.getValue() +"' is not allowed.");
            bindings.put(entry.getKey(), beanFactory.getBean(entry.getValue()));
        }

        try {
            writeJson(jsEngine.eval(command.getScript(), bindings), response);
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


    private Collection<String> getContextBeanNames(ListableBeanFactory factory){
        List<String> beanNames= new ArrayList<String>();
        beanNames.addAll(Arrays.asList(factory.getBeanDefinitionNames()));
        if(factory instanceof DefaultListableBeanFactory){
            DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) factory;
            if(listableBeanFactory.getParentBeanFactory()!=null)
                beanNames.addAll(getContextBeanNames((ListableBeanFactory)listableBeanFactory.getParentBeanFactory()));
        }
        Collections.sort(beanNames);
        //apply blacklist/whitelist filtering
        return Collections2.filter(beanNames, new Predicate<String>() {
            @Override
            public boolean apply(String input) {
                return isBeanAllowed(input);
            }
        });
    }

    private Boolean isBeanAllowed(String name) {
        return (beanWhitelist==null || beanWhitelist.length==0 || matches(beanWhitelist, name))
                && (beanBlacklist==null || beanBlacklist.length==0 || !matches(beanBlacklist,name));
    }

    private Boolean matches(String[] patterns, String name){
        for(String pattern: patterns)
            if(name.matches(pattern)) return true;
        return false;
    }

    private BeanDefinition getBeanDefinition(BeanFactory factory, String beanId){
        if(factory instanceof DefaultListableBeanFactory){
            DefaultListableBeanFactory factory1 = (DefaultListableBeanFactory) factory;
            if(factory1.containsBeanDefinition(beanId))
                return factory1.getBeanDefinition(beanId);
            if(factory1.getParentBeanFactory()!=null)
                return getBeanDefinition(factory1.getParentBeanFactory(), beanId);
        }
        LOGGER.error("Bean " + beanId + " not found");
        return null;
    }

    private Map<String, Object> getBeanInfo(BeanDefinition beanDefinition, boolean includeMethods){
        if(beanDefinition==null) return null;
        Map<String, String> attrs = new HashMap<String, String>();

        for (PropertyValue property: beanDefinition.getPropertyValues().getPropertyValues()) {
            Object value = property.getValue();
            if(value instanceof BeanDefinitionHolder){
                BeanDefinitionHolder beanDef = ((BeanDefinitionHolder) value);
                value = beanDef.getBeanName()!=null?"ref " + beanDef.getBeanName() : beanDef.getBeanDefinition().getBeanClassName();
            }
            attrs.put(property.getName(), value==null?null : value.toString());
        }

        BeanDefinition beanClass = beanDefinition;
        while(beanClass.getBeanClassName() == null && beanClass.getParentName() != null){
            beanClass = getBeanDefinition(beanFactory, beanClass.getParentName());
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("class", beanClass.getBeanClassName());
        map.put("parent", beanDefinition.getParentName());
        map.put("scope", beanDefinition.getScope());
        map.put("properties", attrs);

        if(includeMethods){
            List<MethodInfo> methods = new ArrayList<MethodInfo>();

            try {
                if(beanClass.getBeanClassName()!=null){
                    Class<?> aClass = Class.forName(beanClass.getBeanClassName());
                    for(Method method : aClass.getMethods())
                        methods.add(MethodInfo.create(method));
                }
            } catch (Exception e) {
                LOGGER.error("Failed extracting methods of class", e);
            }
            map.put("methods", methods);
        }

        return map;

    }

}


