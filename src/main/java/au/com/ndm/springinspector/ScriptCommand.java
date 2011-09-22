package au.com.ndm.springinspector;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: rinconj
 * Date: 9/22/11 11:04 AM
 */
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

    public static ScriptCommand fromXml(Reader reader) throws Exception{
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

    public static ScriptCommand fromScript(String script) throws Exception{

        Pattern beanRefPattern = Pattern.compile("\\$\\{?([^\\s\\;}]+)\\}?");
        Map<String, String> args = new HashMap<String, String>();
        Matcher matcher = beanRefPattern.matcher(script);
        StringBuffer buffer = new StringBuffer();
        int i=0;
        while (matcher.find()){
            i++;
            String beanId = matcher.group(1);
            String varName = genVarName(i);
            matcher.appendReplacement(buffer, varName);
            args.put(varName, beanId);
        }
        matcher.appendTail(buffer);

        return new ScriptCommand(args, buffer.toString());
    }

    public static ScriptCommand fromScript(Reader reader) throws Exception{
        //extract bean references ${beanid} from the script and replace with synthetic variables
        StringBuilder sb = null;
        try {
            sb = readContent(reader);
            return fromScript(sb.toString());
        } catch (IOException e) {
            throw new Exception("Failed reading script content", e);
        }
    }

    private static StringBuilder readContent(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line = null;
        while ((line = bufferedReader.readLine())!=null){
            sb.append(line);
        }
        return sb;
    }

    private static String genVarName(int i){
        return "_val_" + i;
    }
}
