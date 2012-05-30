Spring Inspector
================

A small plugable library for Spring based web applications that provides programatic access to the application at runtime. This can be used to diagnostic or fix problems at runtime. It provides a simple REST like JSON interface, as well as a rich user interface console for inspecting and executing scripts (JavaScript) on JVM running the application.


JSON Interface endpoints:
--------------------------

* Spring beans list: `GET /spring/beanNames`
This returns an array with the names of the Spring beans configured in the application. e.g

		['messageProcessor', 'messageListener', ...]

* Details of a specific bean: `GET /spring/bean?im=true&id=<beanId>`
This returns the properties and methods of the specified bean. e.g.

		{class:"au.com...GenericMessageListener"
		, properties:{messageLogger: <dbMessageLogger>, maxRetry:5, processBatchSize:2}
		, methods:[
			{name:'receive', returnType:void, paramTypes:[Message]},
			{name:'start', returnType:void, paramTypes:[]},
			{name:'stop', returnType:void, paramTypes:[]}
			...
			]
		}

* Execute script: `POST /spring/run`
This executes the script sent as a text/plain payload in the request. Only javascript is supported at the moment. The script can contain references to spring beans using ${} and the bean id, e.g.

		${messageListener}.stop();


Additionally, the script can invoke the following built-in functions:	 

* `toJava(<a javascript object>)`: Converts javascript literals to Java .e.g

		//return some data to client
		toJava({status:${messageLister}.getStatus, mailbox: ${messageListener}.getMailboxSize()});

* `exec(<some OS command>)`: Executes the given command as separate process in the operating system. e.g.

		//get memory usage
		exec("free -m");

* `getField(<object instance>, <field name>)` : Uses reflexion to access the given field in the given object instance. e.g

		//check a private field
		getField(${messageListener}, 'retryCount')


Adding Spring Inspector to your application
--------------------------
1. Add the following dependency to your maven pom.xml (if not using maven include the jar file directly in your project classpath)

		<dependency>
		  <groupId>au.com.ndm</groupId>
		  <artifactId>spring-inspector</artifactId>
		  <version>1.1-SNAPSHOT</version>
		</dependency>	

2. Enable component scan in your Spring application context

		<context:component-scan base-package="org.springinspector" use-default-filters="false" >
			<context:include-filter type="annotation" expression="org.springframework.stereotype.Controller"/>
		</context:component-scan>

3. Secure the /spring/* endpoints. This is a powerfull tool hence security restrictions have to be put accordingly. For instance with Spring Security module you can restrict the endpoints easily like this:

		<security:http auto-config="true">
		    <security:intercept-url pattern="/spring/run" access="ROLE_MASTER" />
		    <security:intercept-url pattern="/spring/*" access="ROLE_SUPER" />
		</security:http>
		



