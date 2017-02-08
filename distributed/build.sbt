lazy val root = (project in file(".")).
  settings(
    name := "LinkedGraphsSpark",
    version := "1.0",
    scalaVersion := "2.10.5",
    mainClass in Compile := Some("es.ugritlab.linkedgraphs.LinkedGraphApp")
  )

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-core_2.10" % "1.6.0" % "provided",
  "org.apache.spark" % "spark-graphx_2.10" % "1.6.0" % "provided"
)