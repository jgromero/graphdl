# GraphDL

**GraphDL** is an OWL ontology that allows describing graphs with a simple vocabulary denoting nodes, edges, and properties. It is strongly based on the [Graph Markup Language](http://graphml.graphdrawing.org) (GraphML) conceptual model, and therefore can be easily translated into other formats.

 This repository includes the following GraphDL resources:

1. **GraphDL ontology model** ([ontology](ontology) folder): GraphDL ontology in OWL Turtle format. A sample RDF file is also included.

2. **Java single-node implementation** of a GraphDL >> TinkerPop parser ([single-node](single-node) folder): Maven project implementing a parser from a Sesame/RDF4J [GraphQueryResult](http://archive.rdf4j.org/javadoc/sesame-4.1.2/) to a [TinkerPop graph](http://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerGraph.html).

3. **Spark implementation** of a GraphDL >> GraphX parser ([distributed](distributed) folder): SBT project implementing an [Apache Spark](http://spark.apache.org/docs/latest/index.html) parser from a triple file (see *triples* folder for a sample file structure and name) to a [GraphX](http://spark.apache.org/docs/latest/graphx-programming-guide.html) representation. To run it, build a *fat* JAR (pre-configured .sbt is provided) and submit to Spark via *spark-submit*.

<!-- There is a **web application** implementing GraphDL-based graph generation and visualization running [here](http://35.164.57.130:53188/graphviewer/vizu.html#). Use it wisely! -->

Contributions are welcome!

>**GraphDL** has been developed within the [BIGFUSE project](http://decsai.ugr.es/~jgomez/bigfuse), and funded by the University of Granada and the Spanish Ministry of Education, Culture and Sport.

