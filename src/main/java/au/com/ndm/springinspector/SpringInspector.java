package au.com.ndm.springinspector;

import au.com.ndm.common.MapBuilder;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.annotation.PostConstruct;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class SpringInspector implements BeanFactoryAware{
    private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(SpringInspector.class);


    private final static HttpHeaders JSON_HEADERS = new HttpHeaders(){{this.setContentType(MediaType.APPLICATION_JSON);}};
    private final static ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private final static ScriptEngine jsEngine = scriptEngineManager.getEngineByExtension("js");
    private ObjectMapper jsonMapper;

    static {
        try {
            Bindings globalScope = scriptEngineManager.getBindings();
            InputStreamReader reader = new InputStreamReader(SpringInspector.class.getResourceAsStream("/au/com/ndm/springinspector/builtin-functions.js"));
            globalScope.putAll((Map<String, Object>)jsEngine.eval(reader));
            scriptEngineManager.setBindings(globalScope);
        } catch (Exception e) {
            LOGGER.error("Failed registering built-in functions", e);
        }
    }


    private DefaultListableBeanFactory beanFactory;

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

    @RequestMapping(value = "/spring/beanNames", method = GET)
    public void getBeanNames(HttpServletResponse response){
        writeJson(getContextBeanNames(beanFactory), response);
    }

    @RequestMapping(value = "/spring/bean", method = GET)
    public void getBeanDetails(HttpServletResponse response, @RequestParam("id") String beanId){
        BeanDefinition beanDefinition = getBeanDefinition(beanFactory, beanId);
        writeJson(getBeanInfo(beanDefinition), response);
    }

    @RequestMapping(value = "/spring/run", method = POST, params = "src=xml")
    public void execScript(HttpServletResponse response, Reader reader){
        try {
            ScriptCommand scriptCommand = ScriptCommand.fromXml(reader);
            executeCommand(scriptCommand, response);
        } catch (Exception e) {
            LOGGER.error("Failed parsing script command", e);
            writeJson("Failed executing script:" + e.getMessage(), response);
        }
    }

    @RequestMapping(value = "/spring/run", method = POST)
    public void execScript(HttpServletResponse response, @RequestBody String script){
        try {
            ScriptCommand scriptCommand = ScriptCommand.fromScript(script);
            executeCommand(scriptCommand, response);
        } catch (Exception e) {
            LOGGER.error("Failed parsing script command", e);
            writeJson("Failed executing script:" + e.getMessage(), response);
        }
    }


    private void executeCommand(ScriptCommand command, HttpServletResponse response) throws Exception{
        Bindings bindings = jsEngine.createBindings();
        for (Map.Entry<String, String> entry : command.getArguments().entrySet()) {
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
            LOGGER.error("Failed serialising object " +t, e);
        }
    }


    private List<String> getContextBeanNames(ListableBeanFactory factory){
        List<String> beanNames= new ArrayList<String>();
        beanNames.addAll(Arrays.asList(factory.getBeanDefinitionNames()));
        if(factory instanceof DefaultListableBeanFactory){
            DefaultListableBeanFactory listableBeanFactory = (DefaultListableBeanFactory) factory;
            if(listableBeanFactory.getParentBeanFactory()!=null)
                beanNames.addAll(getContextBeanNames((ListableBeanFactory)listableBeanFactory.getParentBeanFactory()));
        }
        return beanNames;
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

    private Map<String, Object> getBeanInfo(BeanDefinition beanDefinition){
        if(beanDefinition==null) return null;
        Map<String, String> attrs = new HashMap<String, String>();

        for (PropertyValue property: beanDefinition.getPropertyValues().getPropertyValues()) {
            Object value = property.getValue();
            attrs.put(property.getName(), value==null?null : value.toString());
        }

        List<MethodInfo> methods = new ArrayList<MethodInfo>();

        try {
            Class<?> aClass = Class.forName(beanDefinition.getBeanClassName());
            for(Method method : aClass.getMethods())
                methods.add(MethodInfo.create(method));
        } catch (Exception e) {
            LOGGER.error("Failed extracting methods of class", e);
        }

        return new MapBuilder<String, Object>().add("class", beanDefinition.getBeanClassName())
                .add("parent", beanDefinition.getParentName())
                .add("scope", beanDefinition.getScope())
                .add("properties", attrs)
                .add("methods", methods)
                .getMap();
    }

}


