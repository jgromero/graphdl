package es.ugr.ugritlab.graphs.gephi;

import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.gephi.filters.api.FilterController;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GephiLayoutManager {

    private ProjectController pc;
    private Workspace workspace;
    private GraphModel graphModel;
    private PreviewModel model;
    private ImportController importController;
    private FilterController filterController;
    private File uploads;

    private String DEFAULT_TEMP_GRAPH_NOLAYOUT;      // @todo Change to allow concurrent execution
    private String DEFAULT_TEMP_GRAPH_LAYOUT;
    private String DEFAULT_TEMP_GRAPH_LAYOUT_NOATT;

    private String tempFolderString;

    public GephiLayoutManager(String fileId, String tempFolderString) {
        String append = "";
        if(fileId != null) {
             append = "_" + fileId;
        }
        DEFAULT_TEMP_GRAPH_NOLAYOUT = "graph" + append + ".graphml";
        DEFAULT_TEMP_GRAPH_LAYOUT   = "graph-layout" + append + ".graphml";
        DEFAULT_TEMP_GRAPH_LAYOUT_NOATT   = "graph-layout-noatt" + append + ".graphml";

        this.tempFolderString = tempFolderString == null? "graphs" : tempFolderString;
    }

    public void init(ServletContext context) {
        //Init a project - and therefore a workspace
        pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        workspace = pc.getCurrentWorkspace();

        // AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
        graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        importController = Lookup.getDefault().lookup(ImportController.class);
        filterController = Lookup.getDefault().lookup(FilterController.class);
        // RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);

        // Set upload temp folder ('config' folder by default)
        if(context != null)
            uploads = new File(context.getRealPath("/" + tempFolderString));
        else
            uploads = new File("./" + tempFolderString);
    }

    public TinkerGraph doLayout(TinkerGraph tinkerGraph) throws IOException, ParserConfigurationException, SAXException, TransformerException {

        // Write temp file to be loaded by Gephi
        File graph_file_nolayout = new File(uploads, DEFAULT_TEMP_GRAPH_NOLAYOUT);
        tinkerGraph.io(IoCore.graphml()).writeGraph(graph_file_nolayout.toPath().toString());

        // Import .graphml file
        Container container;
        File file = new File(uploads, DEFAULT_TEMP_GRAPH_NOLAYOUT);
        container = importController.importFile(file);
        container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED);   // Force DIRECTED

        // Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);

        // Run YifanHuLayout for 100 passes - The layout always takes the current visible view  // @todo Extend to additional layouts --passed as parameters
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setOptimalDistance(100f);
        layout.setRelativeStrength(0.2f);
        layout.setInitialStep(20.0f);
        layout.setStepRatio(0.95f);
        layout.setConvergenceThreshold(0.0001f);
        layout.setBarnesHutTheta(1.2f);
        layout.setQuadTreeMaxLevel(10);
        layout.initAlgo();

        for (int i = 0; i < 100 && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();

        // Export .graphml file
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        File graph_file_layout_noatt = new File(uploads, DEFAULT_TEMP_GRAPH_LAYOUT_NOATT);
        ec.exportFile(graph_file_layout_noatt);

        // Reload .graphml into a TinkerGraph
        // (before: add edge ids and labels; otherwise IoCore.graphml will not read them. There is
        //  a patch here, but it is not working: https://github.com/gephi/gephi/issues/1585)
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(graph_file_layout_noatt);
        NodeList list = doc.getElementsByTagName("edge");
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            String id_value = null, label_value = null;
            NodeList dataKeys = node.getChildNodes();
            for(int j=0 ; j < dataKeys.getLength(); j++) {
                Node dataKey = dataKeys.item(j);
                if(dataKey.getNodeName().equals("data")) {
                    NamedNodeMap dataKeyAttributes = dataKey.getAttributes();
                    for(int k=0; k<dataKeyAttributes.getLength(); k++) {
                        Node dataKeyAttribute = dataKeyAttributes.item(k);
                        if(dataKeyAttribute.getNodeName().equals("key") && dataKeyAttribute.getNodeValue().equals("edgeid"))
                            id_value = dataKey.getFirstChild().getNodeValue();
                        if(dataKeyAttribute.getNodeName().equals("key") && dataKeyAttribute.getNodeValue().equals("labele")) {
                            label_value = dataKey.getFirstChild().getNodeValue();
                            dataKeyAttribute.setNodeValue("labelE");
                        }
                    }
                }
            }
            NamedNodeMap nodeAttributes = node.getAttributes();
            Attr id = doc.createAttribute("id");
            id.setValue(id_value==null?"":id_value);
            nodeAttributes.setNamedItem(id);
            Attr label = doc.createAttribute("label");
            label.setValue(label_value==null?"":label_value);
            nodeAttributes.setNamedItem(label);
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        File graph_file_layout = new File(uploads, DEFAULT_TEMP_GRAPH_LAYOUT);
        StreamResult result = new StreamResult(graph_file_layout.getAbsolutePath());
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);

        // Replace all labele by labelE
        Path path = Paths.get(graph_file_layout.getAbsolutePath());
        Charset charset = StandardCharsets.UTF_8;
        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll("labele", "labelE");
        Files.write(path, content.getBytes(charset));

        // Import into TinkerGraph
        TinkerGraph newGraph = TinkerGraph.open();
        newGraph.io(IoCore.graphml()).readGraph(graph_file_layout.toPath().toString());

        return newGraph;
    }
}
