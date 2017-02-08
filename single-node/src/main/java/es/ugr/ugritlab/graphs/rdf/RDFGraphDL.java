package es.ugr.ugritlab.graphs.rdf;

/**
 * Created by jgomez on 08/02/17.

 * GraphDL to TinkerPop parser.
 */

import com.google.common.collect.*;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.openrdf.model.Statement;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryEvaluationException;

import java.util.*;

public class RDFGraphDL {

    /* Wrapped TinkerGraph */
    private TinkerGraph graph;
    private int nodeCount, edgeCount;

    /* String constants of the GraphDL ontology */
    private static final String RDF_TYPE_URI   = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    public static final String NODE_CLASS_URI = "http://ugritlab.ugr.es/graphdl#Node";
    public static final String EDGE_CLASS_URI = "http://ugritlab.ugr.es/graphdl#Edge";
    public static final String ATTRIBUTE_VALUE_CLASS_URI = "http://ugritlab.ugr.es/graphdl#AttributeValue";
    public static final String SOURCE_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#source";
    public static final String TARGET_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#target";
    public static final String FOR_ATTRIBUTE_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#forAttribute";
    public static final String VAL_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#val";
    public static final String HAS_ATTRIBUTE_VALUE_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#hasAttributeValue";

    /* Correspondences between GraphDL property URIs and GraphML property names:
    *  color, r, g, b, x, y, size */
    public static final String ID_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#id";
    public static final String ID_PROPERTY_GRAPHML = "id_rdf";

    public static final String LABEL_PROPERTY_URI = "http://www.w3.org/2000/01/rdf-schema#label";
    public static final String LABEL_PROPERTY_GRAPHML = "label";

    public static final String COLOR_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#color";
    public static final String COLOR_PROPERTY_GRAPHML = "color";

    public static final String R_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#r";
    public static final String R_PROPERTY_GRAPHML = "r";

    public static final String G_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#g";
    public static final String G_PROPERTY_GRAPHML = "g";

    public static final String B_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#b";
    public static final String B_PROPERTY_GRAPHML = "b";

    public static final String X_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#x";
    public static final String X_PROPERTY_GRAPHML = "x";

    public static final String Y_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#y";
    public static final String Y_PROPERTY_GRAPHML = "y";

    public static final String SIZE_PROPERTY_URI = "http://ugritlab.ugr.es/graphdl#size";
    public static final String SIZE_PROPERTY_GRAPHML = "size";

    // @todo Add edge weight

    static final ImmutableMap<String, String> SpecialGraphMLProperties =
            new ImmutableMap.Builder<String, String>()
                    .put(ID_PROPERTY_URI, ID_PROPERTY_GRAPHML)
                    .put(COLOR_PROPERTY_URI, COLOR_PROPERTY_GRAPHML)
                    .put(R_PROPERTY_URI, R_PROPERTY_GRAPHML)
                    .put(G_PROPERTY_URI, G_PROPERTY_GRAPHML)
                    .put(B_PROPERTY_URI, B_PROPERTY_GRAPHML)
                    .put(X_PROPERTY_URI, X_PROPERTY_GRAPHML)
                    .put(Y_PROPERTY_URI, Y_PROPERTY_GRAPHML)
                    .put(SIZE_PROPERTY_URI, SIZE_PROPERTY_GRAPHML)
                    .put(LABEL_PROPERTY_URI, LABEL_PROPERTY_GRAPHML)
                    .build();

    /** Basic constructor **/
    public RDFGraphDL() {
        graph = TinkerGraph.open();
    }

    /** Expose member graph (as mutable reference)
     *  @return Graph as a mutable TinkerGraph reference **/
    public TinkerGraph asTinkerGraph() {
        return this.graph;
    }

    /** Load data from query result
     *  @param r Query result. Should include triples defining nodes, edges, etc. using the GraphDL ontology
     *           at http://ugritlab.ugr.es/graphdl# **/
    public void load(GraphQueryResult r) throws QueryEvaluationException {

        /* Parse triple result into a TinkerGraph */
        Set<String> vertices = new HashSet<>();                                     // vertex URIs
        Set<String> edges    = new HashSet<>();                                     // edges URIs
        Map<String, MutablePair<String,String>> edgesMap = new HashMap<>();         // edge URI --> <vertex URI, vertex URI>
        Multimap<String,String> graphElementToAttributeValue    = ArrayListMultimap.create();   // vertex or edge URIs --> attribute value URI
        Map<String, MutablePair<String,String>> attributeValues = new HashMap<>();  // attribute value URI --> List of pairs <attribute for value, value>
        BiMap<String, String> ids = HashBiMap.create();                             // element URI <--> id

        // Build lists and maps of vertices, edges and attributes
        while(r.hasNext()) {
            Statement stmt   = r.next();
            String subject   = stmt.getSubject().toString();
            String predicate = stmt.getPredicate().toString();
            String object    = stmt.getObject().toString();

            if(predicate.equals(RDF_TYPE_URI) && object.equals(NODE_CLASS_URI)) {
                // Add vertex to vertices list
                vertices.add(subject);

            } else if(predicate.equals(RDF_TYPE_URI) && object.equals(EDGE_CLASS_URI)) {
                // Add edge to edges list
                edges.add(subject);
                // Add edge without vertices to edges map
                edgesMap.putIfAbsent(subject, new MutablePair<String,String>());

            } else if(predicate.equals(SOURCE_PROPERTY_URI)) {
                // Add source vertex to vertices list (in case it is not already present)
                vertices.add(object);
                // Add edge to edges list and edges map, if not present
                edges.add(subject);
                edgesMap.putIfAbsent(subject, new MutablePair<String,String>());
                // Associate source vertex to the edge edge
                MutablePair<String,String> e = edgesMap.get(subject);
                e.setLeft(object);

            } else if(predicate.equals(TARGET_PROPERTY_URI)) {
                // Add target vertex to vertices list (in case it is not already present)
                vertices.add(object);
                // Add edge to edges list and edges map, if not present
                edges.add(subject);
                edgesMap.putIfAbsent(subject, new MutablePair<String,String>());
                // Associate target vertex to the edge edge
                MutablePair<String,String> e = edgesMap.get(subject);
                e.setRight(object);

            } else if(predicate.equals(FOR_ATTRIBUTE_PROPERTY_URI)) {
                // Add forAttribute binding to attribute set
                attributeValues.putIfAbsent(subject, new MutablePair<String,String>());
                MutablePair<String,String> value = attributeValues.get(subject);
                value.setLeft(object);

            } else if(predicate.equals(VAL_PROPERTY_URI)) {
                // Add val binding to attribute set
                attributeValues.putIfAbsent(subject, new MutablePair<String,String>());
                MutablePair<String,String> value = attributeValues.get(subject);
                value.setRight(object);

            } else if(predicate.equals(RDF_TYPE_URI) && object.equals(ATTRIBUTE_VALUE_CLASS_URI)) {
                // Add empty attribute value
                attributeValues.putIfAbsent(subject, new MutablePair<String,String>());

            } else if(predicate.equals(HAS_ATTRIBUTE_VALUE_PROPERTY_URI)) {
                // Add target vertex to vertices list (in case it is not already present)
                vertices.add(subject);
                // Add empty attribute value, if not present
                attributeValues.putIfAbsent(object, new MutablePair<String,String>());
                // Associate attribute to vertex
                graphElementToAttributeValue.put(subject, object);

            } else if(predicate.equals(ID_PROPERTY_URI)) {
                // Add id for subject entity (previous values will be overwritten)
                ids.put(subject, object);
            }

        }

        // Add vertices to graph
        Map<String, Vertex> verticesInGraph = new HashMap<>();  // vertex URI --> Vertex
        for(String v_uri : vertices) {
            String label = ids.getOrDefault(v_uri,v_uri);
            Vertex v = graph.addVertex(label);
            verticesInGraph.put(v_uri, v);
        }
        nodeCount = verticesInGraph.keySet().size();

        // Add edges to graph
        Map<String, Edge> edgesInGraph = new HashMap();         // edges URI --> Edge
        for(String e_uri : edges) {
            MutablePair<String,String> vs_uris = edgesMap.get(e_uri);
            if(vs_uris != null) {
                Vertex v_source = verticesInGraph.get(vs_uris.getLeft());
                Vertex v_target = verticesInGraph.get(vs_uris.getRight());

                if(v_source != null && v_target != null) {
                    String e_label = ids.getOrDefault(e_uri, e_uri);
                    Edge e = v_source.addEdge(e_label, v_target);
                    edgesInGraph.put(e_uri, e);
                }
            }
        }
        edgeCount = edgesInGraph.keySet().size();

        // Add properties to graph elements
        for(Map.Entry<String,String> a : graphElementToAttributeValue.entries()) {
            String ge_uri = a.getKey();
            String att_val_uri = a.getValue();
            MutablePair<String,String> property_value = attributeValues.get(att_val_uri);
            String property = property_value.getLeft();
            String value = property_value.getRight();

            if(verticesInGraph.get(ge_uri) != null) {
                verticesInGraph.get(ge_uri).property(property, value);

            } else if(edgesInGraph.get(ge_uri) != null) {
                edgesInGraph.get(ge_uri).property(property, value);
            }

        }


    }

    /** Clean GraphML <--> GraphDL tags */
    public void cleanForGraphML() {
        final Iterator<Vertex> vertexIterator = graph.vertices();
        while(vertexIterator.hasNext()) {
            Vertex vertex = vertexIterator.next();
            final Iterator<VertexProperty<String>> propertyIterator = vertex.properties();

            while(propertyIterator.hasNext()) {
                VertexProperty<String> property = propertyIterator.next();
                String value = property.value();

                // Trim leading and ending quotes (")
                if(value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length()-1);
                    vertex.property(property.key(), value);
                }

                // Trim quotes and 'english' tag
                if(value.startsWith("\"") && value.endsWith("\"@en")) {
                    value = value.substring(1, value.length()-4);
                    vertex.property(property.key(), value);
                }

                // Trim quotes and remove XMLSchema#xxx
                if(value.startsWith("\"") && value.contains("^^<http://www.w3.org/2001/XMLSchema#")) {
                    value = value.substring(1, value.indexOf("^")-2);
                    vertex.property(property.key(), value);
                }

                // Add property value if it correspond to a GraphML property
                if(SpecialGraphMLProperties.containsKey(property.key())) {
                    vertex.property(SpecialGraphMLProperties.get(property.key()), value);
                }

            }
        }
    }

    public int getNodeCount() { return this.nodeCount; }

    public int getEdgeCount() { return this.edgeCount; }


}
