import sbt._

class AkkaExpectProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
  lazy val junit     = "junit" % "junit" % "4.8.1"
  lazy val scalatest = "org.scalatest" % "scalatest" % "1.2"
}
