package com.github.julior.springinspector;

import org.junit.Test;

import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * User: rinconj
 * Date: 5/3/13 9:28 AM
 */
public class ScriptCommandTest {

    @Test
    public void testParser() throws Exception {
        ScriptCommand cmd = ScriptCommand.fromScript("var x=1\n" +
                "var b = ${bean1};\n" +
                "System.out.println(\"printing ${bean1}\"+b)");

        assertEquals(1,cmd.getArguments().size());
        assertEquals(true,cmd.getArguments().containsValue("bean1"));
        assertTrue(cmd.getScript().contains("${bean1}"));
        System.out.println(cmd.getScript());
    }

    @Test
    public void testMultilineString() throws Exception {
        ScriptCommand cmd = ScriptCommand.fromScript("var x = '''line1\nline2\nline3 \"word\" another'''");
        assertEquals("var x = \"line1\\nline2\\nline3 \\\"word\\\" another\"", cmd.getScript());
        System.out.println(cmd.getScript());
    }

    @Test
    public void testScriptFile() throws Exception {
        ScriptCommand cmd = ScriptCommand.fromScript(new InputStreamReader(getClass().getResourceAsStream("/test-script.js")));
        System.out.println(cmd.getScript());
    }
}
