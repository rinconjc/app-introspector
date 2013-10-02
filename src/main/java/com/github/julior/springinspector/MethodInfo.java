package com.github.julior.springinspector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * User: rinconj
 * Date: 9/22/11 11:44 AM
 */
public class MethodInfo{
    private String name;
    private String[] paramTypes;
    private String returnType;

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

    public String getName() {
        return name;
    }

    public String[] getParamTypes() {
        return paramTypes;
    }

    public String getReturnType() {
        return returnType;
    }
}
