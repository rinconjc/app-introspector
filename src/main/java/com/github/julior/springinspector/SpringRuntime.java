package com.github.julior.springinspector;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.*;

/**
 * User: rinconj
 * Date: 4/30/13 11:57 AM
 */
@Component
public class SpringRuntime implements BeanFactoryAware {
    private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(SpringInspector.class);
    private final static ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private final static ScriptEngine jsEngine = scriptEngineManager.getEngineByExtension("js");

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

    public <T> T evalCommand(ScriptCommand command) throws Exception{
        Bindings bindings = jsEngine.createBindings();
        for (Map.Entry<String, String> entry : command.getArguments().entrySet()) {
            if(!isBeanAllowed(entry.getValue()))
                throw new Exception("Bean '" + entry.getValue() +"' is not allowed.");
            bindings.put(entry.getKey(), beanFactory.getBean(entry.getValue()));
        }

        try {
            return (T) jsEngine.eval(command.getScript(), bindings);
        } catch (ScriptException e) {
            LOGGER.error("Failed executing script:" + command ,e );
            throw new Exception(e);
        }
    }

    public Collection<String> getContextBeanNames(){
        return getContextBeanNames(beanFactory);
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
        Collection<String> filteredBeans = Collections2.filter(beanNames, new Predicate<String>() {
            @Override
            public boolean apply(String input) {
                return isBeanAllowed(input);
            }
        });
        //LOGGER.debug("bean names:" + Arrays.deepToString(filteredBeans.toArray()));
        return filteredBeans;
    }

    Boolean isBeanAllowed(String name) {
        return (beanWhitelist==null || beanWhitelist.length==0 || matches(beanWhitelist, name))
                && (beanBlacklist==null || beanBlacklist.length==0 || !matches(beanBlacklist,name));
    }

    private Boolean matches(String[] patterns, String name){
        for(String pattern: patterns)
            if(name.matches(pattern)) return true;
        return false;
    }

    BeanDefinition getBeanDefinition(String beanId){
        return getBeanDefinition(beanFactory, beanId);
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

    Map<String, Object> getBeanInfo(BeanDefinition beanDefinition, boolean includeMethods){
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

    public void setBeanWhitelist(String[] beanWhitelist) {
        this.beanWhitelist = beanWhitelist;
    }

    public void setBeanBlacklist(String[] beanBlacklist) {
        this.beanBlacklist = beanBlacklist;
    }
}

