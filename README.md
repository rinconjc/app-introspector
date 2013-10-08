App Introspector
================

A small plugable library for Spring based web apps that provides programatic access to the application at runtime. This can be used for investigating or fixing problems during application runtime. It provides a simple REST like JSON interface, as well as a rich web console for inspecting and executing scripts (JavaScript) in the JVM running the application.


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

* A web console can be accessed at: `/spring/console` . See [App Introspector Console](https://github.com/julior/app-introspector/wiki/app-introspector-Console)) for more details.

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
* `dbquery(<dataSource>, <sql>)` : Executes a database query.
* `dbupdate(<dataSource>, <sql>)` : Executes a database update.
```


Adding App Introspector to your Spring application
--------------------------
1. Prerequisites:
	+ Spring 3.2 (for older versions checkout branch spring-3.0)
	+ PropertyPlaceholder configuration (or declare the controller and components explicitly in 3) e.g.
```
<context:property-placeholder location="classpath:/application.properties" />
```
1. Add the following dependency to your maven pom.xml (if not using maven, include the [jar file](https://oss.sonatype.org/content/repositories/snapshots/com/github/julior/app-introspector) directly in your project classpath)
```xml
<dependency>
  <groupId>com.github.julior</groupId>
  <artifactId>app-introspector</artifactId>
  <version>1.3.1-SNAPSHOT</version>
</dependency>	
```
The snapshot repository is at https://oss.sonatype.org/content/repositories/snapshots

1. Enable component scan in your Spring application context (or declare the controller explicitly)
```xml
<context:component-scan base-package="com.github.julior.appintrospector" />
```
if no PropertyPlaceholderConfiguration is defined in your Spring context, the above will fail, if so declare the components explicitly instead.
```xml
<bean id="appruntime" class="com.github.julior.appintrospector.AppRuntime">
	<property name="beanWhitelist" value="<empty or comma separated list of bean names to include>"/>
	<property name="beanBlacklist" value="<empty or comma separated list of bean names to exclude>"/>
</bean>
<bean id="appIntrospector" class="com.github.julior.appintrospector.AppIntrospector">
	<property name="fireBaseRef" value="<empty or your Firebase URL>"/>
	<property name="fireBaseSecret" value="<empty or your Firebase auth secret>"/>
	<property name="appName" value="<your app name>"/>
</bean>
```

3. Secure the /spring/* endpoints. This is a powerfull tool hence security restrictions have to be put accordingly. For instance with Spring Security module you can restrict the endpoints easily like this:
```xml
<security:http auto-config="true">
    <security:intercept-url pattern="/spring/run" access="ROLE_MASTER" />
    <security:intercept-url pattern="/spring/*" access="ROLE_SUPER" />
</security:http>
```

Configuration Properties
-----------------------------------------
The following properties can be specified as part of the PropertyPlaceholderConfiguration
* <b>app.introspector.beanblacklist</b> list of beans to exclude from introspection (comma separated regular expressions)
* <b>app.introspector.beanwhitelist</b> list of beans to include in introspection (comma separated regular expressions)
* <b>app-introspector-console.firebase-path</b> (Optional) Firebase URL for storing scripts in the App Console, if not provided it will store scripts in the browser Local Storage.
* <b>app-introspector-console.firebase-secret</b> The authentication secret for the Firebase URL.
* <b>app-introspector-console.appname</b> The application name for displaying in the App Console.

e.g.

```
# Application properties
# exclude beans from introspection
app.introspector.beanblacklist=org\\.springframework\\..*
# console properties
# firebase path for storing scripts
#app-introspector-console.firebase-path=https://<your-own-firebase>.firebaseio.com/
# firebase secret
#app-introspector-console.firebase-secret=<your firebase secret key>
# application name
app-introspector-console.appname=Test App
```



