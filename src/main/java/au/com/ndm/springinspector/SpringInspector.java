package au.com.ndm.springinspector;

import au.com.ndm.common.MapBuilder;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStreamReader;
import java.io.Reader;
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
public class SpringInspector implements BeanFactoryAware{
    private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(SpringInspector.class);


    private final static HttpHeaders JSON_HEADERS = new HttpHeaders(){{this.setContentType(MediaType.APPLICATION_JSON);}};
    private final static ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private final static ScriptEngine jsEngine = scriptEngineManager.getEngineByExtension("js");

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

    @RequestMapping(value = "/spring/beanNames", method = GET)
    public ResponseEntity<List<String>> getBeanNames(){
        return jsonEntity(getContextBeanNames(beanFactory));
    }

    @RequestMapping(value = "/spring/bean", method = GET)
    public ResponseEntity<Object> getBeanDetails(@RequestParam("id") String beanId){
        BeanDefinition beanDefinition = getBeanDefinition(beanFactory, beanId);
        return jsonEntity((Object)getBeanInfo(beanDefinition));
    }

    @RequestMapping(value = "/spring/run", method = POST)
    public ResponseEntity<?> execScript(Reader reader){
        try {
            ScriptCommand scriptCommand = ScriptCommand.fromXml(reader);
            return executeCommand(scriptCommand);
        } catch (Exception e) {
            LOGGER.error("Failed parsing script command", e);
            return jsonEntity("Failed executing script:" + e.getMessage());
        }
    }

    private ResponseEntity<?> executeCommand(ScriptCommand command) throws Exception{
        Bindings bindings = jsEngine.createBindings();
        for (Map.Entry<String, String> entry : command.getArguments().entrySet()) {
            bindings.put(entry.getKey(), beanFactory.getBean(entry.getValue()));
        }

        try {
            return jsonEntity(jsEngine.eval(command.getScript(), bindings));
        } catch (ScriptException e) {
            LOGGER.error("Failed executing script:" + command ,e );
            throw new Exception(e);
        }
    }

    private <T> ResponseEntity<T> jsonEntity(T t){
        return new ResponseEntity<T>(t, JSON_HEADERS, HttpStatus.OK);
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


class ScriptCommand{
    private final static Logger LOGGER = Logger.getLogger(ScriptCommand.class);

    private Map<String, String> arguments;
    private String script;

    ScriptCommand(Map<String, String> arguments, String script) {
        this.arguments = arguments;
        this.script = script;
    }

    @Override
    public String toString() {
        return "ScriptCommand{" +
                "arguments=" + arguments +
                ", script='" + script + '\'' +
                '}';
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public String getScript() {
        return script;
    }

    static ScriptCommand fromXml(Reader reader) throws Exception{
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));

            Map<String, String> args = new HashMap<String, String>();
            //parse params
            NodeList nodes = (NodeList) xpath.evaluate("/command/vars/var", xml, XPathConstants.NODESET);
            for(int i=0; i<nodes.getLength(); i++){
                NamedNodeMap attrs = nodes.item(i).getAttributes();
                args.put(attrs.getNamedItem("name").getNodeValue(), attrs.getNamedItem("value").getNodeValue());
            }
            //parse script
            Node node = (Node)xpath.evaluate("/command/script", xml, XPathConstants.NODE);

            return new ScriptCommand(args, node.getTextContent());
        } catch (Exception e) {
            throw new Exception("Failed parsing command:" + e.getCause(), e);
        }
    }

    static ScriptCommand fromScript(Reader reader){

        return null;
    }
}

class MethodInfo{
    final String name;
    final String[] paramTypes;
    final String returnType;

    MethodInfo(String name, String[] paramTypes, String returnType) {
        this.name = name;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }

    static MethodInfo create(Method method) {
        List<String> paramTypes = new ArrayList<String>();

        for(Class<?> clazz : method.getParameterTypes()){
            paramTypes.add(clazz.getSimpleName());

        }
        Class<?> returnType = method.getReturnType();
        return new MethodInfo(method.getName(), paramTypes.toArray(new String[0]), returnType==null?"void" :returnType.getSimpleName());
    }


}
