Spring Inspector
================

A small plugable library for Spring based web applications that provides programatic access to the application at runtime. This can be used to diagnostic or fix problems at runtime. It provides a simple REST like JSON interface, as well as a rich web console for inspecting and executing scripts (JavaScript) in the JVM running the application.


JSON Interface endpoints:
--------------------------

* Spring beans list: `GET /spring/beanNames`
This returns an array with the names of the Spring beans configured in the application. e.g
```json
['messageProcessor', 'messageListener', ...]
```

* Details of a specific bean: `GET /spring/bean?im=true&id=<beanId>`
This returns the properties and methods of the specified bean. e.g.
```json
{class:"au.com...GenericMessageListener"
, properties:{messageLogger: <dbMessageLogger>, maxRetry:5, processBatchSize:2}
, methods:[
	{name:'receive', returnType:void, paramTypes:[Message]},
	{name:'start', returnType:void, paramTypes:[]},
	{name:'stop', returnType:void, paramTypes:[]}
	...
	]
}
```

* Execute script: `POST /spring/run`
This executes the script sent as a text/plain payload in the request. Only javascript is supported at the moment. The script can contain references to spring beans using ${} and the bean id, e.g.
```javascript
${messageListener}.stop();
```

* A web console can be accessed at: `/spring/console` . See [Spring Inspector Console](https://github.com/julior/spring-inspector/wiki/Spring-inspector-Console)) for more details.

Additionally, the script can invoke the following built-in functions:	 

 * `toJava(<a javascript object>)`: Converts javascript literals to Java .e.g
```javascript
//return some data to client
toJava({status:${messageLister}.getStatus, mailbox: ${messageListener}.getMailboxSize()});
```

 * `exec(<some OS command>)`: Executes the given command as separate process in the operating system. e.g.
```javascript
//get memory usage
exec("free -m");
```		

 * `getField(<object instance>, <field name>)` : Uses reflexion to access the given field in the given object instance. e.g
```javascript
//check a private field
getField(${messageListener}, 'retryCount')
```


Adding Spring Inspector to your application
--------------------------
1. Add the following dependency to your maven pom.xml (if not using maven include the jar file directly in your project classpath)
```xml
<dependency>
  <groupId>com.github.julior</groupId>
  <artifactId>spring-inspector</artifactId>
  <version>1.3-SNAPSHOT</version>
</dependency>	
```

2. Enable component scan in your Spring application context (or declare the controller explicitly)
```xml
<context:component-scan base-package="com.github.julior.springinspector" use-default-filters="false" >
	<context:include-filter type="annotation" expression="org.springframework.stereotype.Controller"/>
	<context:include-filter type="annotation" expression="org.springframework.stereotype.Component"/>
</context:component-scan>
```

3. Secure the /spring/* endpoints. This is a powerfull tool hence security restrictions have to be put accordingly. For instance with Spring Security module you can restrict the endpoints easily like this:
```xml
<security:http auto-config="true">
    <security:intercept-url pattern="/spring/run" access="ROLE_MASTER" />
    <security:intercept-url pattern="/spring/*" access="ROLE_SUPER" />
</security:http>
```




