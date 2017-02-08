package es.ugr.ugritlab.graphs.rdf.test;

/**
 * Created by jgomez on 08/02/17.

 * Drug interactions graph generation sample.
 * Graph with layout can be found at: ./tmp/graph-layout.graphml (open with Gephi)
 */

import com.bigdata.rdf.sail.webapp.client.RemoteRepository;
import com.bigdata.rdf.sail.webapp.client.RemoteRepositoryManager;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.oupls.jung.GraphJung;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import es.ugr.ugritlab.graphs.gephi.GephiLayoutManager;
import es.ugr.ugritlab.graphs.rdf.RDFGraphDL;
import org.apache.commons.io.IOUtils;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.openrdf.query.GraphQueryResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class TestDrugInteractions {

    private static final String serviceURL = "http://35.164.57.130:9999/blazegraph";
    private static final String namespace = "drugbank";

    public static void main(String[] args) throws Exception {

        final RemoteRepositoryManager remoteRepoManager = new RemoteRepositoryManager(serviceURL, false /* useLBS */);
        try {
            /* Setup & query remote repository */
            RemoteRepository remoteRepo = remoteRepoManager.getRepositoryForNamespace(namespace);
            String query = readFile("/query-show-edges.sparql", Charset.defaultCharset());
            GraphQueryResult queryResult = remoteRepo.prepareGraphQuery(query).evaluate();

            /* Transform GraphDL triples into a graph structure */
            RDFGraphDL graph = new RDFGraphDL();
            graph.load(queryResult);
            graph.cleanForGraphML();
            TinkerGraph tinker = graph.asTinkerGraph();

            /* Layout graph with Gephi */
            String tmpFolder = "./tmp/";
            GephiLayoutManager lm = new GephiLayoutManager(null, tmpFolder);
            lm.init(null);
            TinkerGraph tinkerLayout = lm.doLayout(tinker);

            /* Calculate PageRank */
            File tmpPageRankFile = new File(tmpFolder + "tinkergraph.xml");
            tmpPageRankFile.deleteOnExit();
            tinker.io(IoCore.graphml()).writeGraph(tmpPageRankFile.getAbsolutePath());
            com.tinkerpop.blueprints.impls.tg.TinkerGraph tinkerBlueprints =
                    new com.tinkerpop.blueprints.impls.tg.TinkerGraph(
                            tmpFolder,
                            com.tinkerpop.blueprints.impls.tg.TinkerGraph.FileType.GRAPHML);
            GraphJung jung = new GraphJung(tinkerBlueprints);
            PageRank pr = new PageRank<Vertex, Edge>(jung, 0.15d);
            pr.evaluate();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            remoteRepoManager.close();
        }

        System.exit(1);
    }

    public static String readFile(String path, Charset encoding) throws IOException {
        InputStream inputStream = TestDrugInteractions.class.getResourceAsStream(path);
        String contents = IOUtils.toString(inputStream, encoding.toString());
        IOUtils.closeQuietly(inputStream);
        return contents;
    }

}
