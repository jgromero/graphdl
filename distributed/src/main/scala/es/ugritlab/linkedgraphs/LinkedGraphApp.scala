package es.ugritlab.linkedgraphs

/**
  * Created by jgomez on 08/02/17.
  * Processing of a triple file to generate a graph file using Spark and GraphX.
  * The output file is written in the ./graphs folder.
  *
  * Run the program using spark-submit. Developed for Apache Spark 1.6.
  */

import java.time.Instant

import org.apache.log4j.{Level, Logger}
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

object LinkedGraphApp {

  def main(args: Array[String]) {
    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.ERROR)
    // or just: sc.setLogLevel("ERROR")

    // Create Spark conf
    val conf = new SparkConf().setAppName("Linked Graph Application").setMaster("local[*]")

    // Create Spark context
    val sc = new SparkContext(conf)

    // Load GraphDL file
    var file = "./triples/631b3421-edbf-4f1a-bf48-7936f397d402_triples_100.txt"
    if(args.length > 0) {
      file = args(0)
    }
    var limit = file.substring(file.lastIndexOf("_") + 1, file.lastIndexOf("."))
    val data = sc.textFile(file)

    // Process lines
    val t0 = Instant.now.toEpochMilli

    // --> list of nodes
    var nodesRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") && line.contains("http://ugritlab.ugr.es/graphdl#Node")
        ).map(line =>
          line.substring(1, line.indexOf(","))
        )
      }
    ).zipWithUniqueId()

    // --> list of edges
    var edgesRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") && line.contains("http://ugritlab.ugr.es/graphdl#Edge")
        ).map(line =>
          line.substring(1, line.indexOf(","))
        )
      }
    )

    // --> attribute values
    var attvalRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") && line.contains("http://ugritlab.ugr.es/graphdl#AttributeValue")
        ).map(line =>
          line.substring(1, line.indexOf(","))
        )
      }
    ).zipWithUniqueId()

    var forAttRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://ugritlab.ugr.es/graphdl#forAttribute")
        ).map(line =>
          (
            line.substring(1, line.indexOf(",")),
            line.substring(line.lastIndexOf(",")+2, line.length-1)
          )
        )
      }
    )
    var valueAttRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://ugritlab.ugr.es/graphdl#val")
        ).map(line =>
          (
            line.substring(1, line.indexOf(",")),
            line.substring(line.lastIndexOf(",")+2, line.length-1)
          )
        )
      }
    )

    var elementAttRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://ugritlab.ugr.es/graphdl#hasAttributeValue")
        ).map(line =>
          (
            line.substring(1, line.indexOf(",")),
            line.substring(line.lastIndexOf(",")+2, line.length-1)
          )
        )
      }
    ).map(e => (e._2, e._1))

    var attributeTableRDD =
      elementAttRDD.join(attvalRDD).
        join(forAttRDD).
        join(valueAttRDD).
        map(e => (e._2._1._1._1, e._2._1._2, e._2._2))

    // --> edge sources
    var sourcesRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://ugritlab.ugr.es/graphdl#source")
        ).map(line =>
          (line.substring(line.lastIndexOf(",")+2, line.length-1), line.substring(1, line.indexOf(",")))
        )
      }
    ).join(nodesRDD).map(r => (r._2._1, (r._1, r._2._2)))

    // --> edge targets
    var targetsRDD = data.mapPartitions(
      iterator => {
        iterator.filter(line =>
          line.contains("http://ugritlab.ugr.es/graphdl#target")
        ).map(line =>
          (line.substring(line.lastIndexOf(",")+2, line.length-1), line.substring(1, line.indexOf(",")))
        )
      }
    ).join(nodesRDD).map(r => (r._2._1, (r._1, r._2._2)))

    // --> joint target and sources
    var edgesSourcesTargetsRDD =
      targetsRDD.join(sourcesRDD).map(e => (e._1, e._2._1, e._2._2)).zipWithUniqueId()

    //edgesSourcesTargetsRDD.take(5).foreach(println)

    // --> build suitable data structure for nodes, edges, and graph
    var graphEdgesRDD : RDD[Edge[String]] =
      edgesSourcesTargetsRDD.map(e => Edge(e._1._2._2, e._1._3._2, e._1._1))
    var graphNodesRDD : RDD[(VertexId, String)] =
      nodesRDD.map(r => (r._2, r._1))

    var graph = Graph(graphNodesRDD, graphEdgesRDD)

    // --> save graph to (single) file
    graphEdgesRDD.map(
      e => Seq(e.srcId, e.dstId).mkString(";")).
      coalesce(1, true).
      saveAsTextFile("graphs/graph_" + limit)

    // --> run PageRank
    val pr = graph.pageRank(0.15).vertices

    // --> end program
    println("Program ended successfully. Check ./graph folder for outputs.")

    sc.stop()
    // safely disregard error: ERROR util.Utils: uncaught error in thread SparkListenerBus, stopping SparkContext
  }
}
